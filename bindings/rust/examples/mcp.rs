// Raven MCP from Rust: a tool an agent can call.
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
    let note = arguments.split("\"text\":\"").nth(1).and_then(|rest| rest.split('"').next()).unwrap_or("");
    Rmcp_call_reply(call, cstr(&format!("added {}", note)).as_ptr());
}

/// One request of an agent over Streamable HTTP; keeps the session the server gives.
fn post(session: &mut Option<String>, body: &str) -> String {
    let mut stream = TcpStream::connect(("127.0.0.1", TEST_PORT as u16)).expect("the server listens");
    let mut request = format!(
        "POST /mcp HTTP/1.1\r\nHost: 127.0.0.1:{}\r\nContent-Type: application/json\r\n\
         Accept: application/json, text/event-stream\r\nContent-Length: {}\r\nConnection: close\r\n",
        TEST_PORT,
        body.len()
    );
    if let Some(id) = session {
        request.push_str(&format!("Mcp-Session-Id: {}\r\n", id));
    }
    request.push_str("\r\n");
    request.push_str(body);
    stream.write_all(request.as_bytes()).unwrap();
    let mut response = String::new();
    stream.read_to_string(&mut response).unwrap();
    let split = response.find("\r\n\r\n").expect("an HTTP response") + 4;
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
    while let Some(end) = rest.find("\r\n") {
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
