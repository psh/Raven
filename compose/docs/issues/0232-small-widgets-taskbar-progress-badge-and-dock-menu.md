---
status: open
---

# Small widgets: taskbar progress, badge and Dock menu

Add `Window(taskbarProgress)`, `TaskbarBadge(text)` and `ApplicationScope.DockMenu { }` over
Raven's taskbar and Dock. Spec: [8: Tray, notifications and taskbar](../spec.md#tray-notifications-and-taskbar-phase-5)
and section 12. Planned in [Small widgets: notifications and tray](archive/0213-small-widgets-notifications-and-tray.md).

**Depends on:** [Small widgets: Tray and notifications](0231-small-widgets-tray-and-notifications.md).

## Deliverables

- [ ] `TaskbarProgress` (`Normal(value)`, `Indeterminate`, `Paused(value)`, `Error(value)`) and `Window(taskbarProgress = null)`, through `Rwindow_set_taskbar_progress`, cleared when null.
- [ ] `TaskbarBadge(text)`: the badge while it is composed, the last one composed winning, none once every one has left.
- [ ] `ApplicationScope.DockMenu { }` from the menu composables, through `Rtaskbar_set_menu`; nothing off macOS.
- [ ] Tests through the same recording seam: progress values and clearing, badges as they come and go, the Dock menu's items.
- [ ] The spec's section and section 12's rows checked against what was built; `api-coverage.md` updated (the jump list left with the Windows target).
