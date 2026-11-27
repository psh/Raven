---
status: open
---

# Small widgets: Tray and notifications

Add Compose Desktop's `Tray` and notifications over Raven's tray and system notifications. Spec:
[8: Tray, notifications and taskbar](../spec.md#tray-notifications-and-taskbar-phase-5) and section
12. Planned in [Small widgets: notifications and tray](archive/0213-small-widgets-notifications-and-tray.md).

**Depends on:** [Small widgets: CodeEditor extras](archive/0230-small-widgets-codeeditor-extras.md), the
ticket before it in phase 5's implementation sequence.

## Deliverables

- [ ] `ApplicationScope.Tray(icon, state, tooltip, onAction, menu)` and `rememberTrayState()`: the icon's pixels from a `painterResource` (a warning for another painter), the menu from the menu composables, `onAction` from Raven's clicked callback; a warning and nothing else where `Rtray_is_supported` is false.
- [ ] The app keeps running after its last window closes while a `Tray` is composed, and ends with `exitApplication()`.
- [ ] `Notification(title, message, type)`, `TrayState.sendNotification` and `ApplicationScope.sendNotification`, over `Rnotification_show`, returning whether the system took it.
- [ ] A seam recording the tray and notification calls; tests of the icon, tooltip and menu passed, a menu item chosen, a notification refused headless (returning false), and the app staying alive with a tray and no window.
