The harness is `raven::testing`, in the crate. A failure panics with the driver's message:

```rust
#[test]
fn adds_a_note() {
    raven::testing::run("", |ui| {
        open_notes("notes.xml");                         // the app's code: builds the window, binds its actions
        ui.type_text("role=text-field", "buy milk");
        ui.click(r#"role=button name="Add""#);
        ui.expect_text("role=list-item [0]", "buy milk");
        ui.expect_enabled(r#"role=button name="Delete""#, false);
    });
}
```

`run(xml, ...)` builds a window from XML text first (`""` for none). `run_with(Options { capture:
true, .. })` renders on headless Vulkan and saves PNGs of the windows on failure into
`RAVEN_TEST_CAPTURES`; without Vulkan the test prints that it is skipped and passes. Tests take
turns: Raven is one per process. `RAVEN_LIB_DIR=/path/to/lib cargo test`. Raven's repository has
the worked example, `bindings/rust/tests/notes.rs`.

### Serving the `ui.*` tools

For an agent to drive the running app, build with the crate's `mcp` feature (`RAVEN_MCP_LIB_DIR`
names the folder of `libraven_mcp`, which needs the shared libraven) and start it after the window
is built:

```rust
let mut config: RMcpConfig = unsafe { std::mem::zeroed() };
let name = cstr("notes");                                // the token file: ~/.raven/mcp/notes.json
config.name = name.as_ptr();
let mcp = unsafe { Rmcp_config_from_environment(&mut config) != 0 && Rmcp_start(&config) != 0 };
// ... the app's loop ...
if mcp { unsafe { Rmcp_stop(); Rmcp_tool_remove_all(); } }   // before Rshutdown
```
