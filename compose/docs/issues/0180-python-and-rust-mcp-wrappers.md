---
status: in-progress
---

# Python and Rust MCP wrappers

`raven_mcp` for Python and `raven_mcp` (the crate `raven-gui-mcp`) for Rust. Design: [Python and Rust](../mcp.md#python-and-rust).

**Depends on:** [Raven MCP in every binding](archive/0183-raven-mcp-in-every-binding.md). The optional
registry package the Python module lives in comes with
[Shipping Raven MCP](0175-shipping-raven-mcp.md), which is on hold; until then it is used locally.

## Deliverables

- [x] Python `raven_mcp` in the optional package: `@mcp.tool` with the schema from type hints and `Annotated`, the return value as the reply, `call: Call` for later replies, `mcp.remove_tool`, `mcp.start(...)`.
- [x] Rust `raven::mcp` behind the `mcp` feature (built as the crate `raven-gui-mcp`, see the notes): the builder, serde arguments with a `schemars` schema, `ToolHandle` (drop withdraws, `.forget()`), `add_deferred` with a consuming `Call`.
- [x] The notepad's `notes.*` tools in both, tested through a client.

## Notes (2026-11-24)

- `raven_mcp.py` is beside `raven.py`, not in a package yet: the `raven-gui[mcp]` extra comes with
  0175, which is on hold. The notepad is `examples/notes`, as in [0182](archive/0182-verify-the-skills-on-samples.md).
- Rust's wrapper is a crate of its own, `raven-gui-mcp` in `bindings/rust/mcp`, used as `raven_mcp`,
  not a module behind `raven-gui`'s feature. Its dependencies (`serde`, `schemars`) made `raven-gui`
  fail to build offline from an empty cargo home, which ctest's `binding_rust` and `binding_rust_ui`
  do. Paul chose the separate crate (2026-11-24). `raven-gui` keeps no dependencies, and this was
  checked from an empty cargo home.
- What the design left open, and how it was settled, is in [`mcp.md`, "As built"](../mcp.md#as-built).
- Tests: `bindings/python/test_raven_mcp.py` (11), `examples/notes/test_notes_mcp.py`,
  `bindings/rust/mcp/tests/mcp.rs` (5) and `tests/notes_mcp.rs`. They run in compose's `check` through
  `:raven-mcp:pythonWrapperTest` and `:raven-mcp:rustTest`. The real Python and Rust notes apps
  were also driven with `RAVEN_MCP=1 RAVEN_MCP_UI=1`: `notes.*` and `ui.*` worked side by side.
- Unblocks [0190](0190-verify-the-skills-with-the-mcp-wrappers.md), the skills' rerun with the wrappers.
