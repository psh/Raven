---
status: closed
---

# compose-raven-ui-test, and idle in a live app

Split the finders and actions out of `compose-raven-test` so a live app's tools can use them.
Design: `compose/docs/spec.md`, section 9, "Shared with Raven MCP".

**Depends on:** nothing open.

## Deliverables

- [x] `compose-raven-ui-test`: the matchers (public, `raven.compose.ui.test`), finding nodes, the `perform…` actions and `fetch…` reads (`@InternalComposeRavenApi`), with no `kotlin.test`.
- [x] `compose-raven-test` on top (`api`), its public API unchanged; its tests pass as they are.
- [x] One `NodeException` with today's messages, reported as `AssertionError` by the test module.
- [x] `RavenHost.awaitIdle(maxFrames)`, suspending on `RavenDispatcher` until a frame ends idle.
- [x] The new module in the spec's module table and the README.

## Resolution

Done on macOS (arm64), on the `compose-raven` branch. `./gradlew allTests` passes twice in a row, 552
tests. `compose-raven-test`'s own tests and every module that tests through it pass unchanged.

- **`compose-raven-ui-test`** (`api` foundation, `implementation` native, no `kotlin.test`), in
  package `raven.compose.ui.test`:
  - `NodeMatcher.kt`, moved with its history. The matchers stay public. A matcher's `matches`, and
    `nodeMatcher(description, matches)` for the UI tools' own matchers, are
    `@InternalComposeRavenApi`. The annotation can't go on a constructor, so the factory stands in
    for one.
  - `Nodes.kt`: `NodeException`, `RavenHost.findNodes` and `findNode(matcher, index)`,
    `singleWindow`, `openMenus`, `clickOpenMenuItem`, `injectKey`, `widgetRect` and the menu
    geometry.
  - `FoundNode.kt`: the `fetch…` reads (text, checked, enabled, focused, selected index, value) and
    every `perform…` action, with the messages they had before. None of it waits or draws a frame.
- **`compose-raven-test`** keeps the same public API. `NodeInteraction` waits for idle, finds the node
  again and calls the shared read or action. Assertions compare the reads. A `NodeException` becomes
  `kotlin.test.fail` with its message, so failures are still `AssertionError`s with the same words.
  The scroll actions draw their frame here.
- **`RavenHost.awaitIdle(maxFrames = 120)`**: true on the first idle frame, false after `maxFrames`
  busy ones. It wakes the app's loop while anything waits, and `close()` drops any waiter still
  there.
  - **Not quite as designed: a frame is judged as the next frame callback starts.** Checking at the
    end of the callback, as first written, almost always found the host idle. Whatever a frame
    itself changes, even a value an animation writes every frame, is composed within that frame. A
    check at the start of the next callback still sees what arrived in between: input, posted
    tasks, timers. Only frames that start after the call count. Section 9 of the spec now says so.
  - Three tests in `RavenHostTest`. The first resumes once the change it made has been applied. The
    second gives up after 5 frames, kept busy by a task that posts itself again every frame. The
    third keeps a `waitFrame()` loop drawing. (A zero-interval repeating Raven timer never fired in
    headless frames, so the busy test uses the task.)
- **Docs**: the spec's module table already had the module. Section 9 names the shared API and
  spells out `awaitIdle`'s frames, and the README says what the module is for.
