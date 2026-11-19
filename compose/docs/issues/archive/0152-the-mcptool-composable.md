---
status: closed
wayfinder: grilling
parent: 0145
blocked_by: [0147]
assignee: Paul Hawke
---

# The McpTool composable

## Question

How does a compose-raven app register its own actions? This is the goal of the map. Favour Compose
conventions.

- **Shape**: `McpTool(name, description) { args -> ... }` as a composable that registers on entering
  composition and unregisters on leaving (`DisposableEffect`), so the tools follow navigation and
  an agent sees only those for the current screen. Or a non-composable API on the host as well, for
  tools that are always there?
- **Arguments and schema**: a raw `JsonObject` and a schema string, or a `@Serializable` argument
  type the schema is derived from? What can the derivation get from kotlinx.serialization alone?
- **The handler**: a `suspend` lambda on the UI thread, which returns the result (the deferred reply
  hidden under a coroutine)? Errors as exceptions that become tool errors?
- **Stale lambdas**: a handler capturing state must see the latest values
  (`rememberUpdatedState`), not the ones from when it registered.
- **Two registrations of one name** (the same composable shown twice): error, last one wins, or
  counted?
- **Starting the server**: `singleWindowApplication(mcp = McpConfig(...))`, a `RavenHost` option,
  or a composable? It must stay off unless the app asks.

Use the notepad sample: `notes.add`, `notes.list`, `notes.delete`.

## Resolution

Decided with Paul on 2026-11-18, and written into
[`compose/docs/mcp.md`](../../mcp.md#compose-raven-mcptool), with the notepad sample's `notes.add`,
`notes.list` and `notes.delete` as the worked example.

- **A composable only**: `McpTool` registers on entering composition and withdraws on leaving, so
  tools follow navigation. App-wide tools sit at the top of `application { }`.
- **Typed arguments**: `McpTool<A>` with a `@Serializable` type. The schema is derived from its
  descriptor (types, required versus defaulted, enums, lists, nested objects, `@McpDescription`
  through `@SerialInfo`). An untyped overload takes a `JsonObject` and an optional schema string.
- **The handler**: a `suspend` lambda on the UI thread, returning a `String` or an `McpReply`
  (text, JSON, image). An exception is a tool failure, and the agent cancelling cancels the
  coroutine. One reply per call is kept underneath.
- **Always the latest handler**, through `rememberUpdatedState`.
- **The same name twice**: the most recent wins, and leaving restores the previous one (a stack per
  name, with a warning while two are active).
- **`McpTool(name, description, title, hints)`**, where `McpHint` maps one to one onto `RMcpToolHint`.
- **Switching on**: `application(mcp = McpConfig(...))`/`singleWindowApplication(mcp = …)`, off
  without it. `McpConfig.uiTools` is the UI tools' opt-in. `McpTool` is a no-op without `mcp`.
- **Where**: `compose-raven-mcp`, package `raven.compose.mcp`. The `mcp` parameters are overloads
  from that module, so `compose-raven-ui` doesn't depend on MCP.
