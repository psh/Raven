---
status: closed
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: Paul Hawke
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

## Resolution

Decided 2026-11-12. Written into spec section 7
([Generic input and focus](../../spec.md#generic-input-and-focus-phase-3)) and section 12.

- **`DropdownMenu(state = rememberDropdownMenuState(), modifier, offset) { MenuScope items }`**,
  opened with `state.open()`: a Raven Menu (`Rmenu_open`) under its parent's widget, at the
  parent's bottom-left (`Rwidget_get_rect`) plus `offset`, as Compose's anchors to its parent.
  Raven dismisses it, as with `ContextMenuArea`. Raven's Menu has no close callback and no "is
  open" query, so a controlled `expanded` (Compose's Material `DropdownMenu`) would need a fork
  change; a dropdown on `Rpopup_open` would lose Raven's menu look, keys, checks and shortcuts.
- **Items**: phase 2's `MenuScope` (`Item`, `CheckboxItem`, `Separator`, sub-`Menu`), as in the
  `MenuBar`'s menus, so `glass`'s checkable "Lossless audio" and Ctrl+Q need nothing new. Tests
  need nothing new: the harness clicks the button and finds and activates menu items by text.
- **`Popup { }`** stays phase 5 fog, keeping it slim: no phase 3 sample needs a popup of
  composables (`ColorPicker`'s is Raven's own). Its fog note is rewritten by
  [Upstream changes and the fog notes](../0109-upstream-changes-and-the-fog-notes.md): Raven now has
  `Rpopup_open` (light-dismiss, a `closed` callback, content kept by the app), which maps to
  `Popup(alignment, offset, onDismissRequest)`.
