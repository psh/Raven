---
status: closed
---

# compose-raven-mcp

`McpTool`, `application(mcp = …)` and compose-raven's `ui.*` over Compose semantics. Design:
[compose-raven: McpTool](../../mcp.md#compose-raven-mcptool), [The UI tools](../../mcp.md#the-ui-tools).

**Depends on:** [The Raven MCP library](0174-the-raven-mcp-library.md),
[compose-raven-ui-test, and idle in a live app](0178-compose-raven-ui-test-and-live-idle.md),
[PNG encoding in Raven's C](0166-png-encoding-in-ravens-c.md).

## Deliverables

- [x] `McpConfig` (with `uiTools`, `onAgentConnected`, `fromEnvironment()`), and the `mcp` overloads of `application` and `singleWindowApplication`.
- [x] `McpTool<A>` with the schema derived from `A`'s descriptor and `@McpDescription`; the untyped overload; `McpReply`; `McpHint`; the suspend handler on the UI thread, with exceptions as failures and cancellation; `rememberUpdatedState`; the stack per name.
- [x] `McpStatus()`.
- [x] `ui.*` over Compose semantics with `tag=`, the outline, `awaitIdle`, screenshots through `Rimage_encode_png`.
- [x] The notes sample (`samples/notes`, not the text-editor notepad: decided 2026-11-20) publishes `notes.add`, `notes.list`, `notes.delete`; tests drive it through a client.

## Resolution

Done on macOS (arm64), on the `compose-raven` branch. `./gradlew allTests` passes twice in a row (562
tests). That includes 7 in `compose-raven-mcp` and 3 in `samples:notes`, each through an MCP client
over HTTP. `:raven-mcp:check` still passes. The real `notes` app, with a window, answered the tools
in 0.01 to 0.13 s when started with `RAVEN_MCP=1 RAVEN_MCP_UI=1`.

- **`compose-raven-mcp`**, package `raven.compose.mcp`, on `raven-mcp`'s klib. A compose app links
  it with the static libraven it already has (the notes executable is 25 MB in debug).
  - **`McpConfig`** with `uiTools`, `onAgentConnected(client)` and `fromEnvironment()`. The `mcp`
    overloads of `application` and `singleWindowApplication` take null for no server.
  - **`McpServer(config) { }`** is what the overloads wrap the content in, and what tests host. It
    starts the server once, as it enters the composition, and stops it and withdraws every tool as
    it leaves. A first version restarted on every new `McpConfig`, which lost the tools already
    composed. A test covers it now.
  - **`McpTool`**, typed (reified, or with a `KSerializer`) or untyped (`JsonObject` and a schema
    string). The schema comes from the serial descriptor: primitives, enums, lists, maps, nested
    objects, `required` for what is neither nullable nor defaulted, and `@McpDescription` on
    properties and classes. A handler returns a `String` or an `McpReply` (`Text`, `Json`, `Image`).
    It runs as a coroutine of the server's composition scope. A throw is the failure, with its
    message, and the call being cancelled cancels the coroutine. `rememberUpdatedState` gives the
    latest handler, and the registry keeps a stack per name and logs while two are composed.
  - **`McpStatus()`** shows `● <client> connected`, or how many agents are.
  - **The UI tools over Compose semantics** (`UiTools.kt`). Selectors take `role=`, `name=`,
    `name~=`, `text=`, `tag=`, the states and `[n]`, and `widget=` is refused with a pointer to
    `tag=`. The outline walks the node tree, reading each widget as screen readers see it, and
    shows a tag-only node as `- tag="notes"`. Actions go through compose-raven-ui-test's
    `FoundNode`, a disabled node fails as the driver says it, and the tools wait in
    `RavenHost.awaitIdle(120)`.
  - **`McpTestClient` and `whileDrawing`** (`@InternalComposeRavenApi`): a small MCP client over
    posix sockets that runs off the UI thread while the test draws frames.
- **`raven-mcp`** gains what both sets of UI tools share, now public: `McpArguments`, `McpSchemas`,
  `McpScreenshots`, `McpKeys`, `McpAccessible`, `McpAccessibleStates` and `McpOutline`. It also
  gains `McpCall.invokeOnCancel` and `RavenMcp.log`. `LocalRavenHost` is public behind
  `@InternalComposeRavenApi`.
- **Found and fixed: Raven MCP took the app's signals.** Ktor's server start takes SIGINT and
  SIGTERM for its own shutdown hook, which stops only the server, so a Raven MCP app no longer ended
  on `kill` or Ctrl+C. `RavenMcp.start` now gives both their earlier handlers back. Checked by hand
  on the notes app: SIGTERM and SIGINT end it with Raven MCP on and off. There is no automated test.
- **`samples/notes`**, with `launch-notes`: a text field and Add, a row per note with its Delete
  (tagged `note-n` and `delete-n`), the count, and `McpStatus`. It publishes `notes.add`,
  `notes.list` and `notes.delete`.
- **Not here**: the rows of a `ListView` or a `LazyColumn` built from items of one widget aren't
  nodes, so the outline doesn't show them and `role=list-item` finds nothing in a compose app. Not
  tried on Linux. The live run left `~/.raven/mcp/notes.json`, the token file as designed.
