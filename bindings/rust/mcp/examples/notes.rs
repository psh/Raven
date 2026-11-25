// The notes example (examples/notes) as a Rust app: its logic in examples/notes/notes_app.rs, and
// its tools for agents in notes_tools.rs.
//
//   RAVEN_LIB_DIR=/path/to/lib RAVEN_MCP_LIB_DIR=/path/to/mcp cargo run --example notes   (in bindings/rust/mcp)
//
// RAVEN_MCP=1 serves its tools to agents (notes.add, notes.list, notes.delete), and
// RAVEN_MCP_UI=1 the ui.* tools besides.

#[path = "../../../../examples/notes/notes_app.rs"]
mod notes_app;
#[path = "../../../../examples/notes/notes_tools.rs"]
mod notes_tools;

use notes_app::{notes_xml, open_notes};
use notes_tools::notes_tools;
use raven::*;
use raven_mcp as mcp;

fn main() {
    if unsafe { Rinit(R_BACKEND_VULKAN) } == 0 {
        eprintln!("cannot start Raven");
        std::process::exit(1);
    }
    let window = open_notes(&notes_xml());
    let asked = std::env::var_os("RAVEN_MCP").is_some();
    let tools = if asked { notes_tools(window) } else { Vec::new() };
    let config = mcp::Config { name: Some("notes".into()), ..mcp::Config::default() };
    if asked && mcp::start_from_environment(&config) {
        println!("notes: serving its tools at {}", mcp::url().unwrap_or_default());
    }
    while unsafe { Rwait_frame(-1.0) } != 0 {}
    drop(tools);
    mcp::stop(); // the tools go too, before Rshutdown
    unsafe { Rshutdown() };
}
