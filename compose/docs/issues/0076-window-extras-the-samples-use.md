---
status: open
wayfinder: grilling
parent: 0073
blocked_by: []
assignee: null
---

# Window extras the samples use

## Question

The samples use window features the spec doesn't cover: `overlay`'s opacity slider
(`Rwindow_set_opacity`/`_get_opacity`) and its display of the backdrop actually obtained
(`Rwindow_get_backdrop`); `notepad`'s files dropped on the window (`Rwindow_set_drop_callback`)
and its file given on the command line. For each: its compose-raven shape (a `Window` parameter, a
modifier, something on `WindowState`, a return value from `WindowBackdrop`), and is it phase 2 or
a named later phase? `WindowState` itself is phase 3, so settle how any of these sit beside it.
