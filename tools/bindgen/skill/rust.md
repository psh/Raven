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

### Serving the `ui.*` tools, and the app's own

For an agent to drive the running app, add the crate `raven-gui-mcp` (`bindings/rust/mcp`, used as
`raven_mcp`; `RAVEN_MCP_LIB_DIR` names the folder of `libraven_mcp`, which needs the shared
libraven) and start it after the window is built:

```rust
let config = raven_mcp::Config { name: Some("notes".into()), ..Default::default() }; // token: ~/.raven/mcp/notes.json
raven_mcp::start_from_environment(&config);              // RAVEN_MCP=1; RAVEN_MCP_UI=1 adds ui.*
// ... the app's loop ...
raven_mcp::stop();                                       // before Rshutdown
```

The app's own tools are closures: `raven_mcp::tool("notes.add", "Add a note").add(|args: AddNote|
-> Result<String, String> { .. })`, with `AddNote` deriving serde's `Deserialize` and schemars'
`JsonSchema`, and the returned `ToolHandle` kept while the tool should stay (the crate's docs have the
rest). Raven's actions need no code: `Rmcp_expose_actions`. The worked example is
`examples/notes/notes_tools.rs`, run as `cargo run --example notes` in `bindings/rust/mcp`.
