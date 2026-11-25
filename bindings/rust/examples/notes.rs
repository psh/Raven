// The notes example (examples/notes) as a Rust app, its logic in examples/notes/notes_app.rs.
//
//   RAVEN_LIB_DIR=/path/to/lib RAVEN_MCP_LIB_DIR=/path/to/mcp cargo run --features mcp --example notes
//
// RAVEN_MCP=1 (RAVEN_MCP_UI=1 for the ui.* tools) lets an agent drive the running app.

#[path = "../../../examples/notes/notes_app.rs"]
mod notes_app;

use notes_app::{open_notes, NOTES_XML};
use raven::*;

fn main() {
    unsafe {
        if Rinit(R_BACKEND_VULKAN) == 0 {
            eprintln!("cannot start Raven");
            std::process::exit(1);
        }
        open_notes(NOTES_XML);
        let mut config: RMcpConfig = std::mem::zeroed();
        let name = cstr("notes");
        config.name = name.as_ptr();
        let mcp = Rmcp_config_from_environment(&mut config) != 0 && Rmcp_start(&config) != 0;
        while Rwait_frame(-1.0) != 0 {}
        if mcp {
            Rmcp_stop();
            Rmcp_tool_remove_all();
        }
        Rshutdown();
    }
}
