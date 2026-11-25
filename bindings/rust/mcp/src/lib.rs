//! Raven MCP for Rust: a Raven app's own tools for agents, from closures with serde arguments.
//! The crate `raven-gui-mcp`, used as `raven_mcp`, over `raven-gui`'s feature `mcp` (the C API,
//! `Rmcp_*`, as it is). It links what that feature links: libraven from `RAVEN_LIB_DIR`, and the
//! Raven MCP library from `RAVEN_MCP_LIB_DIR`.
//!
//! ```no_run
//! use raven_mcp::{self as mcp, Call, Hint};
//! use raven_mcp::schemars::JsonSchema;
//! use raven_mcp::serde::Deserialize;
//!
//! #[derive(Deserialize, JsonSchema)]
//! # #[serde(crate = "raven_mcp::serde")] #[schemars(crate = "raven_mcp::schemars")]
//! struct AddNote {
//!     /// The note's text
//!     text: String,
//!     #[serde(default)]
//!     pinned: bool,
//! }
//!
//! let _add = mcp::tool("notes.add", "Add a note to the list")
//!     .hints(Hint::NOT_DESTRUCTIVE | Hint::CLOSED_WORLD)
//!     .add(move |args: AddNote| -> Result<String, String> { Ok(format!("added {}", args.text)) });
//!
//! let _export = mcp::tool("notes.export", "Export the notes to a file")
//!     .add_deferred(|_: mcp::NoArgs, call: Call| {
//!         std::thread::spawn(move || call.reply("exported"));   // `reply` consumes the Call
//!     });
//!
//! mcp::start(&mcp::Config::default());
//! // ... the app's loop ...
//! mcp::stop();                                                 // before Rshutdown
//! ```
//!
//! - **Arguments through serde**, and the schema from schemars' `JsonSchema` derive: doc comments
//!   become descriptions. A tool without arguments takes [`NoArgs`]. The derives name `serde` and
//!   `schemars`, so an app depends on both, or points them at the ones re-exported here
//!   (`#[serde(crate = "raven_mcp::serde")]`, `#[schemars(crate = "raven_mcp::schemars")]`).
//! - **`Ok` replies, `Err` fails the call** with its `Display`. A reply is a `String`, a `&str`, `()`
//!   (an empty text) or a `serde_json::Value` (JSON). A panic fails the call with its message.
//! - **A [`ToolHandle`] withdraws its tool when dropped.** `.forget()` keeps it for the program's life.
//! - **Exactly one reply, checked by the compiler**: [`ToolBuilder::add_deferred`] hands over a
//!   [`Call`] that is `Send` but not `Clone`, and `reply` and `fail` consume it. A `Call` dropped
//!   without a reply fails the call itself ("the tool never replied").
//! - Tools run on Raven's UI thread, as its actions do, and tools are added, dropped, started and
//!   stopped from it: a `ToolHandle` isn't `Send`.
//!
//! Raven's own actions need nothing here: `Rmcp_expose_actions` publishes them.

use raven::{
    RMcpCall, RMcpConfig, RMcpToolDesc, Rmcp_call_fail, Rmcp_call_is_cancelled, Rmcp_call_reply, Rmcp_call_reply_json,
    Rmcp_config_from_environment, Rmcp_get_url, Rmcp_start, Rmcp_stop, Rmcp_tool_add, Rmcp_tool_remove,
    Rmcp_tool_remove_all, R_MCP_HINT_CLOSED_WORLD, R_MCP_HINT_IDEMPOTENT, R_MCP_HINT_NOT_DESTRUCTIVE,
    R_MCP_HINT_READ_ONLY,
};
use raven::{cstr, text};
use std::cell::{Cell, RefCell};
use std::collections::HashMap;
use std::fmt::Display;
use std::os::raw::{c_char, c_void};
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::ptr;

pub use schemars;
pub use serde;
pub use serde_json;

use schemars::JsonSchema;
use serde::de::DeserializeOwned;
use serde_json::Value;

/// What a tool says about itself (`RMcpToolHint`): each hint makes it look safer to an agent.
#[derive(Copy, Clone, Debug, Default, PartialEq, Eq)]
pub struct Hint(u32);

impl Hint {
    /// Changes nothing.
    pub const READ_ONLY: Hint = Hint(R_MCP_HINT_READ_ONLY as u32);
    /// Changes only by adding: never deletes or overwrites.
    pub const NOT_DESTRUCTIVE: Hint = Hint(R_MCP_HINT_NOT_DESTRUCTIVE as u32);
    /// Calling it again with the same arguments does nothing more.
    pub const IDEMPOTENT: Hint = Hint(R_MCP_HINT_IDEMPOTENT as u32);
    /// Touches nothing outside the app: no network, no other programs.
    pub const CLOSED_WORLD: Hint = Hint(R_MCP_HINT_CLOSED_WORLD as u32);
}

impl std::ops::BitOr for Hint {
    type Output = Hint;
    fn bitor(self, other: Hint) -> Hint {
        Hint(self.0 | other.0)
    }
}

/// The arguments of a tool that takes none.
#[derive(serde::Deserialize, JsonSchema)]
pub struct NoArgs {}

/// A call to answer later, from any thread: `reply`, `reply_json` or `fail` consumes it.
pub struct Call {
    call: *mut RMcpCall,
}

// The library's reply functions are thread-safe, and a Call answers once.
unsafe impl Send for Call {}

impl Call {
    fn take(mut self) -> *mut RMcpCall {
        std::mem::replace(&mut self.call, ptr::null_mut())
    }

    /// Replies with a text.
    pub fn reply(self, text: impl AsRef<str>) {
        let message = cstr(text.as_ref());
        unsafe { Rmcp_call_reply(self.take(), message.as_ptr()) };
    }

    /// Replies with JSON: as text, and as structured content when it is an object.
    pub fn reply_json(self, value: Value) {
        reply_value(self.take(), value);
    }

    /// Fails the call with a message the agent reads.
    pub fn fail(self, message: impl Display) {
        fail(self.take(), &message.to_string());
    }

    /// True once the agent cancelled the call or Raven MCP stopped: long work can stop early, but still replies.
    pub fn is_cancelled(&self) -> bool {
        unsafe { Rmcp_call_is_cancelled(self.call) != 0 }
    }
}

impl Drop for Call {
    fn drop(&mut self) {
        if !self.call.is_null() {
            fail(self.call, "the tool never replied");
        }
    }
}

fn fail(call: *mut RMcpCall, message: &str) {
    let message = cstr(message);
    unsafe { Rmcp_call_fail(call, message.as_ptr()) };
}

fn reply_value(call: *mut RMcpCall, value: Value) {
    let mut result = serde_json::json!({ "content": [{ "type": "text", "text": value.to_string() }] });
    if value.is_object() {
        result["structuredContent"] = value;
    }
    let json = cstr(&result.to_string());
    unsafe { Rmcp_call_reply_json(call, json.as_ptr()) };
}

/// What a tool's `Ok` can be: a text, nothing, or JSON.
pub trait Reply {
    /// Answers the call with it.
    fn send(self, call: Call);
}

impl Reply for String {
    fn send(self, call: Call) {
        call.reply(self)
    }
}

impl<'a> Reply for &'a str {
    fn send(self, call: Call) {
        call.reply(self)
    }
}

impl Reply for () {
    fn send(self, call: Call) {
        call.reply("")
    }
}

impl Reply for Value {
    fn send(self, call: Call) {
        call.reply_json(self)
    }
}

/// A tool being described: [`tool`], then `.title`, `.hints`, and `.add` or `.add_deferred`.
pub struct ToolBuilder {
    name: String,
    description: String,
    title: Option<String>,
    hints: Hint,
}

/// Starts describing a tool: its name (dotted by area, "notes.add") and what it does.
pub fn tool(name: &str, description: &str) -> ToolBuilder {
    ToolBuilder { name: name.to_string(), description: description.to_string(), title: None, hints: Hint::default() }
}

impl ToolBuilder {
    /// A name for people.
    pub fn title(mut self, title: &str) -> Self {
        self.title = Some(title.to_string());
        self
    }

    /// What the tool claims about itself, or-ed: `Hint::READ_ONLY | Hint::CLOSED_WORLD`.
    pub fn hints(mut self, hints: Hint) -> Self {
        self.hints = hints;
        self
    }

    /// Publishes a tool that replies at once: `Ok` replies, `Err` fails the call.
    ///
    /// # Panics
    /// When Raven MCP refuses the tool (an invalid name, or called off Raven's UI thread); its log says why.
    pub fn add<A, R, E, F>(self, mut function: F) -> ToolHandle
    where
        A: DeserializeOwned + JsonSchema,
        R: Reply,
        E: Display,
        F: FnMut(A) -> Result<R, E> + 'static,
    {
        self.publish::<A>(Box::new(move |call: Call, arguments: &str| {
            let arguments = match parse::<A>(arguments) {
                Ok(arguments) => arguments,
                Err(message) => return call.fail(message),
            };
            match catch_unwind(AssertUnwindSafe(|| function(arguments))) {
                Ok(Ok(reply)) => reply.send(call),
                Ok(Err(error)) => call.fail(error),
                Err(panic) => call.fail(panic_message(&panic)),
            }
        }))
    }

    /// Publishes a tool that replies later, through the [`Call`] it is handed, from any thread.
    ///
    /// # Panics
    /// When Raven MCP refuses the tool (an invalid name, or called off Raven's UI thread); its log says why.
    pub fn add_deferred<A, F>(self, mut function: F) -> ToolHandle
    where
        A: DeserializeOwned + JsonSchema,
        F: FnMut(A, Call) + 'static,
    {
        self.publish::<A>(Box::new(move |call: Call, arguments: &str| match parse::<A>(arguments) {
            // a panic drops the Call, which fails it
            Ok(arguments) => {
                let _ = catch_unwind(AssertUnwindSafe(|| function(arguments, call)));
            }
            Err(message) => call.fail(message),
        }))
    }

    fn publish<A: JsonSchema>(self, handler: Handler) -> ToolHandle {
        let schema = serde_json::to_string(&schemars::schema_for!(A)).unwrap_or_else(|_| "{}".to_string());
        let (name, description, schema) = (cstr(&self.name), cstr(&self.description), cstr(&schema));
        let title = self.title.as_deref().map(cstr);
        let desc = RMcpToolDesc {
            name: name.as_ptr(),
            title: title.as_ref().map_or(ptr::null(), |title| title.as_ptr()),
            description: description.as_ptr(),
            input_schema_json: schema.as_ptr(),
            hints: self.hints.0,
        };
        let registration = Box::into_raw(Box::new(Registration {
            handler: RefCell::new(handler),
            running: Cell::new(false),
            dropped: Cell::new(false),
        }));
        if unsafe { Rmcp_tool_add(&desc, Some(trampoline), registration as *mut c_void) } == 0 {
            drop(unsafe { Box::from_raw(registration) });
            panic!("Raven MCP refused the tool {} (its log says why)", self.name);
        }
        CURRENT.with(|current| current.borrow_mut().insert(self.name.clone(), registration));
        ToolHandle { name: self.name, registration }
    }
}

fn parse<A: DeserializeOwned>(arguments: &str) -> Result<A, String> {
    serde_json::from_str(if arguments.is_empty() { "{}" } else { arguments })
        .map_err(|error| format!("invalid arguments: {}", error))
}

fn panic_message(panic: &Box<dyn std::any::Any + Send>) -> String {
    let message = panic
        .downcast_ref::<&str>()
        .map(|text| text.to_string())
        .or_else(|| panic.downcast_ref::<String>().cloned())
        .unwrap_or_else(|| "a panic".to_string());
    format!("the tool panicked: {}", message)
}

type Handler = Box<dyn FnMut(Call, &str)>;

/// A published tool's function, given to the library as its user_data.
struct Registration {
    handler: RefCell<Handler>,
    /// Inside its function: its handle dropped then leaves the freeing to the trampoline.
    running: Cell<bool>,
    dropped: Cell<bool>,
}

thread_local! {
    /// Each name's registration as the library has it: a handle withdraws its tool only while it is
    /// still this one (not replaced, not removed by `stop`).
    static CURRENT: RefCell<HashMap<String, *mut Registration>> = RefCell::new(HashMap::new());
}

unsafe extern "C" fn trampoline(call: *mut RMcpCall, _tool: *const c_char, arguments: *const c_char, user_data: *mut c_void) {
    let registration = user_data as *mut Registration;
    let call = Call { call };
    let arguments = text(arguments);
    {
        let this = &*registration;
        this.running.set(true);
        // a panic was caught where the function ran; this keeps any other from crossing into C
        let _ = catch_unwind(AssertUnwindSafe(|| (this.handler.borrow_mut())(call, &arguments)));
        this.running.set(false);
        if !this.dropped.get() {
            return;
        }
    }
    drop(Box::from_raw(registration));
}

/// A published tool: dropping it withdraws the tool, `forget` keeps it for the program's life.
#[must_use = "a ToolHandle withdraws its tool when dropped: keep it, or call .forget()"]
pub struct ToolHandle {
    name: String,
    registration: *mut Registration,
}

impl ToolHandle {
    /// The tool's name.
    pub fn name(&self) -> &str {
        &self.name
    }

    /// Keeps the tool published for the rest of the program (or until `stop`, or a tool of the same name).
    pub fn forget(self) {
        std::mem::forget(self);
    }
}

impl Drop for ToolHandle {
    fn drop(&mut self) {
        let ours = CURRENT.with(|current| {
            let mut current = current.borrow_mut();
            if current.get(&self.name) == Some(&self.registration) {
                current.remove(&self.name);
                true
            } else {
                false
            }
        });
        if ours {
            let name = cstr(&self.name);
            unsafe { Rmcp_tool_remove(name.as_ptr()) };
        }
        // the library never calls a replaced or removed tool's function again
        let registration = unsafe { &*self.registration };
        if registration.running.get() {
            registration.dropped.set(true);
        } else {
            drop(unsafe { Box::from_raw(self.registration) });
        }
    }
}

/// Raven MCP's settings (`RMcpConfig`); `Config::default()` takes every default.
#[derive(Clone, Debug, Default)]
pub struct Config {
    /// The app's name, as agents see it, and its token file's (`~/.raven/mcp/<name>.json`). None: the program's.
    pub name: Option<String>,
    /// The app's version, as agents see it.
    pub version: Option<String>,
    /// A hint to the agent about how to use the app's tools.
    pub instructions: Option<String>,
    /// The port, on the loopback interface. 0: 8765.
    pub port: u16,
    /// The bearer token. None: the app's stored token; `Some("")`: none, which is logged as a warning.
    pub token: Option<String>,
    /// The UI tools (`ui.*`) on Raven's UI driver.
    pub ui_tools: bool,
}

fn with_config<T>(config: &Config, f: impl FnOnce(&mut RMcpConfig) -> T) -> T {
    let texts: Vec<_> = [&config.name, &config.version, &config.instructions, &config.token]
        .iter()
        .map(|field| field.as_deref().map(cstr))
        .collect();
    let pointer = |index: usize| texts[index].as_ref().map_or(ptr::null(), |text| text.as_ptr());
    let mut raw: RMcpConfig = unsafe { std::mem::zeroed() };
    raw.name = pointer(0);
    raw.version = pointer(1);
    raw.instructions = pointer(2);
    raw.token = pointer(3);
    raw.port = config.port as i32;
    raw.ui_tools = config.ui_tools as i32;
    f(&mut raw)
}

/// Starts serving the tools (`Rmcp_start`). False, logged, when it can't (the port is taken, it runs already).
pub fn start(config: &Config) -> bool {
    with_config(config, |raw| unsafe { Rmcp_start(raw) != 0 })
}

/// Starts only when `RAVEN_MCP=1`, for an app that offers Raven MCP to developers: `RAVEN_MCP_PORT`
/// and `RAVEN_MCP_UI=1` override the config. Returns whether it runs.
pub fn start_from_environment(config: &Config) -> bool {
    with_config(config, |raw| unsafe { Rmcp_config_from_environment(raw) != 0 && Rmcp_start(raw) != 0 })
}

/// Stops serving and withdraws every tool (`Rmcp_stop`, `Rmcp_tool_remove_all`): before `Rshutdown`.
/// The handles still held then only free their functions when dropped.
pub fn stop() {
    unsafe {
        Rmcp_stop();
        Rmcp_tool_remove_all();
    }
    CURRENT.with(|current| current.borrow_mut().clear());
}

/// The address being served ("http://127.0.0.1:8765/mcp"), or None when stopped.
pub fn url() -> Option<String> {
    let url = unsafe { Rmcp_get_url() };
    if url.is_null() {
        None
    } else {
        Some(unsafe { text(url) })
    }
}
