---
status: open
---

# The Raven MCP library

`compose/raven-mcp`: the spike made real. One Kotlin/Native module on the MCP Kotlin SDK, giving the
shared library behind `raven_mcp.h` and a klib for compose-raven. Design: [`mcp.md`](../mcp.md),
[ADR 0003](../adr/0003-raven-mcp-on-the-kotlin-sdk.md). Starting point: the `spike/mcp` branch.

**Depends on:** nothing open.

## Deliverables

- [ ] `include/raven/raven_mcp.h` as designed, with `RAVEN_MCP_API` and `RAVEN_MCP_API_VERSION`.
- [ ] The module: the registry (replace on add, queued calls failing when their tool goes, `listChanged`), Streamable HTTP on loopback, the deferred reply with exactly one reply per call, cancellation, `Rmcp_call_reply_json`, hints.
- [ ] `RMcpConfig`: defaults, a fixed port with no fallback, `Rmcp_get_url`, the log callback with nothing on stdout, `on_session`, and the stale-tools rule across runs of Raven.
- [ ] Who may connect: Host and Origin checks always on, the bearer token stored in `~/.raven/mcp/<app>.json` (0600), `token = ""` with a warning, the `fromEnvironment` counterpart, the `RAVEN_MCP=0` kill switch.
- [ ] The shared library links the shared libraven (`@rpath`) and records the Raven it was built against; nothing starts until `Rmcp_start`.
- [ ] Tests: the spike's Python round trip as a test, cancellation, the token, a stale tool, the kill switch.
