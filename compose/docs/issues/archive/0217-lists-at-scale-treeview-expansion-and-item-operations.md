---
status: closed
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: claude
---

# Lists at scale: TreeView expansion and item operations

## Question

How does `TreeView`'s expansion become the app's state, and which item operations do `TreeView`
and `ListView` gain? Expansion needs a Raven change (an expand or collapse callback); decide its C
shape and the controlled `expanded` state. The item operations: scrolling to an item, the item at
a point, the selected item, renaming a tab. The answer is the spec section, the Raven change and
the implementation tickets.

## Resolution

Resolved on 2026-11-26, with the user, in one round: designed into
[spec section 8: TreeView expansion](../../spec.md#treeview-expansion-phase-5) and
[section 11, change 14](../../spec.md#11-required-raven-changes).

- **The Raven change**: `Rtree_view_set_expanded_callback`, called for every expansion change Raven
  makes itself (arrow, double click, Left/Right, ancestors a selection opens), not for
  `Rtree_item_set_expanded`; a collapse moving the selection also calls the selection callback.
- **Controlled `expanded: Set<Any>` and `onExpandedChange`**, replacing `initiallyExpanded`; a refused
  change reverts.
- **Children asked for lazily**, down to one level below what is open.
- **`TreeViewState.revealItem(key)`** opens the ancestors through `onExpandedChange`, then scrolls;
  not saveable.
- **Item operations**: renaming a tab already works through `Tab(title)` (a test only); the selected
  item is already controlled; the item at a point waits for drags inside the app.

Filed as [Raven: tree expand callback](0237-raven-tree-expand-callback.md) and
[Lists at scale: TreeView expansion and revealItem](../0238-lists-at-scale-treeview-expansion-and-revealitem.md).
