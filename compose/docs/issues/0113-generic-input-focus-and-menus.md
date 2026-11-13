---
status: open
---

# Generic input, focus and menus

Raven's single event callback dispatched to the input modifiers, focus, and the two menus opened from code. Spec: [7: Generic input and focus](../spec.md#generic-input-and-focus-phase-3).

**Depends on:** nothing in phase 3.

## Deliverables

- [ ] The plumbing: one Raven event callback per node with an input element, dispatching in chain order, the first consumer returning true.
- [ ] `Modifier.clickable`, `combinedClickable`, `onPointerEvent(PointerEventType)` with `PointerEvent`, and `pointerHoverIcon(PointerIcon)`.
- [ ] `Modifier.onKeyEvent` and `Window(onKeyEvent)`, with `KeyEvent`.
- [ ] `Modifier.focusable()`, `FocusRequester`/`focusRequester`, `onFocusChanged`, and `LocalFocusManager.current.clearFocus()` over `Rwindow_clear_focus` (fork change 5).
- [ ] `ContextMenuArea`, and `DropdownMenu(state = rememberDropdownMenuState(), modifier, offset: DpOffset)` with phase 2's `MenuScope`, opened under its parent ([Menus opened from a button, and Raven's popups](archive/0106-menus-opened-from-a-button-and-ravens-popups.md)); `DpOffset` arrives with it.
- [ ] Tests: clicks, double clicks, hover enter and exit, keys bubbling to `Window(onKeyEvent)`, focus requests and `clearFocus`, a context menu and a dropdown menu chosen by item text.
