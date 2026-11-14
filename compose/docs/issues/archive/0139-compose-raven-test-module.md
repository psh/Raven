---
status: closed
---

# The public compose-raven-test module

Move the test harness out of foundation's `src/testHarness/kotlin` into a `compose-raven-test`
module that apps add as a test dependency, and give it the API decided in
[Public test module for apps](0029-app-testing-module.md). Spec:
[Public test module](../../spec.md#public-test-module-phase-4). Publishing stays with
[Packaging and publishing](0031-packaging-and-publishing.md), in the release phase.

**Depends on:** nothing open.

## Deliverables

- [x] `compose-raven-test`, package `raven.compose.ui.test` (as Compose's `androidx.compose.ui.test`): `api` on `compose-raven-foundation` and `kotlin-test`, `implementation` on `compose-raven-native`, so no Raven type is in its public API.
- [x] What reaches Raven (`host`, `fetchWidget()`, `widgetRect`, `frame()`, `openMenus`) stays for compose-raven's own tests, public behind `@InternalComposeRavenApi`.
- [x] One harness: foundation's, navigation3's, the samples' and the spikes' tests depend on the module instead of compiling `src/testHarness/kotlin`, which goes.
- [x] The decided API that is missing: `runRavenTest(checkLeaks = true)`; `onAllNodesWithText/Tag/ContentDescription`; `onNode(matcher)` with `hasText`, `hasTestTag`, `hasContentDescription`, `isEnabled`, `isFocused`, `isToggleable`, combined with `and`/`or`; `performTextReplacement`, `performKeyPress(KeyShortcut)`, `performScrollBy`; `assertIsDisplayed`, `assertTextEquals`, `assertIsEnabled`/`assertIsNotEnabled`, `assertValueEquals`.
- [x] A leak failure names the composables whose widgets were not released, by test tag where they have one.
- [x] The samples' tests use only the public API where it covers what they check, as an app's would.
- [x] The spec lists what the harness grew beyond the decision (`pressKey`, `clickOpenMenuItem`, `performSelection`, `performValueInput`, `performColorChange`, double and right clicks, mouse enter and exit, `performExternalDrop`, `assertSelectedIndex`) and documents the module in `compose/README.md`.
- [x] Tests of the new API in the module itself; `./gradlew build allTests` passes.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 542 tests, 6 of them the module's own `RavenTestTest`.

- **The module**: `compose-raven-test`, package `raven.compose.ui.test`. It has `api` on
  `compose-raven-foundation` and `kotlin-test`, and `implementation` on `compose-raven-native`. Its
  main sources opt in to `@InternalComposeRavenApi`. Its tests don't, so they check the API an app
  sees.
- **One harness**: `RavenTest.kt` moved out of foundation's `src/testHarness/kotlin`, which is gone.
  Foundation's, navigation3's, the 11 samples' and the 3 spikes' tests depend on the module instead.
- **Behind the opt-in**: `host`, `fetchNode()`, `fetchWidget()`, `findNodes`, `openMenus` and
  `widgetRect` hand out Raven types, so they stay for compose-raven's own tests. `frame()` and
  `checkFiles()` carry none, so they are public.
- **The decided API, now there**:
  - `runRavenTest(checkLeaks)`.
  - `onAllNodesWith…`, and `onNode`/`onAllNodes(matcher)` with `hasText`, `hasTestTag`,
    `hasContentDescription`, `isEnabled`, `isNotEnabled`, `isFocused` and `isToggleable`, combined
    with `and`/`or`.
  - Collections with `assertCountEquals`, `fetchCount`, `[index]`, `onFirst` and `onLast`.
  - `performTextReplacement`, `performKeyPress(KeyShortcut)` and `performScrollBy`.
  - `assertIsDisplayed`, `assertTextEquals`, `assertIsEnabled`/`assertIsNotEnabled`, and
    `assertValueEquals`, which replaces `assertValue` and now reads sliders and progress bars too.
- **Grown while porting, now in the spec**:
  - `setApplicationContent { }`, for an app's own `Window`; the samples used `host.start` for this.
  - `fetchText()` and `assert(matcher)`, for checks on part of a text.
  - `openMenuCount()`, `pressKey(KeyShortcut)`, and the actions and assertions phases 1–3 added.
- **Leak report**: `CallbackRegistry` keeps its live references, and a leak failure names their
  composables by name and test tag (`RavenNode.name` is now public behind the opt-in). Only a bug
  in the binding can leak a widget, so this report has no test of its own.
- **Samples**:
  - components' and xml-translator's tests now use the public API alone (`fetchText`).
  - music's reads texts with `fetchText`, and glass's counts menus with `openMenuCount`.
  - Every sample hosts its window with `setApplicationContent`.
  - Tests that check theme colours or geometry still opt in and read Raven: no public API covers
    those.
- **Docs**: the spec's testing section and module table describe the module. `compose/README.md`
  gains "Testing an app".
- **Not done here**: publishing the module, which waits for
  [Packaging and publishing](0031-packaging-and-publishing.md).
