---
status: open
---

# compose-raven-navigation3

Navigation 3 over compose-raven. Spec: [8: Navigation 3](../spec.md#navigation-3-phase-3-compose-raven-navigation3).

**Depends on:** [Windows that come and go, and their owners](archive/0112-windows-that-come-and-go-and-their-owners.md), [Dialog and the default dialogs](archive/0130-dialog-and-the-default-dialogs.md).

## Deliverables

- [ ] The module, depending on `navigation3-runtime`, with the Scene layer adapted from AndroidX 1.2.1 and `NavDisplay(backStack, onBack, sceneStrategy, transition, entryProvider)`.
- [ ] `DialogSceneStrategy` rendering with `Dialog`, dismissal calling `onBack`.
- [ ] `NavTransition` with appear transitions by push or pop.
- [ ] Back: each window's `NavigationEventDispatcher` fed by the mouse Back button and `Window(backKeys)`.
- [ ] Documented: on Kotlin/Native `rememberNavBackStack` needs a `SavedStateConfiguration` declaring the keys ([Verify Navigation 3 under the host](archive/0097-verify-navigation-3-under-the-host.md)).
- [ ] Tests: push and pop, entries keeping saveable state, a dialog destination, back keys.
