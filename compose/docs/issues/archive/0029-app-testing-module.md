---
status: closed
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: Paul Hawke
---

# Public test module for apps

## Question

How does the internal test harness from [Testing strategy](0012-testing-strategy.md) become a public `compose-raven-test` module so apps can test their own UIs? Decide its API surface (finders, actions, assertions, `runRavenTest`), whether it depends on `raven-native` publicly, how leak checks are offered, and how it ships alongside the library.

## Resolution

- **Module**: `compose-raven-test`, an apps' test dependency. It depends on `compose-raven-foundation` with `api` and on `raven-native` with `implementation`, so no Raven type reaches app tests.
  - The internal harness from [Testing strategy](0012-testing-strategy.md) **moves into it**, and foundation's own tests depend on it too. There is one harness, not two.
  - What the harness needs from foundation (`RavenHost`, the node tree, node accessors) becomes public behind `@InternalComposeRavenApi` (`@RequiresOptIn`), as Compose does for its internal APIs. Kotlin has no clean cross-module `internal`.
- **API**, a practical subset of Compose's `ComposeUiTest`:
  - **Entry**: `runRavenTest(checkLeaks = true) { setContent { ... } }`, for a single window.
  - **Finders**: `onNodeWithText(text, substring = false)`, `onNodeWithTag`, `onNodeWithContentDescription`, their `onAllNodesWith...` forms, and `onNode(matcher)`. The matchers `hasText`, `hasTestTag`, `hasContentDescription`, `isEnabled`, `isFocused` and `isToggleable` combine with `and` and `or`.
  - **Actions** use Raven's real input injection: `performClick()`, `performTextInput(text)`, `performTextReplacement(text)`, `performKeyPress(KeyShortcut)`, `performScrollBy(dx, dy)`.
  - **Assertions**: `assertExists`, `assertDoesNotExist`, `assertIsDisplayed`, `assertTextEquals`, `assertIsChecked`/`assertIsNotChecked`, `assertIsEnabled`/`assertIsNotEnabled`, `assertIsFocused`, and `assertValueEquals(...)` for sliders and spin boxes. They read typed accessors on the node, never raw Raven getters.
  - Tree dumps (`printToLog`) come later.
- **Clock**: `mainClock.autoAdvance = true` by default, so `waitForIdle()` runs frames until nothing is pending. `mainClock.advanceTimeBy(ms)` and `advanceTimeByFrame()` step frames with Raven's fixed animation time step, for checking an animation midway. `LaunchedEffect` delays and coroutine timers run on the same clock.
- **Leak checks**: `checkLeaks = true` by default. After shutdown, the test fails if Raven holds memory or any `StableRef` is alive, and the message lists the composables whose widgets were not released, by test tag where they have one. `checkLeaks = false` opts out.
- **Limits**: `setContent` is one window. Tests of `application { }` with several windows and dialogs (`onWindow(title)`, `onDialog()`) go to the Multi-window tests fog. There are no screenshot tests, because the null renderer draws nothing. Tests run on each target's own host in CI. Publishing belongs to [Packaging and publishing](../0031-packaging-and-publishing.md).
