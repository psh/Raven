---
status: closed
---

# compose-raven-navigation3

Navigation 3 over compose-raven. Spec: [8: Navigation 3](../../spec.md#navigation-3-phase-3-compose-raven-navigation3).

**Depends on:** [Windows that come and go, and their owners](0112-windows-that-come-and-go-and-their-owners.md), [Dialog and the default dialogs](0130-dialog-and-the-default-dialogs.md).

## Deliverables

- [x] The module, depending on `navigation3-runtime`, with the Scene layer adapted from AndroidX 1.2.1 and `NavDisplay(backStack, onBack, sceneStrategy, transition, entryProvider)`.
- [x] `DialogSceneStrategy` rendering with `Dialog`, dismissal calling `onBack`.
- [x] `NavTransition` with appear transitions by push or pop.
- [x] Back: each window's `NavigationEventDispatcher` fed by the mouse Back button and `Window(backKeys)`.
- [x] Documented: on Kotlin/Native `rememberNavBackStack` needs a `SavedStateConfiguration` declaring the keys ([Verify Navigation 3 under the host](0097-verify-navigation-3-under-the-host.md)).
- [x] Tests: push and pop, entries keeping saveable state, a dialog destination, back keys.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 501 tests, 6 of them `NavDisplayTest`'s.

- **The module**, `compose-raven-navigation3` (`settings.gradle.kts`), depends on
  `compose-raven-foundation` and `navigation3-runtime` 1.2.1 (both in the version catalog now,
  with `navigationevent` 1.1.1, the one the runtime brings).
- **The Scene layer**, adapted from AndroidX 1.2.1's `navigation3-ui` without Compose UI and with
  its names: `Scene` (`key`, `entries`, `previousEntries`, `metadata`, `content`), `OverlayScene`
  (`overlaidEntries`), `SceneStrategy` with `then`, `SceneStrategyScope` (`onBack`),
  `SinglePaneSceneStrategy` and `SinglePaneScene`, `SceneDecoratorStrategy`, and `SceneState`
  (`currentScene` and `overlayScenes`, topmost first) from `calculateSceneState`. It is a slimmer
  version of AndroidX's: no shared elements, predictive back progress or scene-level animations,
  which have nothing to draw them here.
- **`DialogSceneStrategy`** shows an entry whose metadata is `DialogSceneStrategy.dialog(properties)`
  in a `Dialog` over the scenes under it, and declines for any other; dialogs stack, and their
  dismissal (Escape) calls `onBack`.
- **`NavDisplay(backStack, modifier, onBack, entryDecorators, sceneStrategy,
  sceneDecoratorStrategies, transition, entryProvider)`**: entries are decorated (the saveable-state
  decorator by default) with `rememberDecoratedNavEntries`, laid out in scenes, the current one in
  a group of its key, the overlays over it. **`NavTransition(push, pop)`** gives a new scene a
  Raven appear transition by whether the back stack grew or shrank; the first scene shown has
  none, and there is none by default.
- **Back**: each `Window` gains a `NavigationEventDispatcher` (`LocalNavigationEventDispatcher`;
  `navigationevent-compose` has no macOS variant, so compose-raven provides its own local),
  disposed with the window. With **`Window(backKeys = true)`**, the mouse's Back button (taken
  wherever the pointer is) and Cmd+[ or Alt+Left that `onKeyEvent` and the widgets left go back
  through it. `NavDisplay` adds a handler, enabled while the current scene has previous entries or
  an overlay is open, that calls `onBack`. Escape only closes dialogs.
- **Documented**: on Kotlin/Native `rememberNavBackStack` needs a `SavedStateConfiguration`
  declaring the keys, as spec section 8 shows and [the spike](0097-verify-navigation-3-under-the-host.md)
  verified; the tests do it so.
- **Not checked**: that a transition plays, which Raven has no getter for; the tests show the
  scenes come and go without errors.
- **Tests** (`NavDisplayTest`): push and pop; an entry's `rememberSaveable` state kept under another;
  a dialog destination over the scene, dismissed by Escape going back; Cmd+[, Alt+Left and the
  mouse's Back button going back, and nothing at the root; `backKeys = false`; the scene state of
  two stacked dialogs.
