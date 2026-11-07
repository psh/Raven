---
status: closed
wayfinder: grilling
parent: 0023
blocked_by: [0024, 0025]
assignee: Paul Hawke
---

# Navigation 3 and scenes

## Question

How does compose-raven support Navigation 3, with destinations, dialogs included, as composable entries arranged into **Scenes**? `navigation3-runtime` (`NavKey`, `NavEntry`, `entryProvider`, `rememberNavBackStack`, `NavEntryDecorator`) runs on our targets unchanged. The Scene layer (`Scene`, `SceneStrategy`, `SceneStrategyScope`, `OverlayScene`, `DialogScene`/`DialogSceneStrategy`, `SinglePaneScene`/`SinglePaneSceneStrategy`, `SceneDecoratorStrategy`, `SceneState`) and `NavDisplay` live in `navigation3-ui`, which needs Compose UI and has no macOS variant. Decide:
- how compose-raven provides the Scene layer and a `NavDisplay` with Navigation 3's names and signatures in its own package (copy the Apache-2.0 sources, or reimplement)
- how `DialogScene` uses the [Multiple windows, dialogs and popups](0024-multiple-windows.md) `Dialog`
- what happens with transitions (none at first? Raven's appear transitions?)
- where back events come from on desktop (Escape, the mouse back button, Cmd+[ / Alt+Left, through `navigationevent`)
- whether a `WindowSceneStrategy` can open entries as separate windows
- the first verification: `rememberNavBackStack` and the saved-state decorator work under compose-raven's host

## Resolution

Facts: `navigation3-runtime` 1.2.1 is published for our targets with no Compose UI. The Scene layer and `NavDisplay` are in `navigation3-ui` (Apache-2.0), which needs Compose UI and Animation and has no macOS variant. [Multiple windows, dialogs and popups](0024-multiple-windows.md) gave windows lifecycle and saved-state owners and a controlled `Dialog`. [Generic input and focus](0025-generic-input-and-focus.md) made the back signals visible. Raven keeps a stack of dialogs per window (`rvn_window_top_dialog`).

- **Module**: an opt-in `compose-raven-navigation3`, depending on `compose-raven-foundation` and `androidx.navigation3:navigation3-runtime`, so foundation has no navigation dependency.
- **Scene layer adapted from AndroidX 1.2.1's Apache-2.0 sources**: `Scene`, `SceneStrategy`, `SceneStrategyScope`, `OverlayScene`, `SinglePaneScene`/`SinglePaneSceneStrategy`, `DialogScene`/`DialogSceneStrategy`, `SceneDecoratorStrategy` and `SceneState`. Names and signatures are kept, in `raven.compose.navigation3.scene` and `raven.compose.navigation3.ui`. Compose UI and Animation are stripped (compose-raven's `Modifier`, no `ContentTransform`). The module has a NOTICE with attribution. Behaviour matches Android's, including overlay stacking and previous entries.
- **`NavDisplay(backStack, onBack, modifier, entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()), sceneStrategy = DialogSceneStrategy<NavKey>() then SinglePaneSceneStrategy(), transition: NavTransition? = null, entryProvider)`**:
  - It renders the current scene plus overlay scenes, as on Android.
  - Entry state is kept by the runtime's saveable decorator through the window's owners.
  - There are no transitions by default. `NavTransition(push = AppearTransition.SlideLeft, pop = AppearTransition.SlideRight)` plays a Raven appear transition on the scene that becomes visible, choosing push or pop from how the back stack changed, in place of `transitionSpec`/`popTransitionSpec` (documented).
  - The ViewModel decorator (`lifecycle-viewmodel-navigation3`) goes to the fog.
- **Dialog destinations**: `entry<K>(metadata = DialogSceneStrategy.dialog(DialogProperties(...))) { }`. `DialogScene` is an `OverlayScene` drawn with compose-raven's `Dialog`, whose `onDismissRequest` is `onBack`, so Escape or a click outside pops the back stack, which stays the source of truth. Dialogs stack, because Raven stacks them per window.
- **Back events**: each `Window` provides a `navigationevent` `NavigationEventDispatcher`, which `NavDisplay` registers with. The window feeds it from the mouse **Back** button, **Cmd+[** on macOS and **Alt+Left** on Windows and Linux. `Window(backKeys = ...)` changes or disables that set. Escape is not a back key on desktop, because it closes dialogs (already handled by `Dialog`). **Verify first** that `navigationevent` (and `navigationevent-compose` if used) resolves without Compose UI. If not, the window calls the registered back handlers directly.
- **`WindowSceneStrategy`** (entries as separate `Window`s, closing as `onBack`) goes to the fog.
- **Verify first** (the roadmap item): `rememberNavBackStack` and the saveable decorator work under the host and window owners, together with the kotlinx-serialization plugin, because `NavKey`s are `@Serializable`, and `navigationevent` as above. The spec tells apps to apply `org.jetbrains.kotlin.plugin.serialization`.
