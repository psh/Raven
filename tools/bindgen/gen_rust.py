"""Rust: a crate with the C API as it is (raven::sys) and a few safe helpers."""

from cparse import classify, doc_lines

SCALAR = {"int": "c_int", "float": "f32", "double": "f64", "char": "c_char", "size_t": "usize", "uint64": "u64",
          "uintptr": "usize", "uint32": "u32", "uint8": "u8", "id": "Rid"}
KEYWORDS = {"type", "ref", "box", "move", "match", "loop", "in", "fn", "self", "mod", "use", "where", "impl", "trait", "enum",
            "struct", "final", "override", "macro", "abstract", "priv", "pub", "as", "dyn", "async", "await", "yield", "try"}


def package_version():
    """The version of the crate: the one of the CMake project (bindings/set_version.py keeps them equal)."""
    import os
    import re
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    with open(os.path.join(root, "CMakeLists.txt"), encoding="utf-8") as f:
        match = re.search(r"project\(raven\s+VERSION\s+([0-9.]+)", f.read())
    return match.group(1) if match else "0.1.0"


def safe(name):
    return name + "_" if name in KEYWORDS else name


def rust_type(api, ctype):
    kind, detail = classify(ctype, api)
    if kind == "void":
        return "()"
    if kind == "scalar":
        return SCALAR[detail]
    if kind in ("enum", "callback"):
        return detail
    if kind == "fnptr":
        return "*const c_void"
    if kind == "cstr":
        return "*const c_char"
    if kind == "owned_str":
        return "*mut c_char"
    if kind == "cstr_out":
        return "*mut *const c_char"
    if kind == "cstr_array":
        return "*const *const c_char"
    if kind == "ptr":
        return "*mut c_void"
    if kind == "cptr":
        return "*const c_void"
    if kind == "bytes":
        return "*mut u8"
    if kind == "cbytes":
        return "*const u8"
    if kind == "out":
        return "*mut %s" % (SCALAR[detail[1]] if detail[0] == "scalar" else detail[1])
    if kind == "carray":
        return "*const %s" % SCALAR[detail]
    if kind == "struct":
        return detail
    if kind == "struct_ptr":
        return "*mut %s" % detail
    if kind == "cstruct_ptr":
        return "*const %s" % detail
    if kind == "opaque_ptr":
        return "*mut %s" % detail
    raise ValueError(ctype)


def emit_doc(out, doc, indent=""):
    for line in doc_lines(doc):
        out.append("%s/// %s" % (indent, line) if line else "%s///" % indent)


LIB_RS = '''//! Raven for Rust.
//!
//! Generated from the C headers by tools/bindgen (Raven %(version)s): do not edit.
//!
//! [`sys`] is the C API as it is: the same names, raw pointers, `unsafe`.
//! Everything in it is also at the root of the crate. Around it, a few helpers
//! for what Rust does differently from C:
//!
//! - [`text`] reads a text of the library; [`cstr`] makes one for it;
//! - [`action_bind`] binds an action to a closure;
//! - [`window_from_xml`] and [`widget_find`] take `&str`.
//!
//! ```no_run
//! use raven::*;
//! unsafe { Rinit(R_BACKEND_VULKAN) };
//! let _window = window_from_xml(r#"<Window Title="Hi"><Button Text="Quit" OnClicked="quit"/></Window>"#);
//! action_bind("quit", |_, _| unsafe { Rquit(0) });
//! while unsafe { Rwait_frame(-1.0) } != 0 {}
//! unsafe { Rshutdown() };
//! ```
//!
//! The library itself is linked by `build.rs`: set `RAVEN_LIB_DIR` to the
//! folder that holds it (and `RAVEN_STATIC=1` to link `libraven.a` instead of
//! the shared library).

#![allow(non_camel_case_types, non_snake_case, non_upper_case_globals, clippy::all)]

pub mod sys;
pub use sys::*;

use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_void};

/// A text of the library as a `String` ("" for a null pointer).
///
/// # Safety
/// `pointer` is null or a text returned by the library and still valid.
pub unsafe fn text(pointer: *const c_char) -> String {
    if pointer.is_null() {
        String::new()
    } else {
        CStr::from_ptr(pointer).to_string_lossy().into_owned()
    }
}

/// A text for the library. Keep the result alive for the duration of the call:
/// `Rlabel_set_text(label, cstr("Hi").as_ptr())`. A zero inside the text ends it.
pub fn cstr(text: &str) -> CString {
    let end = text.find('\\0').unwrap_or(text.len());
    CString::new(&text[..end]).unwrap_or_default()
}

unsafe extern "C" fn action_trampoline(sender: Rid, action: *const c_char, user_data: *mut c_void) {
    let closure = &mut *(user_data as *mut Box<dyn FnMut(Rid, &str)>);
    let name = text(action);
    closure(sender, &name);
}

/// Binds an action (`OnClicked="name"` in XML, `Rwidget_set_action`) to a
/// closure. The closure lives until the program ends.
pub fn action_bind<F: FnMut(Rid, &str) + 'static>(name: &str, function: F) {
    let boxed: Box<Box<dyn FnMut(Rid, &str)>> = Box::new(Box::new(function));
    let name = cstr(name);
    unsafe { Raction_bind(name.as_ptr(), Some(action_trampoline), Box::into_raw(boxed) as *mut c_void) }
}

/// Creates a window from XML (`Rwindow_create_from_xml`).
pub fn window_from_xml(xml: &str) -> Rid {
    unsafe { Rwindow_create_from_xml(xml.as_ptr() as *const c_char, xml.len()) }
}

/// Creates a widget tree from XML (`Rwidget_create_from_xml`).
pub fn widget_from_xml(xml: &str) -> Rid {
    unsafe { Rwidget_create_from_xml(xml.as_ptr() as *const c_char, xml.len()) }
}

/// Finds a widget by its name (`Rwidget_find`).
pub fn widget_find(scope: Rid, name: &str) -> Rid {
    let name = cstr(name);
    unsafe { Rwidget_find(scope, name.as_ptr()) }
}
'''

BUILD_RS = '''// Tells cargo where the Raven library is.
//
//   RAVEN_LIB_DIR   the folder that holds libraven.so / raven.lib / libraven.a
//   RAVEN_STATIC    set to 1 to link the static library (and what it needs)
//   RAVEN_MCP_LIB_DIR   with the feature `mcp`: the folder that holds libraven_mcp

use std::env;
use std::path::Path;

fn main() {
    println!("cargo:rerun-if-env-changed=RAVEN_LIB_DIR");
    println!("cargo:rerun-if-env-changed=RAVEN_STATIC");
    if let Ok(dir) = env::var("RAVEN_LIB_DIR") {
        println!("cargo:rustc-link-search=native={}", dir);
    }
    let is_static = env::var("RAVEN_STATIC").map(|value| value == "1").unwrap_or(false);
    println!("cargo:rustc-link-lib={}=raven", if is_static { "static" } else { "dylib" });
    if env::var_os("CARGO_FEATURE_MCP").is_some() {
        // Raven MCP calls the app's own libraven, so it needs the shared one
        if is_static {
            panic!("the feature `mcp` needs the shared libraven: unset RAVEN_STATIC");
        }
        println!("cargo:rerun-if-env-changed=RAVEN_MCP_LIB_DIR");
        if let Ok(dir) = env::var("RAVEN_MCP_LIB_DIR") {
            println!("cargo:rustc-link-search=native={}", dir);
        }
    }
    if is_static {
        // the library is copied into the rlib of this crate: cargo must see when it changes
        if let Ok(dir) = env::var("RAVEN_LIB_DIR") {
            for name in &["libraven.a", "raven.lib"] {
                let path = Path::new(&dir).join(name);
                if path.exists() {
                    println!("cargo:rerun-if-changed={}", path.display());
                }
            }
        }
        let target = env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
        let (libs, frameworks): (&[&str], &[&str]) = match target.as_str() {
            "windows" => (&["user32", "gdi32", "shell32", "comdlg32", "ole32"], &[]),
            "linux" => (&["X11", "dl", "pthread", "m"], &[]),
            "macos" => (&["objc"], &["AppKit", "QuartzCore", "Metal", "Foundation", "CoreFoundation"]),
            _ => (&[], &[]),
        };
        for lib in libs {
            println!("cargo:rustc-link-lib={}", lib);
        }
        for framework in frameworks {
            println!("cargo:rustc-link-lib=framework={}", framework);
        }
    }
}
'''

CARGO = '''[package]
name = "raven-gui"
version = "%(package_version)s"
edition = "2018"
description = "Rust bindings for Raven, a lightweight multithreaded GUI library in C for editors and tools"
license = "Apache-2.0"
repository = "https://github.com/oscar-soirey/Raven"
homepage = "https://github.com/oscar-soirey/Raven"
documentation = "https://github.com/oscar-soirey/Raven/tree/main/bindings#readme"
readme = "README.md"
keywords = ["gui", "ui", "vulkan", "widgets", "editor"]
categories = ["gui", "api-bindings"]
links = "raven"
build = "build.rs"
include = ["src/**/*.rs", "examples/**/*.rs", "build.rs", "Cargo.toml", "README.md", "LICENSE"]

[lib]
# the crate is published as raven-gui, and used as `raven` (use raven::*;)
name = "raven"
path = "src/lib.rs"

[features]
# Raven MCP (raven_mcp.h): links the Raven MCP library, from RAVEN_MCP_LIB_DIR
mcp = []

[[example]]
name = "mcp"
required-features = ["mcp"]
'''

EXAMPLE = '''// A window described in XML, driven from Rust.
//
//   RAVEN_LIB_DIR=/path/to/lib cargo run --example hello
//
// `-- --test` runs without a window system and without a GPU, clicks its own
// button and checks what happened: it is how the build of Raven checks this
// binding.

use raven::*;
use std::cell::RefCell;
use std::rc::Rc;

const XML: &str = r#"
<Window Title="Raven from Rust" Width="360" Height="200">
    <VBox Spacing="10" Slot.Padding="16">
        <Label Name="title" Text="Hello from Rust" TextSize="20"/>
        <TextInput Name="name" Placeholder="Your name"/>
        <HBox Spacing="8">
            <Button Name="greet" Text="Greet" Style="Primary" OnClicked="greet" Slot.Fill="1"/>
            <Button Text="Quit" OnClicked="quit"/>
        </HBox>
    </VBox>
</Window>"#;

fn main() {
    let test = std::env::args().any(|argument| argument == "--test");
    unsafe {
        let mut desc: RInitDesc = std::mem::zeroed();
        desc.backend = if test { R_BACKEND_NULL } else { R_BACKEND_VULKAN };
        desc.headless = test as i32;
        if Rinit_ex(&desc) == 0 {
            eprintln!("cannot start Raven");
            std::process::exit(1);
        }

        let window = window_from_xml(XML);
        let title = widget_find(window, "title");
        let name = widget_find(window, "name");
        let clicks = Rc::new(RefCell::new(0));

        let counted = clicks.clone();
        action_bind("greet", move |_sender, _action| {
            *counted.borrow_mut() += 1;
            let who = text(Rtext_input_get_text(name));
            Rlabel_set_text(title, cstr(&format!("Hello, {}!", who)).as_ptr());
        });
        action_bind("quit", |_, _| Rquit(0));

        if !test {
            while Rwait_frame(-1.0) != 0 {}
            Rshutdown();
            return;
        }

        Rdraw_frame();
        assert_eq!(text(Rwindow_get_title(window)), "Raven from Rust");
        assert_eq!(Rwidget_get_type(title), R_WIDGET_LABEL);
        Rtext_input_set_text(name, cstr("Zoé").as_ptr());
        let (mut x, mut y, mut width, mut height) = (0.0f32, 0.0f32, 0.0f32, 0.0f32);
        Rwidget_get_rect(widget_find(window, "greet"), &mut x, &mut y, &mut width, &mut height);
        assert!(width > 0.0 && height > 0.0);
        Rwindow_inject_mouse_move(window, x + width / 2.0, y + height / 2.0);
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, 1);
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, 0);
        assert_eq!(*clicks.borrow(), 1);
        assert_eq!(text(Rlabel_get_text(title)), "Hello, Zoé!");

        // structures, and a mistake described in one
        let mut stats: RMemoryStats = std::mem::zeroed();
        Rmemory_get_stats(&mut stats);
        assert!(stats.allocation_count > 0);
        let mut error: RXmlError = std::mem::zeroed();
        let broken = "<A><B></A>";
        assert_eq!(Rxml_document_parse(broken.as_ptr() as *const _, broken.len(), &mut error), R_INVALID_ID);
        assert_eq!(error.line, 1);
        assert!(text(error.message.as_ptr()).contains("does not match"));

        // documents
        let document = Rxml_document_create(cstr("VBox").as_ptr());
        let root = Rxml_document_get_root(document);
        let label = Rxml_node_add_child(root, cstr("Label").as_ptr(), -1);
        Rxml_node_set_attribute(label, cstr("Text").as_ptr(), cstr("a < b").as_ptr());
        assert!(text(Rxml_document_to_string(document)).contains("Text=\\"a &lt; b\\""));
        let built = Rxml_document_create_widget(document);
        assert_eq!(Rwidget_get_xml_node(Rwidget_get_child(built, 0)), label);
        Rwidget_destroy(built);
        Rxml_document_destroy(document);

        Raction_fire(cstr("quit").as_ptr(), R_INVALID_ID);
        assert_eq!(Ris_running(), 0);
        Rshutdown();
    }
    println!("rust binding: ok");
}
'''


def emit_items(out, api, link=None):
    """Raven's types and functions as Rust items; `link` names the library of the functions when it isn't libraven."""
    w = out.append
    for name, kind, value in api.defines:
        if kind == "str":
            w('pub const %s: &str = "%s";' % (name, value))
        elif kind == "id":
            w("pub const %s: Rid = 0x%08X;" % (name, value))
        else:
            w("pub const %s: c_int = %d;" % (name, value))
    w("")
    for name in api.opaque:
        w("#[repr(C)]")
        w("pub struct %s {" % name)
        w("    _private: [u8; 0],")
        w("}")
        w("")
    for e in api.enums:
        emit_doc(out, e.doc)
        w("pub type %s = c_int;" % e.name)
        for item, value, doc in e.items:
            emit_doc(out, doc)
            w("pub const %s: %s = %s;" % (item, e.name, "0x%04X" % value if value > 9 else str(value)))
        w("")
    for c in api.callbacks:
        emit_doc(out, c.doc)
        params = ", ".join("%s: %s" % (safe(p.name), rust_type(api, p.ctype)) for p in c.params)
        ret = rust_type(api, c.ret)
        w("pub type %s = Option<unsafe extern \"C\" fn(%s)%s>;" % (c.name, params, "" if ret == "()" else " -> " + ret))
    w("")
    for s in api.structs:
        emit_doc(out, s.doc)
        w("#[repr(C)]")
        w("#[derive(Copy, Clone)]")
        w("pub struct %s {" % s.name)
        for f in s.fields:
            emit_doc(out, f.doc, "    ")
            t = rust_type(api, f.ctype)
            if f.array:
                t = "[%s; %d]" % (t, f.array)
            w("    pub %s: %s," % (safe(f.name), t))
        w("}")
        w("")
    if link:
        w("#[link(name = \"%s\")]" % link)
    w("extern \"C\" {")
    for f in api.functions:
        emit_doc(out, f.doc, "    ")
        params = ", ".join("%s: %s" % (safe(p.name), rust_type(api, p.ctype)) for p in f.params)
        ret = rust_type(api, f.ret)
        w("    pub fn %s(%s)%s;" % (f.name, params, "" if ret == "()" else " -> " + ret))
    w("}")

EXAMPLE_MCP = '''// Raven MCP from Rust: a tool an agent can call.
//
//   RAVEN_LIB_DIR=/path/to/lib RAVEN_MCP_LIB_DIR=/path/to/mcp cargo run --features mcp --example mcp
//
// `-- --test` runs without a window system, calls its own tool over HTTP as an
// agent would, and checks the reply: it is how Raven MCP's build checks this
// binding (`./gradlew :raven-mcp:rustTest` in compose/).

use raven::*;
use std::io::{Read, Write};
use std::net::TcpStream;
use std::os::raw::{c_char, c_int, c_void};
use std::ptr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;

const TEST_PORT: c_int = 18775; // apart from Raven MCP's Python suite (18765, 18766)
static ON_UI_THREAD: AtomicBool = AtomicBool::new(false);

unsafe extern "C" fn add_note(call: *mut RMcpCall, _tool: *const c_char, arguments: *const c_char, _user: *mut c_void) {
    ON_UI_THREAD.store(Ris_ui_thread() != 0, Ordering::SeqCst);
    // {"text":"milk"}: this binding is the C API as it is, so the JSON is read by hand here
    let arguments = text(arguments);
    let note = arguments.split("\\"text\\":\\"").nth(1).and_then(|rest| rest.split('"').next()).unwrap_or("");
    Rmcp_call_reply(call, cstr(&format!("added {}", note)).as_ptr());
}

/// One request of an agent over Streamable HTTP; keeps the session the server gives.
fn post(session: &mut Option<String>, body: &str) -> String {
    let mut stream = TcpStream::connect(("127.0.0.1", TEST_PORT as u16)).expect("the server listens");
    let mut request = format!(
        "POST /mcp HTTP/1.1\\r\\nHost: 127.0.0.1:{}\\r\\nContent-Type: application/json\\r\\n\\
         Accept: application/json, text/event-stream\\r\\nContent-Length: {}\\r\\nConnection: close\\r\\n",
        TEST_PORT,
        body.len()
    );
    if let Some(id) = session {
        request.push_str(&format!("Mcp-Session-Id: {}\\r\\n", id));
    }
    request.push_str("\\r\\n");
    request.push_str(body);
    stream.write_all(request.as_bytes()).unwrap();
    let mut response = String::new();
    stream.read_to_string(&mut response).unwrap();
    let split = response.find("\\r\\n\\r\\n").expect("an HTTP response") + 4;
    let (head, rest) = response.split_at(split);
    for line in head.lines() {
        if let Some(colon) = line.find(':') {
            if line[..colon].eq_ignore_ascii_case("mcp-session-id") {
                *session = Some(line[colon + 1..].trim().to_string());
            }
        }
    }
    if !head.to_ascii_lowercase().contains("transfer-encoding: chunked") {
        return rest.to_string();
    }
    let (mut rest, mut body) = (rest, String::new());
    while let Some(end) = rest.find("\\r\\n") {
        let size = usize::from_str_radix(rest[..end].trim(), 16).unwrap_or(0);
        if size == 0 {
            break;
        }
        body.push_str(&rest[end + 2..end + 2 + size]);
        rest = &rest[end + 4 + size..];
    }
    body
}

fn main() {
    let test = std::env::args().any(|argument| argument == "--test");
    unsafe {
        let mut desc: RInitDesc = std::mem::zeroed();
        desc.backend = if test { R_BACKEND_NULL } else { R_BACKEND_VULKAN };
        desc.headless = test as i32;
        if Rinit_ex(&desc) == 0 {
            eprintln!("cannot start Raven");
            std::process::exit(1);
        }

        let name = cstr("notes.add");
        let description = cstr("Add a note to the list");
        let schema = cstr(r#"{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}"#);
        let mut tool: RMcpToolDesc = std::mem::zeroed();
        tool.name = name.as_ptr();
        tool.description = description.as_ptr();
        tool.input_schema_json = schema.as_ptr();
        tool.hints = (R_MCP_HINT_NOT_DESTRUCTIVE | R_MCP_HINT_CLOSED_WORLD) as u32;
        assert_eq!(Rmcp_tool_add(&tool, Some(add_note), ptr::null_mut()), 1);

        // a zeroed configuration takes every default: port 8765, the app's stored token
        let mut config: RMcpConfig = std::mem::zeroed();
        let no_token = cstr("");
        if test {
            config.port = TEST_PORT;
            config.token = no_token.as_ptr();
        }
        if Rmcp_start(&config) == 0 {
            eprintln!("cannot start Raven MCP");
            std::process::exit(1);
        }

        if !test {
            let xml = format!(r#"<Window Title="Raven MCP from Rust" Width="420" Height="120">
                <Label Text="Serving notes.add at {}" Slot.Padding="16"/></Window>"#, text(Rmcp_get_url()));
            window_from_xml(&xml);
            while Rwait_frame(-1.0) != 0 {}
            Rmcp_stop();
            Rmcp_tool_remove_all();
            Rshutdown();
            return;
        }

        assert_eq!(text(Rmcp_get_url()), format!("http://127.0.0.1:{}/mcp", TEST_PORT));
        let agent = thread::spawn(|| {
            let mut session = None;
            post(&mut session, r#"{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"rust-test","version":"1"}}}"#);
            post(&mut session, r#"{"jsonrpc":"2.0","method":"notifications/initialized"}"#);
            post(&mut session, r#"{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"notes.add","arguments":{"text":"milk"}}}"#)
        });
        // the call runs on this thread, the UI thread, while it draws frames
        for _ in 0..1000 {
            if agent.is_finished() {
                break;
            }
            Rwait_frame(0.01);
        }
        let reply = agent.join().unwrap();
        assert!(reply.contains("added milk"), "the reply was {}", reply);
        assert!(ON_UI_THREAD.load(Ordering::SeqCst));

        let bad = cstr("not a name");
        tool.name = bad.as_ptr();
        assert_eq!(Rmcp_tool_add(&tool, Some(add_note), ptr::null_mut()), 0);

        Rmcp_stop();
        assert!(Rmcp_get_url().is_null());
        Rmcp_tool_remove_all();
        Rshutdown();
    }
    println!("rust mcp binding: ok");
}
'''


def generate(api):
    out = []
    w = out.append
    w("//! The C API of Raven, as it is. Generated by tools/bindgen: do not edit.")
    w("")
    w("use std::os::raw::{c_char, c_int, c_void};")
    w("")
    w("/// A handle: a window, a widget, a font...")
    w("pub type Rid = u32;")
    w("pub type Ruint = u32;")
    emit_items(out, api)
    w("")
    w("// Raven MCP (raven_mcp.h), behind the cargo feature `mcp`: it links the Raven MCP library.")
    w("#[cfg(feature = \"mcp\")]")
    w("mod mcp;")
    w("#[cfg(feature = \"mcp\")]")
    w("pub use mcp::*;")

    mcp = []
    m = mcp.append
    m("//! The C API of Raven MCP (raven_mcp.h, version %s), as it is. Generated by tools/bindgen: do not edit." % api.mcp.version)
    m("//!")
    m("//! Built with the cargo feature `mcp`, which links the Raven MCP library (libraven_mcp):")
    m("//! set `RAVEN_MCP_LIB_DIR` to the folder that holds it. It needs the shared libraven.")
    m("")
    m("#[allow(unused_imports)]")
    m("use super::*;")
    m("#[allow(unused_imports)]")
    m("use std::os::raw::{c_char, c_int, c_void};")
    m("")
    emit_items(mcp, api.mcp, link="raven_mcp")

    version = api.version
    return {
        "rust/src/sys.rs": "\n".join(out),
        "rust/src/sys/mcp.rs": "\n".join(mcp),
        "rust/src/lib.rs": LIB_RS % {"version": version},
        "rust/build.rs": BUILD_RS,
        "rust/Cargo.toml": CARGO % {"package_version": package_version()},
        "rust/examples/hello.rs": EXAMPLE,
        "rust/examples/mcp.rs": EXAMPLE_MCP,
    }
