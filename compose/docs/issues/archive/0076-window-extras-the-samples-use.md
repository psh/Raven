---
status: closed
wayfinder: grilling
parent: 0073
blocked_by: []
assignee: Paul Hawke
---

# Window extras the samples use

## Question

The samples use window features the spec doesn't cover: `overlay`'s opacity slider
(`Rwindow_set_opacity`/`_get_opacity`) and its display of the backdrop actually obtained
(`Rwindow_get_backdrop`); `notepad`'s files dropped on the window (`Rwindow_set_drop_callback`)
and its file given on the command line. For each: its compose-raven shape (a `Window` parameter, a
modifier, something on `WindowState`, a return value from `WindowBackdrop`), and is it phase 2 or
a named later phase? `WindowState` itself is phase 3, so settle how any of these sit beside it.

## Resolution

All of them come into phase 2, so `overlay` has no gaps and `notepad` none from its window.

- **Opacity**: `Window(opacity: Float = 1f)` through `Rwindow_set_opacity`; a refusal logs a
  warning and leaves the window opaque, the app's value staying the source of truth.
- **The backdrop obtained**: `WindowBackdrop(backdrop): Backdrop` returns the one in effect, read
  with `Rwindow_get_backdrop` after each apply as snapshot state (`None` until the first). A
  return value, since `WindowBackdrop` emits no node.
- **Is the window really see-through**: `LocalWindowInfo.current.isTransparent`, from the native
  handle's `transparent`, after Compose Desktop's `LocalWindowInfo`. `overlay` uses it to choose
  between its two explanations. The raw native handle stays with phase 3's `NativeViewport`.
- **Drops**: `Window(onExternalDrop: ((DragData) -> Unit)?)` with `DragData.FilesList`/`Text`, the
  names of Compose Desktop's `onExternalDrag`; non-null accepts, null refuses, called only on the
  drop. The harness gains `performExternalDrop` over `Rwindow_inject_drop`. Drop targets on widgets
  and drags inside the app are phase 4, named in the roadmap's gestures fog.
- **`CodeEditorState.insertText(text)`**, for dropped text: typed at the cursor, undoable, calling
  `onValueChange`. Added to [CodeEditorState, completions, markers and languages](../0081-codeeditorstate-completions-markers-and-languages.md).
- **The command line** is no gap: the port's `main(args)` passes the path to its composable.

Opacity, the return value and `isTransparent` joined the renamed
[Window transparency, opacity and backdrop](0068-window-transparency-opacity-and-backdrop.md);
drops are a new ticket, [Files and text dropped on a window](../0082-files-and-text-dropped-on-a-window.md).
Spec section 4 has them, section 12 the two differences.

Surfaced: `notepad`'s minimum window size has no phase (phase 3's `WindowState` has no minimum), so
it became [Window minimum size](0083-window-minimum-size.md).
