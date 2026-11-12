---
status: closed
wayfinder: grilling
parent: 0073
blocked_by: []
assignee: Paul Hawke
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
[Window extras the samples use](0076-window-extras-the-samples-use.md).

## Resolution

Phase 2, as a `Window` parameter, since compose-raven has no AWT window whose `minimumSize` an app
could set as in Compose Desktop. New ticket:
[Window minimum size and DpSize](../0085-window-minimum-size-and-dpsize.md).

- **`Window(minimumSize: DpSize? = null)`**, on `singleWindowApplication` too, through
  `Rwindow_set_min_size`; `null` keeps Raven's default, and a change calls it again.
- **`DpSize`** comes into `raven.compose.ui.unit` in phase 2, so the parameter doesn't change in
  phase 3.
- **Phase 3's `WindowState.size` is a `DpSize`**, as Compose Desktop's, now stated in spec
  section 4: it replaces phase 1's `width` and `height`, and shares the minimum's type.

`samples/notepad` and `samples/components` depend on the new ticket.
