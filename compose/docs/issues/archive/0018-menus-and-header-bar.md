---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0014]
assignee: Paul Hawke
---

# Menus and the header bar

## Question

Which composables cover Raven's MenuBar, Menu, MenuItem (with Shortcut, Checkable, Checked) and HeaderBar, as the `notepad` port needs? Compose Desktop's `MenuBar { Menu { Item() } }` is the convention to follow. Decide how menus attach to the window from `ravenApplication`, how shortcuts and actions map onto lambdas, and how HeaderBar and the window's `CustomHeader` fit the single-window entry point.

## Resolution

Facts: Raven's MenuBar holds Menus, and a Menu holds MenuItems, separators and sub-menus, as ordinary widget children (`src/widgets/menu.c`), so the [Node model and Applier](0007-node-model-and-applier.md) works for them unchanged. `Rmenu_item_set_shortcut(item, RKey, modifiers)` shows the shortcut and fires the item from the window. Raven maps no Ctrl to Cmd. The MenuBar is drawn in the window, not in the macOS system menu bar. A HeaderBar needs a window created with `R_WINDOW_CUSTOM_HEADER`.

- **Menus** use Compose Desktop's names and scopes:
  ```kotlin
  MenuBar {
      Menu("File") {
          Item("Save", shortcut = KeyShortcut(Key.S, ctrl = true), onClick = { save() })
          Separator()
          CheckboxItem("Line numbers", checked = lines, onCheckedChange = { lines = it })
          Menu("Recent") { ... }
      }
  }
  ```
  `MenuBarScope` allows only `Menu`. `MenuScope` allows `Item`, `CheckboxItem`, `Separator` and `Menu`. Unlike Compose Desktop, `MenuBar` is an ordinary layout composable placed in the content (`notepad` puts it inside the HeaderBar), not a window-level system menu bar. This is documented. Actions are lambdas only, and Raven's action strings and built-in actions are not exposed.
- **Widgets**:
  - `MenuBar` uses `R_WIDGET_MENU_BAR` (many children).
  - `Menu(text)` uses `R_WIDGET_MENU` with `Rmenu_set_title` (many children).
  - `Item(text, onClick, shortcut, enabled)` and `CheckboxItem(text, checked, onCheckedChange, shortcut, enabled)` use `R_WIDGET_MENU_ITEM` (no children) with `Rmenu_item_set_text`/`set_checkable`/`set_checked`/`set_shortcut`/`set_clicked_callback`, and `Rwidget_set_enabled`.
  - `Separator()` in a menu is a plain `R_WIDGET_SEPARATOR` child. The implementation first verifies that Raven treats an inserted separator as a menu separator, as `Rmenu_add_separator` does.
- **Shortcuts**: `KeyShortcut(key: Key, ctrl, shift, alt, meta)`, where `meta` is Raven's Super (Cmd on macOS). `Key` is a small Kotlin enum mirroring `RKey`: letters, digits, F1 to F12, and the named keys of `docs/XML.md`. There is no automatic Ctrl-to-Cmd mapping, so apps write `meta = true` for macOS, as in Compose Desktop. This is documented.
- **`CheckboxItem`** is a controlled input, using the frame-callback check from [Events and state](0010-events-and-state.md).
- **Header bar**:
  - `ravenApplication(..., customHeader: Boolean = false)` creates the window with `R_WINDOW_CUSTOM_HEADER`. The name stays Raven's, because Compose Desktop's `undecorated` also removes resizing, which Raven keeps.
  - `HeaderBar(modifier, title: String? = null, buttons: Set<WindowButton> = WindowButton.all()) { content }` uses `R_WIDGET_HEADER_BAR`. A null `title` means the window's title, and `WindowButton` is Close, Minimize or Maximize. In `HeaderBarScope`, `Modifier.align(Alignment.End)` places a child on the right. Button side, title alignment and background wait until a sample needs them. `HeaderBar` without `customHeader = true` logs a warning.
- **Context menus** (`Rmenu_open`, Compose's `ContextMenuArea`) need right-click, so they join the Generic input and focus fog.
