---
status: closed
---

# Files and text dropped on a window

`Window(onExternalDrop)` for files and text dropped from other applications, for the `notepad`
port, and the harness action that tests it. Spec: [4. Application host](../spec.md#4-application-host).
Decided in: [Window extras the samples use](archive/0076-window-extras-the-samples-use.md).

**Depends on:** [Window: WindowState and its parameters](archive/0086-window-windowstate-and-its-parameters.md) (the `Window` it adds a parameter to).

## Deliverables

- [x] `Window(onExternalDrop: ((DragData) -> Unit)? = null)`, on `singleWindowApplication` too,
      over `Rwindow_set_drop_callback`, with `DragData.FilesList(paths)` and `DragData.Text(text)`.
- [x] A non-null handler accepts drops (Raven shows its "can drop here" cursor), a null one refuses
      them; the handler runs only on `R_DROP_DROPPED`, through the C-boundary catch, with the
      paths and text copied before the callback returns.
- [x] Changing the handler between null and non-null takes effect at once.
- [x] Harness: `performExternalDrop(paths, text)` over `Rwindow_inject_drop`, returning whether the
      window took it.
- [x] Tests: a dropped file list and a dropped text reach the handler; a window without a handler
      refuses a drop.

## Resolution

Done on macOS. 231 tests pass across the build, 8 of them new: 5 in compose-raven-ui (`WindowTest`)
and 3 in foundation (`ExternalDropTest`).

- **`DragData`** (`DragData.kt`, in `raven.compose.ui`, where Compose Desktop's `onExternalDrag`
  had it) is a sealed interface with `FilesList(paths: List<String>)` and `Text(text: String)`,
  data classes as the spec writes them, rather than Compose Desktop's `readFiles()`/`readText()`.
- **`Window(..., opacity, onExternalDrop: ((DragData) -> Unit)? = null, content)`**, and
  `singleWindowApplication` passing it on. The handler is part of `WindowSettings`, so the
  `SideEffect` that applies the parameters keeps it current. Raven only takes drops on a window
  that has a callback, so a non-null handler sets `Rwindow_set_drop_callback` (with the window's
  existing `StableRef` as user data, disposed with the window, so no new reference to leak) and a
  null one clears it. Raven then neither highlights nor accepts, and the switch takes effect in
  the `SideEffect` of the composition that changed it.
- **The callback** accepts every phase while a handler is set, so Raven shows its "can drop here"
  cursor and highlight, and calls the handler only on `R_DROP_DROPPED`. It copies the paths and the
  text into Kotlin strings before calling it: files make a `FilesList`, else text a `Text`, and a
  drop carrying neither is refused. Everything runs inside the C-boundary catch: a throwable goes
  to the host's error path, and the drop counts as refused.
- **Harness**: `performExternalDrop(paths = emptyList(), text = null): Boolean` waits for idle,
  then calls `Rwindow_inject_drop` at the centre of the one window's root (it fails with more than
  one window), and returns whether the window took the drop.
- **Tests** (`WindowTest` in compose-raven-ui, `ExternalDropTest` in foundation):
  - a dropped file list reaches the handler, and dragging over first accepts without calling it;
  - a dropped text reaches the handler;
  - a window without a handler refuses both the drag and the drop;
  - changing the handler from null to one, to another, and back to null takes effect at once;
  - an exception in `onExternalDrop` reaches the host's error path;
  - through the harness, a dropped file list and a dropped text reach a handler that shows them,
    and the harness's own window (no handler) refuses a drop.
