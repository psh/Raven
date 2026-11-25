//! raven_mcp's tests: tools from closures, called by an MCP client on another thread while a
//! headless app draws frames.
//!
//!   RAVEN_LIB_DIR=... RAVEN_MCP_LIB_DIR=... cargo test --test mcp   (in bindings/rust/mcp)
//!
//! Run by `./gradlew :raven-mcp:rustTest` (part of compose's `check`), which builds both libraries.

#[path = "common/mcp_client.rs"]
mod mcp_client;

use mcp_client::{on_client, text_of, Client};
use raven_mcp::{self as mcp, Call, Hint, NoArgs};
use schemars::JsonSchema;
use serde::Deserialize;
use serde_json::json;
use std::cell::RefCell;
use std::rc::Rc;
use std::sync::Mutex;

const PORT: u16 = 18777;
static ONE_AT_A_TIME: Mutex<()> = Mutex::new(());

/// Stops Raven MCP and shuts Raven down, a failed test too, so the next one can start.
struct Shutdown;

impl Drop for Shutdown {
    fn drop(&mut self) {
        mcp::stop();
        unsafe { raven::Rshutdown() };
    }
}

/// A headless Raven, for one test at a time; Raven MCP is started by `start`.
fn with_raven(test: impl FnOnce()) {
    let _turn = ONE_AT_A_TIME.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
    unsafe {
        let mut desc: raven::RInitDesc = std::mem::zeroed();
        desc.backend = raven::R_BACKEND_NULL;
        desc.headless = 1;
        assert_eq!(raven::Rinit_ex(&desc), 1);
    }
    let _shutdown = Shutdown;
    test();
}

fn start() {
    assert!(mcp::start(&mcp::Config {
        name: Some("raven-mcp-rust-test".into()),
        port: PORT,
        token: Some(String::new()),
        ..mcp::Config::default()
    }));
}

#[derive(Deserialize, JsonSchema)]
struct AddNote {
    /// The note's text
    text: String,
    #[serde(default)]
    pinned: bool,
}

#[derive(Deserialize, JsonSchema)]
struct Index {
    index: usize,
}

#[test]
fn a_tool_replies_with_ok_and_its_schema_comes_from_the_type() {
    with_raven(|| {
        let notes = Rc::new(RefCell::new(Vec::new()));
        let added = notes.clone();
        let _add = mcp::tool("notes.add", "Add a note to the list")
            .hints(Hint::NOT_DESTRUCTIVE | Hint::CLOSED_WORLD)
            .add(move |args: AddNote| -> Result<String, String> {
                added.borrow_mut().push((args.text, args.pinned, unsafe { raven::Ris_ui_thread() } != 0));
                Ok(format!("added note {}", added.borrow().len()))
            });
        start();
        let (listed, result) = on_client(|| {
            let mut client = Client::connect(PORT);
            (client.request("tools/list", json!({})), client.call("notes.add", json!({"text": "milk"})))
        });
        let tool = &listed["result"]["tools"][0];
        assert_eq!(tool["inputSchema"]["properties"]["text"]["description"], "The note's text");
        assert_eq!(tool["inputSchema"]["required"], json!(["text"]));
        assert_eq!(tool["annotations"]["destructiveHint"], false);
        assert_eq!(tool["annotations"]["openWorldHint"], false);
        assert_eq!(text_of(&result), "added note 1");
        assert_eq!(*notes.borrow(), vec![("milk".to_string(), false, true)]);
    });
}

#[test]
fn err_bad_arguments_and_panics_fail_the_call_and_json_replies() {
    with_raven(|| {
        let _delete = mcp::tool("notes.delete", "Delete a note")
            .add(|args: Index| -> Result<(), String> { Err(format!("no note {}", args.index)) });
        let _boom = mcp::tool("notes.boom", "Panics").add(|_: NoArgs| -> Result<(), String> { panic!("boom") });
        let _list = mcp::tool("notes.list", "The notes")
            .hints(Hint::READ_ONLY)
            .add(|_: NoArgs| -> Result<serde_json::Value, String> { Ok(json!({"notes": ["milk"]})) });
        start();
        let (failed, invalid, boom, listed) = on_client(|| {
            let mut client = Client::connect(PORT);
            (
                client.call("notes.delete", json!({"index": 7})),
                client.call("notes.delete", json!({"index": "seven"})),
                client.call("notes.boom", json!({})),
                client.call("notes.list", json!({})),
            )
        });
        assert_eq!(failed["isError"], true);
        assert_eq!(text_of(&failed), "no note 7");
        assert_eq!(invalid["isError"], true);
        assert!(text_of(&invalid).starts_with("invalid arguments: "), "{}", text_of(&invalid));
        assert_eq!(boom["isError"], true);
        assert_eq!(text_of(&boom), "the tool panicked: boom");
        assert_eq!(listed["structuredContent"], json!({"notes": ["milk"]}));
    });
}

#[test]
fn a_deferred_call_replies_from_another_thread_and_a_dropped_one_fails() {
    with_raven(|| {
        let _export = mcp::tool("notes.export", "Export the notes").add_deferred(|_: NoArgs, call: Call| {
            std::thread::spawn(move || {
                std::thread::sleep(std::time::Duration::from_millis(50));
                call.reply("exported");
            });
        });
        let _forget = mcp::tool("notes.forget", "Never replies").add_deferred(|_: NoArgs, call: Call| drop(call));
        start();
        let (exported, forgotten) = on_client(|| {
            let mut client = Client::connect(PORT);
            (client.call("notes.export", json!({})), client.call("notes.forget", json!({})))
        });
        assert_eq!(text_of(&exported), "exported");
        assert_eq!(forgotten["isError"], true);
        assert_eq!(text_of(&forgotten), "the tool never replied");
    });
}

#[test]
fn a_dropped_handle_withdraws_its_tool_and_forget_keeps_it() {
    with_raven(|| {
        let dropped = mcp::tool("notes.dropped", "Gone").add(|_: NoArgs| -> Result<&str, String> { Ok("here") });
        mcp::tool("notes.kept", "Kept").add(|_: NoArgs| -> Result<&str, String> { Ok("kept") }).forget();
        // a replaced tool's old handle leaves the new one in place
        let old = mcp::tool("notes.replaced", "Old").add(|_: NoArgs| -> Result<&str, String> { Ok("old") });
        let _new = mcp::tool("notes.replaced", "New").add(|_: NoArgs| -> Result<&str, String> { Ok("new") });
        drop(old);
        drop(dropped);
        start();
        let (names, replaced) = on_client(|| {
            let mut client = Client::connect(PORT);
            let listed = client.request("tools/list", json!({}));
            let mut names: Vec<String> = listed["result"]["tools"]
                .as_array()
                .unwrap()
                .iter()
                .map(|tool| tool["name"].as_str().unwrap().to_string())
                .collect();
            names.sort();
            (names, client.call("notes.replaced", json!({})))
        });
        assert_eq!(names, vec!["notes.kept", "notes.replaced"]);
        assert_eq!(text_of(&replaced), "new");
    });
}

#[test]
#[should_panic(expected = "Raven MCP refused the tool not a name")]
fn a_refused_tool_panics() {
    with_raven(|| {
        let _never = mcp::tool("not a name", "Invalid").add(|_: NoArgs| -> Result<(), String> { Ok(()) });
    });
}
