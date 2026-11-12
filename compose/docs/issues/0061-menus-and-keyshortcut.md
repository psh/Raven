---
status: open
---

# Menus and KeyShortcut

`MenuBar`, `Menu`, `Item`, `CheckboxItem` and the menu `Separator`, with `KeyShortcut` and `Key`,
for the `notepad` port. Spec: [8: menus and the header bar](../spec.md#phase-2-menus-and-the-header-bar).

**Depends on:** nothing open.

## Deliverables

- [ ] `MenuBar { }` over MenuBar, whose `MenuBarScope` allows only `Menu`.
- [ ] `Menu(text) { }`, whose `MenuScope` allows `Item`, `CheckboxItem`, `Separator` and
      sub-`Menu`s. Items added, removed and moved by recomposition land at the right index.
- [ ] `Item(text, onClick, shortcut, enabled)` over MenuItem, with no children.
- [ ] `CheckboxItem(text, checked, onCheckedChange, shortcut, enabled)` over a checkable MenuItem,
      controlled through `ControlledValue` as `Checkbox` is.
- [ ] `Separator()` in `MenuScope`: a MenuItem with `Separator = true` set through
      `Rwidget_set_attribute`, as the [spikes](../../spikes/README.md) found.
- [ ] `KeyShortcut(key, ctrl, shift, alt, meta)` through `Rmenu_item_set_shortcut`, `meta` being
      Super (Cmd on macOS), and `Key` mirroring `RKey`: letters, digits, F1 to F12 and the named keys.
- [ ] Harness: finding a menu item by its text and activating it, the way `performClick` does for
      a button.
- [ ] Tests: an item's `onClick` runs; an ignored `CheckboxItem` click reverts; a separator and an
      item inserted in the middle of a menu land in place; a disabled item does nothing.
