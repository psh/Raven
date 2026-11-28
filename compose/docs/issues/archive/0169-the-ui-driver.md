---
status: closed
---

# The UI driver in Raven's C

`raven/raven_driver.h`: the screen-reader tree made public as accessibles, found with selectors and
driven with real input, for tests in every language, `raven_preview` scripts and Raven MCP's UI tools.
Design: [Driving a UI](../../agent-skills.md#driving-a-ui).

**Depends on:** [Idle and the window list in Raven's C](0168-idle-and-the-window-list.md).

## Deliverables

- [x] `RAccessible` and `RAccessibleInfo` over `src/core/accessibility.{h,c}`: windows, meaningful widgets, and items (list, tree and tab rows).
- [x] The selector language: `role=` (`RAccessibleRole` in kebab case, listed in the header), `name=`/`name~=`, `text=`, `widget=`, state words, `[n]`. A selector that doesn't parse is reported through the error callback.
- [x] `Raccessible_find_all`/`_find` across every window, island, popup and dialog; `_describe`; `_click`, `_type`, `_press_key` as real input (menus opened through their menus); `_act`; `_get_last_error` with messages like compose-raven's.
- [x] Rows carry their own rects (today they carry their widget's); `Rbutton_get_text`; `Rwidget_get_accessible_description`.
- [x] Tests in `tests/` over the examples' XML: finding by each key, clicking a row, typing, a disabled button refusing a click, the failure messages.
- [x] The bindings regenerated with `tools/bindgen`.
- [ ] The upstream proposal opened (the header, the window list and idle, and the fixes), as what makes every binding testable: moved to [Propose the UI driver upstream](../on-hold/0187-propose-the-ui-driver-upstream.md), on hold.

## Resolution

Done, on macOS (arm64), headless, except the upstream proposal: that publishes outside this
machine, so it moved to [Propose the UI driver upstream](../on-hold/0187-propose-the-ui-driver-upstream.md),
on hold. `tests/test_driver.c` (99 checks) passes, and so do `bindings_up_to_date`, `binding_cpp`
and `./gradlew :raven-mcp:check`. The rest of `ctest` passes apart from `test_shell` and
`test_themes`, which already failed.

- **`raven_driver.h`** (in `Raven.h`) and **`src/core/driver.c`**, over `core/accessibility.c`'s
  tree.
  - `RAccessible` is a `uint64_t` (0: none). `RAccessibleInfo` holds the role, the texts (copied,
    so a frame doesn't change them), number and range, `RAccessibleState` (the tree's bits, one to
    one), the rect, the window, the widget and the item index.
  - `RAccessibleAction` is expand, collapse, select and focus.
  - `Raccessible_get_role_name` gives the selector's word for a role.
- **The API, changed from the design**: `Raccessible_count` and `Raccessible_get(window, selector,
  index)` replace `Raccessible_find_all(..., RAccessible* out, int max)`. bindgen reads a writable
  pointer as one value written back, so the array form would overflow in Python and elsewhere.
  `Raccessible_get_root`/`_parent`/`_child_count`/`_child` were added for `ui.tree`'s outline.
  `agent-skills.md` has both changes.
- **Selectors**, parsed in `driver.c`:
  - `role=` uses the role names listed in the header. `text-field`, not the design's
    `text-input`; the docs are fixed.
  - `name=` is exact, and `name~=` contains, whatever the case.
  - `text=` reads a field's value, else the name.
  - `widget=` is the XML Name. With an item role it means that widget's rows.
  - The states are checked, selected, expanded, focused, disabled and `enabled`.
  - `[n]` picks a match, and values may be quoted.
  - A selector that doesn't parse is reported (`R_ERROR_INVALID_VALUE`) and finds nothing.
- **Finding** walks every shown window: its content, island, popups and dialogs, in the order
  shown. `Raccessible_find` wants exactly one. Failures read "no accessible with …", "3
  accessibles with …, expected one", "… so none at 9", "role=button name="Go" is disabled" and "the
  accessible is gone".
- **Acting**, with real input:
  - `_click` clicks the centre. A list or tree row is scrolled into view first.
  - A menu item in a closed menu is reached by pressing its title in the menu bar, then hovering
    each submenu. The window is laid out after each menu opens, so the entries have their places.
  - `_type` and `_press_key` give the focus, then inject text or a key.
  - `_act` refuses what the state doesn't allow ("can't be expanded or collapsed").
- **Fixes** (`accessibility.c`, the widgets):
  - Rows and tabs carry their own rects (`rvn_list_view_row_box`, `rvn_tree_view_row_box`,
    `rvn_tab_view_tab_box`), and a row scrolled out of its list is `OFFSCREEN`.
  - A tree's items are every visible row, nested ones included, where before they were only the
    top level. Only a row with children is expandable.
  - Menu entries are placed where their open menu draws them (`rvn_menu_entry_box`), and are
    `OFFSCREEN` while it is closed. Menus are expandable and expanded, and are named by their
    title: before, a menu had no name, for screen readers too.
  - `Rbutton_get_text` returns the button's first label. `Rwidget_get_accessible_description`
    returns the given description, or NULL.
- **bindgen**: `typedef uint64_t RAccessible` is read and mapped to a 64-bit number.
  `RAccessibleState` is flags for the Odin wrapper, and C3 knows the type. The bindings are
  regenerated (1180 functions), and the Python binding drove notepad's File > Save by hand.
- **Tests** (`tests/test_driver.c`, `RAVEN_EXAMPLES_DIR`), over `examples/notepad` and
  `examples/editor`:
  - every key and state, and the failure messages;
  - File > Save, and View > Scale > Compact through a submenu;
  - 60 log rows, the 56th clicked after scrolling;
  - a tree row expanded, then its child clicked;
  - tabs;
  - typing and Backspace in a text field;
  - a disabled button refusing a click;
  - the getters;
  - an accessible that is gone.
- **Not done**: no check that something covers what is clicked, so a click lands on whatever is
  on top, as a user's would. Not tried with a native menu bar (macOS's, when it isn't drawn), with
  Vulkan, or on Linux and Windows.
