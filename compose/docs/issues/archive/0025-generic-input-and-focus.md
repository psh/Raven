---
status: closed
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: Paul Hawke
---

# Generic input and focus

## Question

Beyond the widget-specific callbacks of phase 1, how do Compose's generic input APIs map onto Raven: `Modifier.clickable`, `onKeyEvent` / `onPreviewKeyEvent`, pointer input over `RWidgetEventCallback` (which sees events before the widget and can consume them), focus control (`FocusRequester`, `Modifier.focusable`, `Rwidget_set_focusable`), and context menus (`ContextMenuArea` over `Rmenu_open` on an unparented Menu)? Decide which are modifiers, how events bubble, and how they fit one node per widget.

## Resolution

Facts (`raven_widget.h`, `raven_window.h`): there is **one** event callback per widget (`Rwidget_set_event_callback`). It sees each event before the widget itself, returning true consumes the event, and mouse and key events otherwise bubble to the parents. Nothing tunnels. Events are mouse move, down, up, wheel, enter and leave (button, click count, local x/y, buttons `X1`/`X2` for back and forward), key down and up (key, modifiers, repeat), text, and focus gained and lost. Focus: `Rwidget_set_focusable`, `Rwidget_focus`, `Rwidget_is_focused`. There is no obvious "focus nothing". Also `Rwidget_set_cursor` and `Rmenu_open(menu, window, x, y)` for an unparented menu.

- **Plumbing**: a node registers Raven's single event callback only once its Modifier chain holds an input element. The callback dispatches each event to the node's handlers in chain order, and the first that consumes it returns true to Raven. Handlers are folded from the chain and replaced on recomposition without a C call.
- **Pointer** (Compose and Compose Desktop names):
  - `Modifier.clickable(enabled = true, onClick)` fires on a left press then release over the widget, and consumes both.
  - `Modifier.combinedClickable(onClick, onDoubleClick)` uses Raven's click count. There is no long press, because it needs a timer.
  - `Modifier.onPointerEvent(PointerEventType.Press | Release | Move | Enter | Exit | Scroll) { event -> }` gives a `PointerEvent` with the local `position`, `button` (Primary, Secondary, Tertiary, Back, Forward), the keyboard modifiers and `scrollDelta`. It is not consumed unless the handler calls `event.consume()`.
  - `Modifier.pointerHoverIcon(PointerIcon.Default | Text | Hand | Crosshair | ...)` uses `Rwidget_set_cursor`.
  - Left out (documented, Gestures fog): coroutine `pointerInput { awaitPointerEventScope { } }`, and drag gestures (`draggable`, `detectDragGestures`).
- **Keys**: `Modifier.onKeyEvent { event -> Boolean }`. `KeyEvent` has `key`, `type` (KeyDown or KeyUp), `isCtrlPressed`, `isShiftPressed`, `isAltPressed`, `isMetaPressed` and `isRepeat`, and returning true consumes the event. Keys go to the focused widget and bubble. `Window(onKeyEvent = ...)` is the root Overlay's handler and sees what nothing below consumed. `onPreviewKeyEvent` is left out because Raven doesn't tunnel (documented).
- **Focus**: `Modifier.focusable(enabled = true)` uses `Rwidget_set_focusable`. `FocusRequester` with `Modifier.focusRequester(r)` lets the app call `r.requestFocus()`, which uses `Rwidget_focus`. `Modifier.onFocusChanged { state -> }` gives `FocusState.isFocused`, from Raven's focus events. `LocalFocusManager.current.clearFocus()` focuses the root. **Verify first** that this clears focus in Raven, and fix it in the fork if not. Focus traversal stays with Raven's Tab handling and is not exposed.
- **Context menus**: `ContextMenuArea(items = { listOf(ContextMenuItem("Copy") { copy() }) }) { content }`, as in Compose Desktop. The content goes in an internal Box. On a right-click, its event callback builds an unparented Raven Menu from `items()` and opens it at the pointer with `Rmenu_open`. The menu is discarded after it closes. Items are data, not composables.
- **Back signals**: `PointerButton.Back`/`Forward` and the window-level `onKeyEvent` make them visible. Mapping them to navigation is for [Navigation 3 and scenes](0039-navigation3-and-scenes.md).
