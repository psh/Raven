---
status: closed
---

# Generic input, focus and menus

Raven's single event callback dispatched to the input modifiers, focus, and the two menus opened from code. Spec: [7: Generic input and focus](../../spec.md#generic-input-and-focus-phase-3).

**Depends on:** nothing in phase 3.

## Deliverables

- [x] The plumbing: one Raven event callback per node with an input element, dispatching in chain order, the first consumer returning true.
- [x] `Modifier.clickable`, `combinedClickable`, `onPointerEvent(PointerEventType)` with `PointerEvent`, and `pointerHoverIcon(PointerIcon)`.
- [x] `Modifier.onKeyEvent` and `Window(onKeyEvent)`, with `KeyEvent`.
- [x] `Modifier.focusable()`, `FocusRequester`/`focusRequester`, `onFocusChanged`, and `LocalFocusManager.current.clearFocus()` over `Rwindow_clear_focus` (fork change 5).
- [x] `ContextMenuArea`, and `DropdownMenu(state = rememberDropdownMenuState(), modifier, offset: DpOffset)` with phase 2's `MenuScope`, opened under its parent ([Menus opened from a button, and Raven's popups](0106-menus-opened-from-a-button-and-ravens-popups.md)); `DpOffset` arrives with it.
- [x] Tests: clicks, double clicks, hover enter and exit, keys bubbling to `Window(onKeyEvent)`, focus requests and `clearFocus`, a context menu and a dropdown menu chosen by item text.

## Resolution

Done on macOS. 346 tests pass across the build, 24 of them new: `InputTest` (17) and
`PopupMenusTest` (6) in compose-raven-foundation, and one in `RavenApplierTest`. No Raven change.

- **The plumbing** (`Input.kt`, `RavenNode.kt`): an `InputElement` in a Modifier chain makes the
  node register Raven's event callback, and drop it when the chain has none. The node copies each
  `REvent` into an `InputEvent` and hands it to its input elements in chain order until one returns
  true, which is returned to Raven. What a handler throws goes to the host's error path, not into C.
  A node that handles input, or is focusable, is made hit-testable, since panels and labels let the
  pointer through. The node tracks a primary press over itself, so a release is a click only when a
  press over the same widget began it and the pointer hasn't left.
- **Pointer**: `Modifier.clickable(enabled, onClick)` and `combinedClickable(enabled, onDoubleClick,
  onClick)` (`Clickable.kt`) consume the press and the release; a disabled one lets them through.
  `onPointerEvent(PointerEventType)` and `pointerHoverIcon(PointerIcon)` are in
  `raven.compose.ui.input.pointer`; `PointerEvent` has `type`, `position`, `button`,
  `keyboardModifiers`, `scrollDelta` and `consume()`.
- **Keys**: `Modifier.onKeyEvent` and `KeyEvent` (`input/key/KeyEvent.kt`, with `Key` gaining the
  keys it needs). `Window(onKeyEvent)` (`WindowKeys.kt`) takes what the focused widget and its
  ancestors left, through the window root's event callback, and, while nothing has the focus,
  every key through the window's event hook, which otherwise lets keys go to the focused widget or
  an open popup first.
- **Focus** (`focus/Focus.kt`): `Modifier.focusable(enabled)`, `FocusRequester` with
  `Modifier.focusRequester`, `Modifier.onFocusChanged`, and `LocalFocusManager.current.clearFocus()`
  over `Rwindow_clear_focus`, provided by each window.
- **Menus**: `ContextMenuArea(items, enabled) { }` with `ContextMenuItem(label, onClick)`
  (`ContextMenu.kt`) builds an unparented Raven Menu on each right-click and opens it at the
  pointer. `DropdownMenu(state = rememberDropdownMenuState(), modifier, offset)` (`Menus.kt`) holds
  phase 2's `MenuScope` items and opens under its parent with `state.open()`. Its Menu is a new
  kind of node, a **popup**: a child of its parent in Compose only, which the Applier keeps out of
  the parent's Raven children, counting Raven's indexes without it. `DpOffset` and `Offset` arrive
  with it.
- **Test harness** (`RavenTest.kt`): `pressKey`, `openMenus`, `clickOpenMenuItem`, and on a node
  `performDoubleClick`, `performRightClick`, `performMouseEnter`, `performMouseExit`,
  `assertIsFocused` and `assertIsNotFocused`.
- **Deviations from Compose**, none from the spec:
  - Raven reports each click as it comes, so the first click of a double click has already called
    `onClick`; there is no long click.
  - Raven has no getters for a widget's own focusability, cursor and hit testing, so dropping
    `focusable()` or `pointerHoverIcon` from a chain leaves the widget unfocusable, or with its
    parent's cursor, even if it had its own (a button's focus, a text field's beam).
  - `onFocusChanged` hears only the widget's own focus, not a child's, and nothing for the state
    it starts in.
  - `scrollDelta` is positive down and right, as in Compose; Raven's wheel is positive up and left.
- **Tests**: clicks, a disabled clickable, a click consumed before its parent, a release without a
  press, double clicks, hover enter and exit, a pointer event's button, position and scroll, events
  going on unless consumed, keys bubbling from the focused widget to `Window(onKeyEvent)`, every key
  reaching the window with nothing focused, focus requests and `clearFocus` reported by
  `onFocusChanged`, a click focusing a focusable widget, a requester in no chain or dropped from
  one, the hover icons, a throwing handler ending the host with its error, handlers replaced on
  recomposition; a context menu opened by a right-click with the items of the moment, none on a
  left click or while disabled; a dropdown menu opening under its parent, taking no room, and its
  shortcut working before it ever opens; the Applier keeping a popup out of Raven's children.
