---
status: closed
---

# Window operations

A `Window` can't be focused, centred, hidden, flashed for attention or put in picture-in-picture,
and a custom header has no drag area. Bind `Rwindow_focus`, `Rwindow_center`, `Rwindow_hide`,
`Rwindow_request_attention`, `Rwindow_set_picture_in_picture` and `Rwidget_set_window_drag`.
One of the everyday features in the [Raven API coverage](../../api-coverage.md) review (2026-11-14).

## Deliverables

- [x] `WindowState` (or the window's scope) gains the operations (decided: `LocalWindowController` for the actions, a `WindowPlacement` for picture in picture); `WindowPosition.Aligned(Center)` centres through `Rwindow_center`.
- [x] `visible` on `Window` hides and shows it.
- [x] `WindowDraggableArea { }` sets `Rwidget_set_window_drag` on its content.
- [x] Tests where the backend allows; a note in the spec for those it can't test.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 594 tests, 9 of them new in ui's `WindowTest` and
2 in foundation's new `WindowDraggableAreaTest`.

- **Where the operations went** (decided on 2026-11-25): the one-shot actions on a
  `LocalWindowController`, beside `LocalWindowInfo`, as the binding already reaches window features
  through locals (`WindowBackdrop`, `LocalWindowInfo`); picture in picture as a placement, so the
  state follows it as it follows maximising. Compose Desktop's `FrameWindowScope` receiver was the
  other choice, and would have changed `Window`'s content type.
- **`Window(visible = true)`**, on `singleWindowApplication` and compose-raven-mcp's too. A window
  composed hidden is never shown until it is visible; hidden, it keeps its composition and its
  lifecycle is CREATED. Showing and hiding go through `WindowCalls`, which the null window system
  doesn't show, so the tests record them.
- **`WindowPosition.Aligned(alignment)`** and `WindowPosition(alignment)`: `Rwindow_center` when the
  window opens or the alignment is asked for again, then the real position replaces it, as for
  `PlatformDefault`. Raven has no monitor geometry, so another alignment centres with a warning.
- **`WindowPlacement.PictureInPicture`**, a compose-raven extension. Leaving it calls
  `Rwindow_set_picture_in_picture(false)` before the new placement, so Raven gives back the size and
  place; `readPlacement` reads it first, so the state follows the user leaving it. A refusal warns
  and puts the placement back.
- **`LocalWindowController`**: `toFront()` is `Rwindow_focus`, but not for a hidden window, which
  Raven would show; `requestAttention(critical)` returns what Raven says.
- **`WindowDraggableArea(modifier) { }`**, in `raven.compose.foundation.window`: a `Box` with a
  `WidgetAttachedElement` (from [Bring into view](0196-bring-into-view.md)) that calls
  `Rwidget_set_window_drag`, through a `WindowDragCalls` seam the tests record. Without
  `customHeader` Raven ignores the drag, so it warns once for each area and does nothing.
- **Broke and fixed**: compose-raven-mcp's `singleWindowApplication` passed its arguments by
  position, so the new `visible` shifted them; it takes `visible` too and passes them by name now.
- **Roadmap**: "bringing a window to the front" and "dragging a window by its content" leave phase
  5. `samples/overlay` stays as it is: its C window isn't `customHeader`, so its `WindowDrag`
  doesn't drag there either ([samples/overlay](0072-samples-overlay.md)).
- **Spec**: section 4's window operations and four rows in section 12.
