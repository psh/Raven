---
status: closed
---

# Small widgets: taskbar progress, badge and Dock menu

Add `Window(taskbarProgress)`, `TaskbarBadge(text)` and `ApplicationScope.DockMenu { }` over
Raven's taskbar and Dock. Spec: [8: Tray, notifications and taskbar](../../spec.md#tray-notifications-and-taskbar-phase-5)
and section 12. Planned in [Small widgets: notifications and tray](0213-small-widgets-notifications-and-tray.md).

**Depends on:** [Small widgets: Tray and notifications](0231-small-widgets-tray-and-notifications.md).

## Deliverables

- [x] `TaskbarProgress` (`Normal(value)`, `Indeterminate`, `Paused(value)`, `Error(value)`) and `Window(taskbarProgress = null)`, through `Rwindow_set_taskbar_progress`, cleared when null.
- [x] `TaskbarBadge(text)`: the badge while it is composed, the last one composed winning, none once every one has left.
- [x] `ApplicationScope.DockMenu { }` from the menu composables, through `Rtaskbar_set_menu`; nothing off macOS.
- [x] Tests through the same recording seam: progress values and clearing, badges as they come and go, the Dock menu's items.
- [x] The spec's section and section 12's rows checked against what was built; `api-coverage.md` updated (the jump list left with the Windows target).

## Resolution

Built on 2026-11-27.

- **`TaskbarProgress`** (`Normal`, `Indeterminate`, `Paused`, `Error`) and
  **`Window(taskbarProgress = null)`** are in compose-raven-ui's `Window.kt`. The progress goes
  through `WindowCalls.setTaskbarProgress`, only when it changes, with values coerced to 0 to 1.
  Null clears it with `R_TASKBAR_PROGRESS_NONE`; null at the start makes no call.
- **`TaskbarBadge(text)`** and **`ApplicationScope.DockMenu { }`** are in foundation's
  `Taskbar.kt`. Each keeps a per-host stack in the order of composition, and the last one shows:
  - A badge's own change shows only while it is the last.
  - An empty text shows no badge, and none once every one has left.
  - The Dock menu is a detached Raven Menu, as the tray's is, given to `Rtaskbar_set_menu`, and
    `R_INVALID_ID` once none is left. The plan didn't ask for last-composed-wins on the Dock
    menu, but without it a second one leaving would clear the first's.
- **The seam**: `ShellCalls` gains `setBadge` and `setDockMenu`.
- **Tests**:
  - `WindowTest.theTaskbarProgressIsSetAndCleared`: every kind, a coerced value, and clearing.
  - `TaskbarTest` (4):
    - badges as they come and go, the last winning, and an earlier one's change not taking over;
    - an empty badge showing none;
    - the Dock menu's items, with one added later and the menu cleared on leaving;
    - two Dock menus, the last winning, then the first again.

  The ui and foundation suites pass.
- **`api-coverage.md`**: 547 of 1,704 functions, `raven_shell.h` 33 of 60. The jump list is left
  for the Windows target. The spec's section records the coercion, the stacks and the empty badge.
