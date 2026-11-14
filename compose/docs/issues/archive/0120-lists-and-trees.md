---
status: closed
---

# Lists and trees

Composable lists, and Raven's virtualised list and tree; completes `samples/themes`' `ListView`. Spec: [8: Lists and trees](../../spec.md#lists-and-trees-phase-3).

**Depends on:** [Generic input, focus and menus](0113-generic-input-focus-and-menus.md), [Images and resources](0115-images-and-resources.md).

## Deliverables

- [x] `LazyColumn`/`LazyRow` with Compose's DSL (`items(list, key)`), composing every item in phase 3, keys kept stable with `Rwidget_move_child`.
- [x] `ListView(items, itemText, selectedIndex, onSelectedIndexChange, modifier, onActivate)` in virtual mode, selection controlled.
- [x] `TreeView(roots, children, itemText, key, selection, onSelectionChange, multiSelect, initiallyExpanded, onActivate, contextMenuItems, itemIcon, modifier)`, diffed by key onto Raven's items.
- [x] Tests: keyed moves keep rows, controlled selection reverts, tree diffs, a tree's context menu.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 460 tests, 11 of them `ListsTest`'s.

- **`LazyColumn(modifier, verticalArrangement, horizontalAlignment) { }` and `LazyRow`**, in
  `raven.compose.foundation.lazy` (`LazyList.kt`), with Compose's DSL: `item(key)`,
  `items(count, key)`, `items(list, key)` and `itemsIndexed(list, key)`, their content in a
  `LazyItemScope`. They are a `VerticalScroll` or `HorizontalScroll` composing every item, as the
  spec says for phase 3. Each item is composed in a `key` group, of its key or, without one, of its
  place, so when keyed items move the applier moves their widgets with `Rwidget_move_child` and
  nothing is composed again. Two items with one key are refused.
- **`ListView(items, itemText, selectedIndex, onSelectedIndexChange, modifier, onActivate = {})`**,
  in `Lists.kt`, uses Raven's virtual mode (`Rlist_view_set_virtual_items`): Raven asks for each
  row's text as it paints it, and gets it through one C string kept until the next row's, since it
  draws it at once. New items set the count again, which Raven does keeping the selection and the
  scroll position. The selection is a controlled value (`ListViewSelected`, `Binding.kt`), and
  `onActivate` gets a row double-clicked or Enter. The spec's `state` parameter belongs to
  [Scroll position](0121-scroll-position.md).
- **`TreeView(roots, children, itemText, key, selection, onSelectionChange, multiSelect = false,
  initiallyExpanded = { false }, onActivate = {}, contextMenuItems = null, itemIcon = null,
  modifier)`**, as [Lists and trees](0033-lists-and-trees.md) decided, with the ticket's `itemIcon`
  before `modifier`. It keeps a mirror of Raven's items by key and by handle, and matches it to the
  data on each composition: an item's text and icon are updated in place, and new items added.
  **Deviation from the decision**: Raven can't reorder items, only append them, so from the first
  item out of place among siblings, the rest are destroyed and added again. Whether each item was
  expanded is read before, so one added again keeps it; `initiallyExpanded` applies only to keys
  new to the tree. The spec says so. Keys must be unique in the whole tree.
  - The selection is a controlled set of keys, written to Raven as a set of item handles
    (`TreeViewSelection`); a key the tree hasn't is left out, and a tree without `multiSelect`
    takes one key at most.
  - `contextMenuItems` gets the item right-clicked, or null in the empty space, and its entries
    open at the pointer, with the `ContextMenuArea`'s menu (now internal).
  - `itemIcon` sets `Rtree_item_set_icon` through a `treeCalls` seam, Raven having no getter.
- **`samples/themes`** gains the C example's ListView (First, Second, Third, 110 high); it starts
  and runs without errors.
- **Tests** (`ListsTest`): keyed items keep their widgets through a move, an insertion and a
  removal, and Raven's child order follows; unkeyed items keep their places; duplicate keys fail;
  a ListView's rows asked for and refreshed; its selection taken, refused and reverted, set by the
  app, and an activation; a tree diffed by key through a rename, an insertion before (expansion
  kept) and a removal; `initiallyExpanded` for new items only; the tree's controlled selection, an
  activation and an unknown key; a multi-select tree; the context menu on an item and in the empty
  space; icons set and removed.
