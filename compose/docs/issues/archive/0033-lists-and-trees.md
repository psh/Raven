---
status: closed
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: Paul Hawke
---

# Lists and trees

## Question

How do Raven's ListView, TreeView and TileView, which are virtual and driven by callbacks (`Rlist_view_set_virtual_items`, text and event callbacks), map onto Compose? Decide between `LazyColumn`-style item DSLs and data-driven composables (`items: List<T>`, item text lambdas), how selection and activation are controlled, how trees express nesting and expansion, and what `music`, `editor` and `workspace` need.

## Resolution

Facts (`raven_controls.h`): **ListView** is virtualised and its rows are **text only**, filled item by item or through a virtual mode (`Rlist_view_set_virtual_items(count, textCallback)`). It has single selection, selection-changed and activated (double click or Enter) callbacks, and `scroll_to_item`. **TreeView** is virtualised, and its items are **handles, not widgets** (text, expanded flag, icon, text colour, user handle). It has multi-select, a right-click callback and item dragging, but **no expand or collapse callback**: Raven handles expansion itself. **TileView** draws tiles from a callback (`RTileItem`) with multi-select. Samples: `editor` uses a TreeView and a ListView, `workspace` a ListView, and `music` builds rich rows as widgets inside ScrollBoxes, which is Compose's `LazyColumn` pattern.

- **Two kinds of list**:
  - **Native text lists**, `ListView` and `TreeView`, compose-raven names over Raven's virtualised widgets, for thousands of text rows.
  - **Composable rows**, Compose's `LazyColumn` and `LazyRow`, with its DSL (`items(list, key = { ... }) { item -> ... }`), over `VerticalScroll`/`HorizontalScroll`. In phase 3 they **compose every item**, with no virtualisation (documented). `key` keeps rows stable across reorders through `Rwidget_move_child`. Real virtualisation from the ScrollBox offset goes to the Lazy virtualisation fog.
- **`ListView(items: List<T>, itemText: (T) -> String, selectedIndex: Int, onSelectedIndexChange: (Int) -> Unit, modifier, onActivate: (Int) -> Unit = {})`** uses virtual mode. The callback returns `itemText(items[i])` from the current list, through one C string buffer per node kept until the next call. A list change resets the count and repaints. The selection is controlled (the frame-callback check of [Events and state](0010-events-and-state.md)).
- **`TreeView(roots: List<T>, children: (T) -> List<T>, itemText: (T) -> String, key: (T) -> Any, selection: Set<Any>, onSelectionChange: (Set<Any>) -> Unit, multiSelect = false, initiallyExpanded: (T) -> Boolean = { false }, onActivate: (T) -> Unit = {}, contextMenuItems: ((T?) -> List<ContextMenuItem>)? = null, modifier)`**:
  - **Updates**: the node keeps a key-to-item-handle map and adds, destroys, re-texts and reorders Raven items to match the data on each composition.
  - **Selection** is a controlled set of keys.
  - **Expansion is not controlled**, because Raven reports no expand or collapse. `initiallyExpanded` only sets new items. Controlled expansion would need a fork change (an expansion callback), which goes to the fog.
  - **`contextMenuItems`** opens a menu at the pointer through `Rmenu_open`, as [Generic input and focus](0025-generic-input-and-focus.md) decided.
  - Icons wait for [Image and resources](0035-image-and-resources.md), and item dragging for the Gestures fog.
- **TileView** goes to the fog, because no phase 3 sample needs it (FileBrowser draws its own tiles).
