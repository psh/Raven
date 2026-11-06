---
status: open
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: null
---

# Multiple windows, dialogs and popups

## Question

Does a composition own one window or many, and how does `ravenApplication` grow into Compose Desktop's `application { Window { } }`? Decide how windows open and close as composables, how dialogs (`Rdialog_*` / file dialogs in `raven_dialogs.h`) and transient popups map onto composables, how per-window settings (background, wallpaper, clear colour via `Rwindow_set_background` / `Rwindow_set_clear_color`) are written, and how [Frame loop and threading](0008-frame-loop-and-threading.md)'s single root and shutdown generalise.
