---
status: open
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: null
---

# Generic input and focus

## Question

Beyond the widget-specific callbacks of phase 1, how do Compose's generic input APIs map onto Raven: `Modifier.clickable`, `onKeyEvent` / `onPreviewKeyEvent`, pointer input over `RWidgetEventCallback` (which sees events before the widget and can consume them), focus control (`FocusRequester`, `Modifier.focusable`, `Rwidget_set_focusable`), and context menus (`ContextMenuArea` over `Rmenu_open` on an unparented Menu)? Decide which are modifiers, how events bubble, and how they fit one node per widget.
