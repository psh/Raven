---
status: closed
---

# The Raven MCP library

`compose/raven-mcp`: the spike made real. One Kotlin/Native module on the MCP Kotlin SDK, giving the
shared library behind `raven_mcp.h` and a klib for compose-raven. Design: [`mcp.md`](../../mcp.md),
[ADR 0003](../../adr/0003-raven-mcp-on-the-kotlin-sdk.md). Starting point: the `spike/mcp` branch.

**Depends on:** nothing open.

## Deliverables

- [x] `include/raven/raven_mcp.h` as designed, with `RAVEN_MCP_API` and `RAVEN_MCP_API_VERSION`.
- [x] The module: the registry (replace on add, queued calls failing when their tool goes, `listChanged`), Streamable HTTP on loopback, the deferred reply with exactly one reply per call, cancellation, `Rmcp_call_reply_json`, hints.
- [x] `RMcpConfig`: defaults, a fixed port with no fallback, `Rmcp_get_url`, the log callback with nothing on stdout, `on_session`, and the stale-tools rule across runs of Raven.
- [x] Who may connect: Host and Origin checks always on, the bearer token stored in `~/.raven/mcp/<app>.json` (0600), `token = ""` with a warning, the `fromEnvironment` counterpart, the `RAVEN_MCP=0` kill switch.
- [x] The shared library links the shared libraven (`@rpath`) and records the Raven it was built against; nothing starts until `Rmcp_start`.
- [x] Tests: the spike's Python round trip as a test, cancellation, the token, a stale tool, the kill switch.

## Resolution

Done on macOS (arm64), on the `raven-mcp` branch. `./gradlew :raven-mcp:check` passes, running
`src/test/python/test_raven_mcp.py`: 13 tests of the C API from Python against a headless Raven,
with an MCP client over HTTP on another thread. Five runs in a row pass, and stdout stays empty.

- **The header**: `include/raven/raven_mcp.h` as designed, plus `Rmcp_config_from_environment`
  (the C form of `fromEnvironment()`), its own `RAVEN_MCP_API` export macro, and two corrections now
  in `mcp.md` too. With no public getter for `RInitDesc.app_name`, the default name is the program's
  name. The log callback can be called from any thread, with information arriving as
  `R_SEVERITY_WEAK_WARNING`.
- **The module** `compose/raven-mcp`, a public Kotlin API (`RavenMcp`, `McpServerConfig`,
  `McpToolSpec`, `McpCall`, `McpHints`) that compose-raven will use, with the 13 C exports over it.
  They match the header one for one, and the library links `@rpath/libraven.0.dylib`.
- **Calls are handles by id**, not pointers, so a second reply is logged and ignored rather than
  touching freed memory. Calls queued for a tool that has been removed or replaced fail inside the
  library.
- **Earlier runs of Raven are recognised without a fork change**: a run is "now - `Rget_time()`" on
  Raven's own clock (`CLOCK_MONOTONIC`), bracketed by two of our clock reads. Two runs are the same
  only when the brackets overlap.
- **Found by the tests and fixed**:
  - An uncaught failure in Ktor's coroutines (a taken port) aborted the app on Kotlin/Native. The
    port is now checked before serving, and Ktor's coroutines have a handler that logs.
  - Ktor's CIO server binds without `SO_REUSEADDR` by default, so a restart failed while old
    connections were in TIME_WAIT. It now sets `reuseAddress`, as the port check does.
  - `Rmcp_stop` waits until the port is free.
- **Not here**: `ui_tools`, `Rmcp_expose_actions` and `Rmcp_expose_action` log "not available yet"
  until [The UI tools in the Raven MCP library](../0176-the-ui-tools-in-the-raven-mcp-library.md)
  and [Raven's actions as tools](../0177-ravens-actions-as-tools.md). Not tried on Linux.
