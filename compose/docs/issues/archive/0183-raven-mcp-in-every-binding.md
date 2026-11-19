---
status: closed
---

# Raven MCP in every binding

Run `raven_mcp.h` through `tools/bindgen` so every binding can call `Rmcp_*`, all on the local
machine with nothing released. Split out of [Shipping Raven MCP](../0175-shipping-raven-mcp.md),
which is on hold. Design: [Building and shipping](../../mcp.md#building-and-shipping).

**Depends on:** [The Raven MCP library](0174-the-raven-mcp-library.md).

## Deliverables

- [x] `raven_mcp.h` through `tools/bindgen`: every binding gets `Rmcp_*`, loading the library on first use (`RAVEN_MCP_LIBRARY`, else beside libraven), and saying Raven MCP isn't available when it's missing.

## Resolution

Done for **Python and Rust**, on macOS (arm64). The other eleven bindings moved to
[Raven MCP in the other bindings](../0184-raven-mcp-in-the-other-bindings.md).
`./gradlew :raven-mcp:check` runs both, and passed four runs in a row.

- **How a binding reaches the library** (decided here, now in `mcp.md`): a binding that loads
  libraven at run time loads the Raven MCP library on the first `Rmcp_` call. A binding that links
  libraven puts `Rmcp_*` in an opt-in unit that links it, and a missing library is then a link
  error rather than a message.
- **bindgen**: `cparse` reads `raven_mcp.h` (not in `Raven.h`) into `api.mcp`, an `Api` whose type
  lookups fall back to Raven's. It also knows `RAVEN_MCP_API` and `unsigned`. Generators that don't ask
  for `api.mcp` write exactly what they wrote before.
- **Python**: `raven.py` gains every `Rmcp_*`, the structures, callbacks and `RMcpToolHint`,
  loading the library on the first call: `RAVEN_MCP_LIBRARY`, beside libraven, beside `raven.py`,
  then the system. Without it, `import raven` works and `Rmcp_` raises `OSError` ("Raven MCP isn't
  available here"). Raven MCP's 13-test suite now runs through these functions instead of its own
  ctypes. The process loads only one libraven, even with the library found beside it.
- **Rust**: the cargo feature `mcp` adds `src/sys/mcp.rs` (`#[link(name = "raven_mcp")]`),
  re-exported in `raven::sys` and at the root of the crate. `RAVEN_MCP_LIB_DIR` is its search path,
  and `RAVEN_STATIC=1` with the feature stops the build. `examples/mcp.rs --test` publishes
  `notes.add`, calls it over HTTP from another thread as an agent would, and checks the reply and
  that it ran on the UI thread. It is run by `rustTest`, which `check` includes when cargo is
  installed.
- **Found and fixed**:
  - The library's install name was its absolute build path, so anything linking it would only
    run beside this build tree. It is now `@rpath/libraven_mcp.dylib` (and soname
    `libraven_mcp.so` on Linux, not tried).
  - `Rmcp_start`'s doc comment sat on `Rmcp_config_from_environment`, in the header and in
    `mcp.md`.
  - The Rust example first shared port 18766 with the Python suite's taken-port test, which failed
    now and then. It uses 18775.
- **Not here**: Linux was not tried. `cargo run` replaces `DYLD_FALLBACK_LIBRARY_PATH`, so
  `rustTest` passes rpaths through `RUSTFLAGS`, and an app does the same or installs the library.
