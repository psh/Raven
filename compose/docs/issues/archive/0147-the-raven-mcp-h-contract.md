---
status: closed
wayfinder: grilling
parent: 0145
blocked_by: []
assignee: Paul Hawke
---

# The raven_mcp.h contract

## Question

What exactly does `raven_mcp.h` promise? Start from the draft header in the map's Notes and grill
each part. Use `codebase-design`: it is the one interface every binding sees.

- **Lifecycle**: one server per process? Started before or after `Rinit`, and stopped before
  `Rshutdown`? What happens to calls still running when it stops? Is `Rmcp_start` idempotent, and
  what errors does it report (port in use, no network), through Raven's error callback or a return
  code?
- **Threading**: callbacks always run on the UI thread (through `Rtask_post`)? Which functions are
  thread-safe: registering from a worker, and `Rmcp_call_reply` from any thread? What happens when
  the UI thread is blocked in a modal loop?
- **Calls**: who owns `arguments_json` and the `RMcpCall`, and for how long? What happens to a call
  never replied to (timeout, cancellation by the client)? Does a reply carry MCP's content list
  (text, image, structured content) or a single JSON value? Is an error reply an MCP tool error
  (`isError`) or a protocol error?
- **Registration**: tool names (MCP's allowed characters; namespacing like `ui.` or `notes.`), an
  input schema as a JSON string, re-registering a name, and the `listChanged` notification it
  triggers. Annotations: read-only, destructive, idempotent?
- **Raven's actions as tools** (`Rmcp_expose_action`, `Rmcp_expose_actions`): decided in
  [Exposing Raven's actions](0155-exposing-ravens-actions.md); fold its answer into the header.
- **Naming**: does `Rmcp_` fit Raven's `R<object>_<action>` convention, and is it a header of
  libraven or of a separate library (this decides with
  [Building and shipping Raven MCP](0148-building-and-shipping-raven-mcp.md))?

Deliver the header with doc comments in Raven's style, in [`compose/docs/mcp.md`](../../mcp.md).

Raised by [the spike](0146-spike-the-kotlin-sdk-as-a-c-library.md): the input schema goes to the SDK whole (the spike dropped
`required`); where kotlin-logging's and Ktor's output goes (they print to stdout); a call never
replied to holds its handle and hangs the client; and the 8 threads that stay after `Rmcp_stop`.

## Resolution

Decided with Paul on 2026-11-18. The header, with its doc comments and the reasons for its shape, is
in [`compose/docs/mcp.md`](../../mcp.md).

- **Lifecycle**: there is one server per process. `Rmcp_start(const RMcpConfig*)` runs after `Rinit`
  and fails with `R_FALSE` if the server is already running. Tools can be added or removed whether or
  not the server runs. `Rmcp_stop` closes the port and every session and cancels calls in flight,
  and may leave idle threads. Before `Rshutdown`, the app calls `Rmcp_stop` and
  `Rmcp_tool_remove_all`; tools left from an earlier run of Raven make `Rmcp_start` and
  `Rmcp_tool_add` fail.
- **Config**: a zeroed struct holding `name`, `version` (defaults from `RInitDesc.app_name`),
  `instructions`, `port` (0 means 8765, loopback only), and `log`/`log_user_data`. It has no host or
  transport field yet. A port already taken fails, with no fallback. `Rmcp_get_url` gives the
  address being served.
- **Errors and logging**: functions that can fail return `R_TRUE`/`R_FALSE`. The reason, and all SDK
  and Ktor output, goes to `RMcpLogCallback(RSeverity, message, user_data)`, and never to stdout.
- **Threading**: everything is UI-thread only except the four `Rmcp_call_*` functions. Tool callbacks
  run on the UI thread, one at a time in arrival order, inside a modal dialog's frames too, and may
  call any Raven MCP function.
- **Registration**: `Rmcp_tool_add(const RMcpToolDesc*, callback, user_data)` and
  `Rmcp_tool_remove(name)`. The desc holds the name (1 to 128 of `[A-Za-z0-9_.-]`, with `ui.`
  reserved for compose-raven), title, description, the input schema as a JSON object passed to
  agents whole, and `hints`. Adding a name that exists replaces it, as `Raction_bind` does.
  `listChanged` goes out on every change.
- **Hints**: a bitmask of explicit claims (`READ_ONLY`, `NOT_DESTRUCTIVE`, `IDEMPOTENT`,
  `CLOSED_WORLD`). Unset keeps MCP's cautious defaults, because a zeroed int can't say "destructive
  by default".
- **Calls**: the callback is `(RMcpCall*, const char* tool, const char* arguments_json, void*
  user_data)`, and `arguments_json` lives only for the callback. Every call gets exactly one reply,
  from any thread, cancelled or not, and the reply frees it. There is no server timeout. A second
  reply is logged and ignored. `Rmcp_call_is_cancelled` lets long work stop early. Calls still queued
  when their tool is removed or replaced fail inside the library, so `user_data` can be freed after
  `Rmcp_tool_remove`.
- **Replies**: `Rmcp_call_reply(text)`, `Rmcp_call_reply_json(a whole CallToolResult)`, and
  `Rmcp_call_fail(message)` as a tool error (`isError`). Protocol errors stay with the server.
- **Arguments are not checked** against the schema; the tool checks its own. Schema validation goes
  to the fog.
- **Header**: `include/raven/raven_mcp.h`, not included by `Raven.h`, with its own `RAVEN_MCP_API`
  export macro and `RAVEN_MCP_API_VERSION "0.1"`.
- **Words** in `compose/GLOSSARY.md`: Raven MCP, Action, Tool, Call, Reply. "Registrant" is dropped.

`Rmcp_expose_action`/`Rmcp_expose_actions` come into the header from
[Exposing Raven's actions](0155-exposing-ravens-actions.md).
