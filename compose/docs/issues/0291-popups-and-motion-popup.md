---
status: open
wayfinder: grilling
parent: 0287
blocked_by: []
assignee: null
---

# Popups and motion: Popup

## Question

How Compose's `Popup(alignment, offset, onDismissRequest, properties) { }` maps onto Raven's `Rpopup_open(window, content, x, y, closed, user_data)`: the position from the parent's widget rectangle plus `alignment` and `offset` (or a `PopupPositionProvider`), light dismissal by a click outside or Escape as `onDismissRequest`, focus, and what follows when the parent moves or the content resizes. Whether `DropdownMenu` of composables follows from it ([Menus opened from a button, and Raven's popups](archive/0106-menus-opened-from-a-button-and-ravens-popups.md)). From [compose-raven phase 5 spec](0204-compose-raven-phase-5-spec.md#not-yet-specified).
