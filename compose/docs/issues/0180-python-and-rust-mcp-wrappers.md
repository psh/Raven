---
status: open
---

# Python and Rust MCP wrappers

`raven_mcp` for Python and `raven::mcp` for Rust. Design: [Python and Rust](../mcp.md#python-and-rust).

**Depends on:** [Raven MCP in every binding](0183-raven-mcp-in-every-binding.md). The optional
registry package the Python module lives in comes with
[Shipping Raven MCP](0175-shipping-raven-mcp.md), which is on hold; until then it is used locally.

## Deliverables

- [ ] Python `raven_mcp` in the optional package: `@mcp.tool` with the schema from type hints and `Annotated`, the return value as the reply, `call: Call` for later replies, `mcp.remove_tool`, `mcp.start(...)`.
- [ ] Rust `raven::mcp` behind the `mcp` feature: the builder, serde arguments with a `schemars` schema, `ToolHandle` (drop withdraws, `.forget()`), `add_deferred` with a consuming `Call`.
- [ ] The notepad's `notes.*` tools in both, tested through a client.
