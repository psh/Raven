---
status: open
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: null
---

# Small widgets: notifications and tray

## Question

How does an app post a system notification, show a tray icon with its menu, and set a taskbar
badge or progress? Compose Desktop has `Tray` and `Notification` in its application scope. Decide
compose-raven's shapes over Raven's (`raven_shell.h`, `raven_dialogs.h`), which a headless test can
check, and what stays out. The answer is the spec section and its implementation tickets.
