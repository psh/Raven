---
status: open
---

# Window operations

A `Window` can't be focused, centred, hidden, flashed for attention or put in picture-in-picture,
and a custom header has no drag area. Bind `Rwindow_focus`, `Rwindow_center`, `Rwindow_hide`,
`Rwindow_request_attention`, `Rwindow_set_picture_in_picture` and `Rwidget_set_window_drag`.
One of the everyday features in the [Raven API coverage review](https://claude.ai/artifact/J56iXnntbqm8dmAUXWAEeh) (2026-11-14).

## Deliverables

- [ ] `WindowState` (or the window's scope) gains the operations; `WindowPosition.Aligned(Center)` centres through `Rwindow_center`.
- [ ] `visible` on `Window` hides and shows it.
- [ ] `WindowDraggableArea { }` sets `Rwidget_set_window_drag` on its content.
- [ ] Tests where the backend allows; a note in the spec for those it can't test.
