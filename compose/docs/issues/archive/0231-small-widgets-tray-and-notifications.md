---
status: closed
---

# Small widgets: Tray and notifications

Add Compose Desktop's `Tray` and notifications over Raven's tray and system notifications. Spec:
[8: Tray, notifications and taskbar](../../spec.md#tray-notifications-and-taskbar-phase-5) and section
12. Planned in [Small widgets: notifications and tray](0213-small-widgets-notifications-and-tray.md).

**Depends on:** [Small widgets: CodeEditor extras](0230-small-widgets-codeeditor-extras.md), the
ticket before it in phase 5's implementation sequence.

## Deliverables

- [x] `ApplicationScope.Tray(icon, state, tooltip, onAction, menu)` and `rememberTrayState()`: the icon's pixels from a `painterResource` (a warning for another painter), the menu from the menu composables, `onAction` from Raven's clicked callback; a warning and nothing else where `Rtray_is_supported` is false.
- [x] The app keeps running after its last window closes while a `Tray` is composed, and ends with `exitApplication()`.
- [x] `Notification(title, message, type)`, `TrayState.sendNotification` and `ApplicationScope.sendNotification`, over `Rnotification_show`, returning whether the system took it.
- [x] A seam recording the tray and notification calls; tests of the icon, tooltip and menu passed, a menu item chosen, a notification refused headless (returning false), and the app staying alive with a tray and no window.

## Resolution

Built on 2026-11-27.

- **`ApplicationScope.Tray(icon, state, tooltip, onAction, menu)`** and `rememberTrayState()`, in
  foundation's `Tray.kt`:
  - The icon's pixels come from `Painter.readPixels()`, now internal API in compose-raven-ui
    alongside `Pixels`. Another painter warns and shows no icon.
  - `onAction` is Raven's clicked callback for the left button, through a `StableRef`, with
    failures going to the host's error path. Icon and tooltip are given to Raven only when they
    change.
  - Where `Rtray_is_supported` is false, or `Rtray_create` refuses, the tray warns and does
    nothing.
- **The menu**: `rememberDetachedMenu`, new internal API in compose-raven-ui
  (`DetachedMenu.kt`). It is a Raven Menu with no parent, in a composition of its own with the
  locals of the place it is remembered, disposed with its widget. The menu composables go in
  through `MenuContent`.
- **Keeping the app alive**: nothing was added; it is Raven's rule. The null window system takes a
  tray, so the test sees it for real: with the window gone `Ris_running` stays true, and once the
  tray leaves too, Raven quits.
- **Notifications**: `Notification(title, message, type)`, `TrayState.sendNotification` and
  `ApplicationScope.sendNotification`, over `Rnotification_show`, each returning whether the
  system took it. Types map None to low, Info and Warning to normal, and Error to critical.
- **The seam**: `ShellCalls` / `shellCalls` in foundation. The tests' recording subclass still
  calls Raven.
- **Tests**: 6 in `TrayTest`, with a 16×16 PNG fixture. They cover:
  - icon, tooltip and menu items passed, and only changes passed again;
  - a menu item chosen, by opening the same Raven menu in a test window;
  - the app staying alive with a tray and no window, then ending without either;
  - a painter that isn't an image file warning;
  - no system tray: nothing done, with a warning;
  - both notification calls refused headless, with their urgencies.
- **`api-coverage.md`**: 544 of 1,704 functions; `raven_shell.h` 30 of 60, `raven_dialogs.h` 27
  of 47. The spec's section records the left click, the detached menu, the urgency mapping and the
  seam.
