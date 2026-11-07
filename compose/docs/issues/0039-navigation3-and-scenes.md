---
status: open
wayfinder: grilling
parent: 0023
blocked_by: [0024, 0025]
assignee: null
---

# Navigation 3 and scenes

## Question

How does compose-raven support Navigation 3, with destinations, dialogs included, as composable entries arranged into **Scenes**? `navigation3-runtime` (`NavKey`, `NavEntry`, `entryProvider`, `rememberNavBackStack`, `NavEntryDecorator`) runs on our targets unchanged. The Scene layer (`Scene`, `SceneStrategy`, `SceneStrategyScope`, `OverlayScene`, `DialogScene`/`DialogSceneStrategy`, `SinglePaneScene`/`SinglePaneSceneStrategy`, `SceneDecoratorStrategy`, `SceneState`) and `NavDisplay` live in `navigation3-ui`, which needs Compose UI and has no macOS variant. Decide:
- how compose-raven provides the Scene layer and a `NavDisplay` with Navigation 3's names and signatures in its own package (copy the Apache-2.0 sources, or reimplement)
- how `DialogScene` uses the [Multiple windows, dialogs and popups](archive/0024-multiple-windows.md) `Dialog`
- what happens with transitions (none at first? Raven's appear transitions?)
- where back events come from on desktop (Escape, the mouse back button, Cmd+[ / Alt+Left, through `navigationevent`)
- whether a `WindowSceneStrategy` can open entries as separate windows
- the first verification: `rememberNavBackStack` and the saved-state decorator work under compose-raven's host
