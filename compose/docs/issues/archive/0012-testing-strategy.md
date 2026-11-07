---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0007, 0008]
assignee: Paul Hawke
---

# Testing strategy

## Question

How is compose-raven tested? Can Raven's `null` render and platform backends run a composition headlessly in a Kotlin/Native test, and what does a test assert on (the Raven widget tree, properties, simulated events)?

## Resolution

- **Harness location**: internal, in `compose-raven-foundation`'s test source set, testing compose-raven itself. A public `compose-raven-test` for app authors is in the App testing fog.
- **`RavenHost`**: an internal module holding everything `ravenApplication` sets up ([Frame loop and threading](0008-frame-loop-and-threading.md): window, Overlay root, Recomposer, clock, write observer, shutdown order) behind `start(content)`, `frame()` and `close()`. `ravenApplication` is `RavenHost` plus the blocking `Rwait_frame` loop. The test harness is `RavenHost` plus frames it drives itself, so tests and real apps share one path.
- **Headless**: `Rinit_ex` with `R_BACKEND_NULL`, `headless = R_TRUE`, `worker_threads = -1`, and a fixed `Rset_animation_time_step`. `waitForIdle()` calls `Rdraw_frame()` (which never sleeps) until the Recomposer has no pending work, no snapshot apply is pending, and no controlled check is waiting ([Events and state](0010-events-and-state.md)).
- **Test API**, a small part of Compose's `ComposeUiTest`:
  - `runRavenTest { setContent { ... }; ... }` is the entry point.
  - Finders `onNodeWithText` and `onNodeWithTag` search compose-raven's node tree. `Modifier.testTag` stores the tag on the node only, with no Raven call.
  - Actions go through Raven's real input path. `performClick()` injects a press and release at the centre of `Rwidget_get_rect`. `performTextInput` clicks to focus, then calls `Rwindow_inject_text`.
  - Assertions include `assertExists` and `assertIsChecked`, plus widget values read through `raven-native` getters (tests are internal and can see it).
- **Leak checks after every test**: after `close()`/`Rshutdown`, `Rmemory_get_stats` reports zero blocks held and the `raven.native` callback registry reports zero live `StableRef`s.
- **Isolation**: Raven starts fresh for each test (`Rinit`/`Rshutdown`). Raven resets its global context, but none of its own tests re-initialise in one process, so the implementation verifies this first. If it fails, it is fixed in the `psh/Raven` fork, which becomes a second required Raven change.
- **Pure logic** (Modifier folding, move-index adjustment, mirror-list bookkeeping) gets plain Kotlin unit tests with no Raven. There are no screenshot tests, because the null renderer draws nothing.
- **CI**: a compose job in the existing `.github/workflows/build.yml` on a macOS and a Linux runner, running `./gradlew -p compose allTests`. Each target is tested on its own host ([Kotlin/Native targets and build](0005-kotlin-native-targets-and-build.md)).
