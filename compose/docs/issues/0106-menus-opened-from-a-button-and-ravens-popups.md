---
status: open
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: null
---

# Menus opened from a button, and Raven's popups

## Question

`glass`'s More button opens a menu under itself on a left click (`Rmenu_open` at the button's
rectangle), with a checkable item and a shortcut. Phase 3 has only `ContextMenuArea`, which opens
at the pointer on a right click, and no way to read a widget's position. Decide an anchored menu
(Compose's `DropdownMenu(expanded, onDismissRequest)` is Material's), and how it finds its anchor.
Upstream has since added `Rpopup_open`/`Rpopup_close`/`Rpopup_is_open`, an app's light-dismiss
popup, which removes the reason Compose's `Popup { }` is phase 5 fog ("Raven only has menu and
tooltip popups"): decide whether `Popup` moves into phase 3 or stays phase 5 fog.
