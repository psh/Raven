---
status: closed
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: claude
---

# Small widgets: notifications and tray

## Question

How does an app post a system notification, show a tray icon with its menu, and set a taskbar
badge or progress? Compose Desktop has `Tray` and `Notification` in its application scope. Decide
compose-raven's shapes over Raven's (`raven_shell.h`, `raven_dialogs.h`), which a headless test can
check, and what stays out. The answer is the spec section and its implementation tickets.

## Resolution

Resolved on 2026-11-26 in conversation. Written into spec section 8 (Tray, notifications and
taskbar) and section 12, and filed as [Small widgets: Tray and notifications](../0231-small-widgets-tray-and-notifications.md)
and [Small widgets: taskbar progress, badge and Dock menu](../0232-small-widgets-taskbar-progress-badge-and-dock-menu.md),
in that order.

- **Tray**: Compose Desktop's `ApplicationScope.Tray(icon, state, tooltip, onAction, menu)`, the
  menu from the binding's menu composables and the icon's pixels from a `painterResource`. Raven
  keeps the app running while a tray exists, so a tray outlives the last window with nothing added.
- **Notifications**: `TrayState.sendNotification(Notification(...))` as Compose's, and
  `ApplicationScope.sendNotification` for an app with no tray, since Raven's need none; both return
  whether the system took it. The clicked callback (Windows only) is left out.
- **Taskbar**: progress is window state, so a `Window(taskbarProgress)` parameter; the badge a
  `TaskbarBadge(text)` composable, app-wide and last-composed-wins; the Dock menu an
  `ApplicationScope.DockMenu { }`. The jump list waits for the Windows target.
- **Tests** record Raven's calls through a seam, as the window operations' do.
