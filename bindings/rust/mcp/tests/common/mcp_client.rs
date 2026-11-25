//! An MCP client over Streamable HTTP, for the tests: run on its own thread while the UI thread
//! draws frames, as an agent would call a running app.

use serde_json::{json, Value};
use std::io::{Read, Write};
use std::net::TcpStream;
use std::thread;
use std::time::{Duration, Instant};

pub struct Client {
    port: u16,
    session: Option<String>,
    next_id: u64,
}

impl Client {
    /// Connects to the server on `port` (no token): initialize, then initialized.
    pub fn connect(port: u16) -> Client {
        let mut client = Client { port, session: None, next_id: 1 };
        client.request(
            "initialize",
            json!({"protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "rust-test", "version": "1"}}),
        );
        client.post(&json!({"jsonrpc": "2.0", "method": "notifications/initialized"}));
        client
    }

    pub fn request(&mut self, method: &str, params: Value) -> Value {
        self.next_id += 1;
        self.post(&json!({"jsonrpc": "2.0", "id": self.next_id, "method": method, "params": params}))
    }

    /// A tool's result: {"content": [...], "isError": ..., "structuredContent": ...}.
    pub fn call(&mut self, tool: &str, arguments: Value) -> Value {
        self.request("tools/call", json!({"name": tool, "arguments": arguments}))["result"].clone()
    }

    fn post(&mut self, message: &Value) -> Value {
        let body = message.to_string();
        let mut stream = TcpStream::connect(("127.0.0.1", self.port)).expect("the server listens");
        let mut request = format!(
            "POST /mcp HTTP/1.1\r\nHost: 127.0.0.1:{}\r\nContent-Type: application/json\r\n\
             Accept: application/json, text/event-stream\r\nContent-Length: {}\r\nConnection: close\r\n",
            self.port,
            body.len()
        );
        if let Some(id) = &self.session {
            request.push_str(&format!("Mcp-Session-Id: {}\r\n", id));
        }
        request.push_str("\r\n");
        request.push_str(&body);
        stream.write_all(request.as_bytes()).unwrap();
        let mut response = String::new();
        stream.read_to_string(&mut response).unwrap();
        let split = response.find("\r\n\r\n").expect("an HTTP response") + 4;
        let (head, rest) = response.split_at(split);
        for line in head.lines() {
            if let Some(colon) = line.find(':') {
                if line[..colon].eq_ignore_ascii_case("mcp-session-id") {
                    self.session = Some(line[colon + 1..].trim().to_string());
                }
            }
        }
        let body = if head.to_ascii_lowercase().contains("transfer-encoding: chunked") {
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
        } else {
            rest.to_string()
        };
        serde_json::from_str(&body).unwrap_or(Value::Null)
    }
}

/// Runs `work` on another thread while this one, Raven's UI thread, draws frames; returns its result.
pub fn on_client<T: Send + 'static>(work: impl FnOnce() -> T + Send + 'static) -> T {
    let worker = thread::spawn(work);
    let deadline = Instant::now() + Duration::from_secs(10);
    while !worker.is_finished() && Instant::now() < deadline {
        unsafe { raven::Rwait_frame(0.01) };
    }
    worker.join().expect("the client finished")
}

/// The text of a tool's first content item.
pub fn text_of(result: &Value) -> String {
    result["content"][0]["text"].as_str().unwrap_or_default().to_string()
}
