---
status: open
---

# The public compose-raven-test module

Move the test harness out of foundation's `src/testHarness/kotlin` into a `compose-raven-test`
module that apps add as a test dependency, and give it the API decided in
[Public test module for apps](archive/0029-app-testing-module.md). Spec:
[Public test module](../spec.md#public-test-module-phase-4). Publishing stays with
[Packaging and publishing](archive/0031-packaging-and-publishing.md), in the release phase.

**Depends on:** nothing open.

## Deliverables

- [ ] `compose-raven-test`, package `raven.compose.ui.test` (as Compose's `androidx.compose.ui.test`): `api` on `compose-raven-foundation` and `kotlin-test`, `implementation` on `compose-raven-native`, so no Raven type is in its public API.
- [ ] What reaches Raven (`host`, `fetchWidget()`, `widgetRect`, `frame()`, `openMenus`) stays for compose-raven's own tests, public behind `@InternalComposeRavenApi`.
- [ ] One harness: foundation's, navigation3's, the samples' and the spikes' tests depend on the module instead of compiling `src/testHarness/kotlin`, which goes.
- [ ] The decided API that is missing: `runRavenTest(checkLeaks = true)`; `onAllNodesWithText/Tag/ContentDescription`; `onNode(matcher)` with `hasText`, `hasTestTag`, `hasContentDescription`, `isEnabled`, `isFocused`, `isToggleable`, combined with `and`/`or`; `performTextReplacement`, `performKeyPress(KeyShortcut)`, `performScrollBy`; `assertIsDisplayed`, `assertTextEquals`, `assertIsEnabled`/`assertIsNotEnabled`, `assertValueEquals`.
- [ ] A leak failure names the composables whose widgets were not released, by test tag where they have one.
- [ ] The samples' tests use only the public API where it covers what they check, as an app's would.
- [ ] The spec lists what the harness grew beyond the decision (`pressKey`, `clickOpenMenuItem`, `performSelection`, `performValueInput`, `performColorChange`, double and right clicks, mouse enter and exit, `performExternalDrop`, `assertSelectedIndex`) and documents the module in `compose/README.md`.
- [ ] Tests of the new API in the module itself; `./gradlew build allTests` passes.
