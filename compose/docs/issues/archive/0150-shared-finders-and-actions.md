---
status: closed
wayfinder: grilling
parent: 0145
blocked_by: []
assignee: Paul Hawke
---

# Finders and actions shared by tests and UI tools

## Question

How do the finders, matchers and actions in `compose-raven-test` move into code that both
`runRavenTest` and the MCP UI tools use? Use `codebase-design`.

- Today they live in `compose-raven-test` (`NodeMatcher.kt`, `NodeInteraction.kt`, `RavenTest.kt`),
  run against a headless `RavenHost`, and fail with `kotlin.test` assertions. The UI tools need the
  same against an app's **live** host, returning errors instead of throwing assertions.
- Which module do they move to: a new internal one, `compose-raven-ui` behind
  `@InternalComposeRavenApi`, or `compose-raven-mcp` with the test module depending on it? It must
  not drag `kotlin.test` into an app.
- What is the seam: a finder that yields nodes, and the test module's `assert*`/`perform*` as one
  adapter over it and the UI tools as another?
- **Waiting for idle**: the harness waits for recomposition and layout between steps under a test
  clock. A live app runs real frames. What does "settled" mean there, and how does a tool wait for
  it on the UI thread without blocking it?
- Keep the test module's public API unchanged ([Public test module](0029-app-testing-module.md)).

## Resolution

Decided with Paul on 2026-11-18, and written into `compose/docs/spec.md` (section 2's module table,
and "Shared with Raven MCP" under section 9's public test module).

- **A new `compose-raven-ui-test`**, as Compose splits `ui-test` from `ui-test-junit4`. It holds the
  matchers, finding nodes, the `perform…` actions and the `fetch…` reads, works against any
  `RavenHost` (headless or live), and doesn't depend on `kotlin.test`. `compose-raven-test` keeps
  `runRavenTest`, `mainClock`, the leak checks and `assert…` on top of it (`api`), with its public
  API unchanged.
- **The matchers stay public** in `raven.compose.ui.test`. Finding nodes and running actions are
  `@InternalComposeRavenApi`, for the test module and `compose-raven-mcp`.
- **One `NodeException`** carries today's messages. The tests report it as an `AssertionError`, and
  the UI tools as `Rmcp_call_fail`.
- **`RavenHost.awaitIdle(maxFrames)`** suspends on `RavenDispatcher` until a frame ends idle, and
  gives up at the limit, so the tool replies saying the app is busy. The tests keep drawing their
  own frames. Both use `isIdle()`.
- **Real input in live windows**: an agent can't press a disabled button, and its clicks move hover
  and focus. The UI tools document that.
- **Glossary**: **Idle** (avoid "settled", "stable").
