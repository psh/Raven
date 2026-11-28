---
status: closed
wayfinder: map
---

# MCP middleware for Raven apps

## Destination

A written design for **Raven MCP**: an in-process MCP server that lets a Raven app, in any language,
publish its own actions as MCP tools for an agent to call. It is settled enough to build:

- `raven_mcp.h`, the C contract: starting and stopping the server, registering and unregistering
  tools, handling a call and replying to it, and the threading and ownership rules. Python, Rust and
  the other bindings use this header, and so does compose-raven through its Kotlin equivalent.
- How it is implemented and shipped: the MCP Kotlin SDK in a Kotlin/Native shared library, or MCP
  written in C inside Raven, recorded as ADR 0003 with the spike's measurements behind it.
- The first two clients: compose-raven's **UI tools** (`ui.find`, `ui.click`, and so on, from the
  test harness's finders and actions), which register like any app's tools, and the **`McpTool`
  composable** for an app's own actions.
- The Python and Rust wrappers, at least as sketches.

The design lands in `compose/docs/mcp.md` (new), plus `compose/docs/spec.md` for the composables.
This map spikes and grills. It does not build the feature; implementation tickets follow when it
closes.

## Notes

- **The goal**: apps register actions and the middleware publishes them. Exposing the testing API
  is only the first pass, and it is a *client* of the registration API, not a feature of the server.
- **The C header is the contract.** Whatever implements it (the Kotlin SDK now, perhaps C later), a
  Python or Rust app sees only `raven_mcp.h`. Design the header first, so the implementation behind
  it can change without breaking a binding.
- **The core stays small.** The server, the tool registry and the hop onto the UI thread. It knows
  nothing about widgets, clicks or Compose. No client gets a private path into the server, our own
  included.
- **Settled while charting (2026-11-18)**, not reopened by the tickets below:
  - The UI-driving tools are registered by compose-raven, the same way an app's tools are, rather
    than built into the server. `Rmcp_expose_ui` is dropped.
  - They match on Compose semantics (test tags, content descriptions, merged text), reusing the
    code behind `compose-raven-test`, so a compose app gets richer tools than a C-level version
    could give it.
  - A compose-raven app registers through the Kotlin module directly, not through the shared
    library's C API, so the process has one registry and one copy of the SDK and Ktor. (Two
    Kotlin/Native runtimes do coexist: see the spike.) An agent sees `ui.*` and the app's own tools
    side by side.
  - The JSON goes across the C boundary as strings (arguments, input schema, result), because every
    language has JSON.
  - A handler can reply later: `Rmcp_call_reply`/`Rmcp_call_fail` are separate from the callback,
    thread-safe, and called exactly once.
- **The header** is in [`compose/docs/mcp.md`](../../mcp.md), decided by
  [The raven_mcp.h contract](../archive/0147-the-raven-mcp-h-contract.md). It replaces the draft
  sketched while charting.

- **Raven seams to build on** (read `include/raven/raven_core.h` first):
  - Named actions (`raven_core.h`, ACTIONS): `Raction_bind`, `Raction_fire`,
    `Raction_set_default_handler`, `Rwidget_set_action`, and XML's `OnClicked="file.save"`. Every
    binding already has them, so they are the natural partner of MCP tools.
  - `Rtask_post` puts work on the UI thread from any thread (thread-safe). `Rtask_run_async` runs
    work on a worker. `Ris_ui_thread`. Every other Raven call is UI-thread only.
  - Accessible names, descriptions and roles (`raven_accessibility.h`), what a C-level set of UI
    tools would see.
  - In compose-raven, `RavenDispatcher` (`compose-raven-ui`, `internal`) is the coroutine dispatcher
    onto the UI thread. The test harness lives in `compose-raven-test`: `RavenTest.kt`
    (`Rwindow_inject_*`, widget-tree walking), `NodeMatcher.kt`, `NodeInteraction.kt`.
- **MCP Kotlin SDK facts** (read 2026-11-18, `modelcontextprotocol/kotlin-sdk`, release 0.16.0 of
  2026-10-27; not yet compiled against):
  - Coordinates: `io.modelcontextprotocol:kotlin-sdk-server`, `-client`, `-core`, and the umbrella
    `kotlin-sdk`. License MIT for existing code, Apache-2.0 for new contributions.
  - On main it uses Kotlin 2.4.21 and Ktor 3.6.1, the same as compose-raven. Language and API
    version 2.3; the 0.16.0 notes say 2.2 and Ktor 3.5.2.
  - The server targets JVM, `macosArm64`, `linuxX64`, `linuxArm64`, `mingwX64`, `js` and `wasmJs`
    (node). There is no `macosX64`, iOS or Android target for the server.
  - Transports: Streamable HTTP is recommended (`mcpStreamableHttp { }` and a stateless variant,
    Ktor server helpers). SSE is deprecated. WebSocket (`mcpWebSocket`). stdio
    (`StdioServerTransport(input, output)` over kotlinx-io `Source`/`Sink`; the README shows only
    JVM). `ChannelTransport` in memory, for tests. No transport matrix per target.
  - Exposed as `api`: kotlinx-serialization-json, coroutines, kotlinx-io,
    collections-immutable, ktor-websockets, ktor-server-core and ktor-server-sse. No engine is
    included; we pick one (`ktor-server-cio` is Ktor's native server engine).
  - Server API: `Server(Implementation, ServerOptions(ServerCapabilities(tools =
    Tools(listChanged = true), ...)))`, `addTool`, `addResource`, `addPrompt`. Since 0.15.0,
    duplicate names throw.
- **Settled tension.** [ADR 0003](../../adr/0003-raven-mcp-on-the-kotlin-sdk.md) puts Raven MCP on the
  Kotlin SDK as consistent with [ADR 0002](../../adr/0002-rendering-in-c-bindings-thin.md): one
  implementation, which every binding reaches through one C header.
- **libraven links statically by default** (`RAVEN_BUILD_SHARED` is OFF in Raven's
  `CMakeLists.txt`). A shared library that calls `Rtask_post`/`Raction_fire` must reach the same
  libraven as the app, never a copy of its own.
- Skills: grilling tickets call `grilling` + `domain-modeling`. Call `codebase-design` for the
  header, the matcher extraction and the registry. Keep `compose/GLOSSARY.md` current (new terms:
  Raven MCP, action, tool, call, reply).
- Standing preferences carried over: **keep it slim**, and for the compose-raven pieces **favour
  Compose conventions**.
- Ticket conventions: `ISSUES-FORMAT.md` in this directory.

## Decisions so far

<!-- one line per closed ticket: [title](file): gist -->

- [Spike: the MCP Kotlin SDK as a C library](../archive/0146-spike-the-kotlin-sdk-as-a-c-library.md): it works on `macosArm64`. Python registers tools through plain `@CName` exports and calls run on the UI thread through `Rtask_post` in about 2 ms, with the deferred reply and `listChanged` both working. The dylib is 5.9 MB stripped, adds about 6 MB RSS and 8 threads, and needs a shared libraven. Two Kotlin/Native runtimes coexist, so linking the module in compose apps is for one registry, not to avoid a crash. Code on `spike/mcp`.
- [The raven_mcp.h contract](../archive/0147-the-raven-mcp-h-contract.md): the header is written up in `compose/docs/mcp.md`. It covers zeroed `RMcpConfig`/`RMcpToolDesc` structs, `Rmcp_tool_add` replacing an existing name, everything on the UI thread except the thread-safe `Rmcp_call_*` functions, exactly one reply per call (queued calls fail when their tool is removed), text, JSON or failure replies, hints that only ever claim safety, a fixed loopback port 8765 with no fallback, a log callback with nothing on stdout, and stop and remove tools before `Rshutdown`.
- [Building and shipping Raven MCP](../archive/0148-building-and-shipping-raven-mcp.md): one Kotlin/Native module, `compose/raven-mcp`, builds both the shared Raven MCP library (for C and every binding, generated by `tools/bindgen` and loaded on first use) and the klib for the opt-in `compose-raven-mcp`. It needs the shared libraven, is built by compose's Gradle build only, and ships as `raven-mcp-<target>.zip` with compose-raven's version. Targets are `macosArm64` and `linuxX64`, with no Intel Mac. Registry packages are optional, and nothing runs until `Rmcp_start`.
- [ADR 0003: the Kotlin SDK or MCP in C](../archive/0149-adr-the-sdk-or-mcp-in-c.md): the MCP Kotlin SDK, behind `raven_mcp.h`, recorded in [ADR 0003](../../adr/0003-raven-mcp-on-the-kotlin-sdk.md). It is consistent with ADR 0002 (one implementation, every binding through one header). It is revisited, in C behind the same header, if upstream wants it, an Intel Mac or static libraven is needed, the runtime cost stops someone, or the SDK falls behind the spec.
- [Finders and actions shared by tests and UI tools](../archive/0150-shared-finders-and-actions.md): a new `compose-raven-ui-test` (as Compose's `ui-test`) holds the matchers, finding nodes and the actions, with no `kotlin.test`. `compose-raven-test` sits on top, unchanged. There is one `NodeException`, reported as a test failure or a tool failure. A live tool suspends in `RavenHost.awaitIdle(maxFrames)`, the input is real, and **Idle** is in the glossary.
- [The UI tools](../archive/0151-the-ui-tools.md): one `ui.*` contract for every Raven app (compose over Compose semantics, others over the C driver). Selectors in every call (plus compose's `tag=`), no ids. Six tools: tree, find, click, type, key, screenshot. A Playwright-style text outline, and actions that use real input, wait for idle and reply with the new outline. Screenshots are PNG image content. Read-only tools are hinted, and it's off by default, as its own opt-in.
- [The McpTool composable](../archive/0152-the-mcptool-composable.md): `McpTool` is a composable that registers while composed. It takes typed `@Serializable` arguments with a derived schema (`@McpDescription`), and its handler is a suspend lambda on the UI thread returning `String`/`McpReply` (exceptions are failures, cancellation cancels). It always runs the latest handler, and a second tool with the same name is stacked. Hints map to `RMcpToolHint`. The app turns it on with `application(mcp = McpConfig(…, uiTools))`, all in `compose-raven-mcp`.
- [Python and Rust wrappers](../archive/0153-python-and-rust-wrappers.md): Python's `raven_mcp` (stdlib only) has `@mcp.tool` with the schema from type hints, the return value as the reply, and a `call` parameter to reply later from any thread. Rust's `raven::mcp` (feature `mcp`) takes serde arguments with a `schemars` schema, a `ToolHandle` that withdraws on drop, and a consuming `Call` so the compiler enforces one reply. Raven's actions need no idiom.
- [Who may connect, and what they may do](../archive/0154-who-may-connect.md): Host and Origin are always checked. A bearer token is made once per app and user, in `~/.raven/mcp/<app>.json` (0600), with `""` opting out with a warning. It's on only when the code says so: `fromEnvironment()` reads `RAVEN_MCP`/`_PORT`/`_UI`, and `RAVEN_MCP=0` is a kill switch. There's an `on_session` callback and an optional `McpStatus()` marker. Confirming destructive tools stays in the fog.
- [Exposing Raven's actions](../archive/0155-exposing-ravens-actions.md): a fork change, proposed upstream on its own, lists actions and gives them descriptions and a list callback. `Rmcp_expose_actions(prefix)` publishes bound actions only (never built-ins, invalid names skipped, no hints, kept in step). `Rmcp_expose_action(name, description, hints)` publishes one on purpose. The sender is the focused window, the app's own tools win, and the reply comes after idle.

## Not yet specified

Closed on 2026-11-18 with the destination reached: Raven MCP's design is written into
`compose/docs/mcp.md` and [ADR 0003](../../adr/0003-raven-mcp-on-the-kotlin-sdk.md), and every ticket
is archived. Implementation is charted as tickets 0166 to 0182, starting from
[The Raven MCP library](../archive/0174-the-raven-mcp-library.md). The patches below are the fog that remains:
each waits for a real need, and they seed a later map when one is charted.

- **Resources and prompts**: MCP resources (an app's state as readable URIs, the widget tree as
  one) and prompts. Tools first; the header should leave room for them.
- **stdio transport**: an app launched by the MCP client as a subprocess. Needs a kotlinx-io source
  over `read(0)` on native, and the app's own stdout kept off the channel.
- **Several windows** in the UI tools (`onWindow(title)`, dialogs), as in the test fog.
- **Checking arguments against the schema**: the library passes `input_schema_json` to agents but
  doesn't check calls against it; each tool checks its own arguments. Validate in the library if
  tools keep repeating the same checks.
- **More UI tools**: scrolling, selecting and semantic actions (expand a row, set a value), when an
  agent needs them beyond `ui.click`/`ui.type`/`ui.key`.
- **Agent-side features**: sampling, elicitation, progress and cancellation of long calls.
- **Windows (`mingwX64`)**: the SDK targets it. It follows
  [0141](../0141-map-widget-on-windows-and-linux.md) and phase 3's Windows target, and has to be checked
  linking a Kotlin/Native (MinGW) library against Raven's MSVC-built `raven.dll`.
- **A static Raven MCP library**: `libraven_mcp.a` (Kotlin/Native can build one) for C and C++ apps
  that link libraven statically. Wait until one asks.
- **Intel Macs**: only if the MCP Kotlin SDK gains `macosX64`, or ADR 0003 moves Raven MCP into C.
- **Confirming destructive tools**: asking the user in the app before a tool marked destructive
  runs.

## Out of scope

- Building it. Implementation tickets are charted when this map closes.
- An MCP *client* in Raven (an app calling out to agents or other servers).
- Targets beyond the desktop ones: iOS, Android, and Emscripten/web builds.
- Connections from other machines: the server listens on the loopback interface only.
- Making Raven work with the Matt Pocock skills (seeing, driving and testing a Raven UI in every
  language), charted on its own as
  [Raven works with the Matt Pocock skills](0157-raven-works-with-the-matt-pocock-skills.md). It took
  [Prototyping Raven UI with an agent](../archive/0156-prototyping-raven-ui-with-an-agent.md) and the fog of UI
  tools for apps that aren't compose.
