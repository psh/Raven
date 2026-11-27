---
status: open
---

# Raven: tree expand callback

Add `Rtree_view_set_expanded_callback(tree, callback, user_data)` to Raven, called for each
expansion change Raven makes itself, so `TreeView`'s expansion can be the app's state. Spec:
[11: change 14](../spec.md#11-required-raven-changes). Planned in
[Lists at scale: TreeView expansion and item operations](archive/0217-lists-at-scale-treeview-expansion-and-item-operations.md).

**Depends on:** [Lists at scale: TileView](0236-lists-at-scale-tileview.md), the ticket before it in
phase 5's implementation sequence.

## Deliverables

- [ ] `RTreeExpandCallback(tree, item, expanded, user_data)` and the setter in the tree view's header, documented: called for the arrow, a double click, Left and Right, and the ancestors a selection opens; not for `Rtree_item_set_expanded`.
- [ ] A collapse that moves the selection to the collapsed item calls the selection-changed callback.
- [ ] A C test: each user path calls it once with the item and the new state; `Rtree_item_set_expanded` doesn't; selecting a hidden item reports its ancestors; a collapse around the selection reports the selection.
- [ ] One commit on `compose-raven`, the bindings regenerated, spec section 11's change 14 marked made, and an on-hold upstream ticket.
