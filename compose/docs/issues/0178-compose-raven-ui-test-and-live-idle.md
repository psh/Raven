---
status: open
---

# compose-raven-ui-test, and idle in a live app

Split the finders and actions out of `compose-raven-test` so a live app's tools can use them.
Design: `compose/docs/spec.md`, section 9, "Shared with Raven MCP".

**Depends on:** nothing open.

## Deliverables

- [ ] `compose-raven-ui-test`: the matchers (public, `raven.compose.ui.test`), finding nodes, the `perform…` actions and `fetch…` reads (`@InternalComposeRavenApi`), with no `kotlin.test`.
- [ ] `compose-raven-test` on top (`api`), its public API unchanged; its tests pass as they are.
- [ ] One `NodeException` with today's messages, reported as `AssertionError` by the test module.
- [ ] `RavenHost.awaitIdle(maxFrames)`, suspending on `RavenDispatcher` until a frame ends idle.
- [ ] The new module in the spec's module table and the README.
