---
status: closed
---

# Menus and KeyShortcut

`MenuBar`, `Menu`, `Item`, `CheckboxItem` and the menu `Separator`, with `KeyShortcut` and `Key`,
for the `notepad` port. Spec: [8: menus and the header bar](../spec.md#phase-2-menus-and-the-header-bar).

**Depends on:** nothing open.

## Deliverables

- [x] `MenuBar { }` over MenuBar, whose `MenuBarScope` allows only `Menu`.
- [x] `Menu(text) { }`, whose `MenuScope` allows `Item`, `CheckboxItem`, `Separator` and
      sub-`Menu`s. Items added, removed and moved by recomposition land at the right index.
- [x] `Item(text, onClick, shortcut, enabled)` over MenuItem, with no children.
- [x] `CheckboxItem(text, checked, onCheckedChange, shortcut, enabled)` over a checkable MenuItem,
      controlled through `ControlledValue` as `Checkbox` is.
- [x] `Separator()` in `MenuScope`: a MenuItem with `Separator = true` set through
      `Rwidget_set_attribute`, as the [spikes](../../spikes/README.md) found.
- [x] `KeyShortcut(key, ctrl, shift, alt, meta)` through `Rmenu_item_set_shortcut`, `meta` being
      Super (Cmd on macOS), and `Key` mirroring `RKey`: letters, digits, F1 to F12 and the named keys.
- [x] Harness: finding a menu item by its text and activating it, the way `performClick` does for
      a button.
- [x] Tests: an item's `onClick` runs; an ignored `CheckboxItem` click reverts; a separator and an
      item inserted in the middle of a menu land in place; a disabled item does nothing.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 164 tests, 10 of them new in foundation's
`MenusTest`.

- **Composables**, in `raven.compose.foundation` (`Menus.kt`): `MenuBar(modifier) { }`, with
  `MenuBarScope.Menu(text) { }`; `MenuScope.Menu`, `MenuScope.Item(text, onClick, shortcut,
  enabled)`, `MenuScope.CheckboxItem(text, checked, onCheckedChange, shortcut, enabled)` and
  `MenuScope.Separator()`. The scoped composables are extensions on empty scope interfaces, so
  `Item` can only be called in a menu and a bar holds only `Menu`s. MenuBar and Menu take many
  children; the items take none, and take no Modifier, as the spec's signatures have none. Each
  is created with `Rwidget_create` and inserted at its index by the Applier, unchanged: inserts,
  removals and keyed moves land in place.
- **Binding layer**: `MenuItemClicked` and `MenuItemCheckedChanged` share
  `Rmenu_item_set_clicked_callback`. Raven has toggled a checkable item before it calls back, so
  `MenuItemCheckedChanged` marks `MenuItemChecked` and passes `Rmenu_item_is_checked`; the
  end-of-frame check reverts a change the app ignores, as for `Checkbox`. `MenuItemShortcut`
  calls `Rmenu_item_set_shortcut`, with `R_KEY_UNKNOWN` for null.
- **Separator** is a MenuItem with `Rwidget_set_attribute(item, "Separator", "true")`, as the
  spikes found. It also gets `R_ROLE_SEPARATOR`, which Raven otherwise reports as a menu item.
- **`Key` and `KeyShortcut(key, ctrl, shift, alt, meta)`** are in `raven.compose.ui.input.key`,
  where phase 3's `KeyEvent.key` can use them. `Key` is an enum over `RKey`: A to Z, digits,
  F1 to F12 and the named keys of Raven's shortcut syntax. The names are Compose's (`Key.Zero`,
  `Key.Spacebar`, `Key.DirectionUp`, `Key.MoveHome`), so code from Compose Desktop reads the same.
  `meta` is `R_MOD_SUPER`; nothing maps Ctrl to Cmd.
- **The menu bar stays in the window**: Raven 0.2.3 gives the first MenuBar of a macOS window to
  the system menu bar by default, so `MenuBar` calls `Rmenu_bar_set_native(bar, R_FALSE)`, as
  spec section 12 ("drawn in the window") says. Offering the system bar would be a later choice.
- **Harness**: `onNodeWithText` also matches a menu item by its text (separators left out), and
  `performClick()` on one opens its menus and clicks it: the bar's menu with `Rmenu_open` below
  the bar, each sub-menu by moving the pointer onto its entry, then a press and release on the
  item, so Raven decides what a click on a disabled item does. Raven doesn't report where an
  entry is, so the harness follows `src/widgets/menu.c`'s layout (padding 4, rows of the row
  height plus 2, separators 7). `assertIsChecked()` reads `Rmenu_item_is_checked` on a menu item.
- **Tests**:
  - an item's `onClick` runs, also in a sub-menu after a separator;
  - a disabled item does nothing, and runs once the app enables it;
  - an ignored `CheckboxItem` click reverts, and an accepted one stays, both ways;
  - a separator and an item inserted in the middle of a menu land in place, Raven measures the
    separator as one (7 high, not a row), both can be clicked past, and removing them restores
    the order;
  - keyed items moved and removed by recomposition land in place;
  - Ctrl+S and Ctrl+Shift+S run their own items;
  - the menu bar isn't given to the system.
