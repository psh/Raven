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
