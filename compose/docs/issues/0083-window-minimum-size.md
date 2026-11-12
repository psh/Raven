---
status: open
wayfinder: grilling
parent: 0073
blocked_by: []
assignee: null
---

# Window minimum size

## Question

`notepad`'s window has a minimum size (`MinWidth="560" MinHeight="320"`, `Rwindow_set_min_size`),
and so does `components`' (`MinWidth="520" MinHeight="400"`).
Its ticket lists that as a port gap completed by phase 3's `WindowState`, but the spec's
`WindowState` holds only `size`, `position`, `placement` and `isMinimized`, so no phase completes it.
What is its compose-raven shape (a `Window` parameter like Compose Desktop's
`window.minimumSize`, a `WindowState` field), and is it phase 2, beside `Window`'s phase 1
`width`/`height`, or phase 3 with `WindowState`? Found while resolving
[Window extras the samples use](archive/0076-window-extras-the-samples-use.md).
