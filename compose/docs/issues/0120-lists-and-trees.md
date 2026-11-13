---
status: open
---

# Lists and trees

Composable lists, and Raven's virtualised list and tree; completes `samples/themes`' `ListView`. Spec: [8: Lists and trees](../spec.md#lists-and-trees-phase-3).

**Depends on:** [Generic input, focus and menus](archive/0113-generic-input-focus-and-menus.md), [Images and resources](0115-images-and-resources.md).

## Deliverables

- [ ] `LazyColumn`/`LazyRow` with Compose's DSL (`items(list, key)`), composing every item in phase 3, keys kept stable with `Rwidget_move_child`.
- [ ] `ListView(items, itemText, selectedIndex, onSelectedIndexChange, modifier, onActivate)` in virtual mode, selection controlled.
- [ ] `TreeView(roots, children, itemText, key, selection, onSelectionChange, multiSelect, initiallyExpanded, onActivate, contextMenuItems, itemIcon, modifier)`, diffed by key onto Raven's items.
- [ ] Tests: keyed moves keep rows, controlled selection reverts, tree diffs, a tree's context menu.
