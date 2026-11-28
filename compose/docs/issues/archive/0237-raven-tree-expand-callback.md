---
status: closed
---

# Raven: tree expand callback

Add `Rtree_view_set_expanded_callback(tree, callback, user_data)` to Raven, called for each
expansion change Raven makes itself, so `TreeView`'s expansion can be the app's state. Spec:
[11: change 14](../../spec.md#11-required-raven-changes). Planned in
[Lists at scale: TreeView expansion and item operations](0217-lists-at-scale-treeview-expansion-and-item-operations.md).

**Depends on:** [Lists at scale: TileView](0236-lists-at-scale-tileview.md), the ticket before it in
phase 5's implementation sequence.

## Deliverables

- [x] `RTreeExpandCallback(tree, item, expanded, user_data)` and the setter in the tree view's header, documented: called for the arrow, a double click, Left and Right, and the ancestors a selection opens; not for `Rtree_item_set_expanded`.
- [x] A collapse that moves the selection to the collapsed item calls the selection-changed callback.
- [x] A C test: each user path calls it once with the item and the new state; `Rtree_item_set_expanded` doesn't; selecting a hidden item reports its ancestors; a collapse around the selection reports the selection.
- [x] One commit on `compose-raven`, the bindings regenerated, spec section 11's change 14 marked made, and an on-hold upstream ticket.

## Resolution

Built on 2026-11-27, in commit `41af6b4`.

- **`raven_controls.h`** gains `RTreeExpandCallback(tree_view, item, expanded, user_data)` and
  `Rtree_view_set_expanded_callback`, documented beside the activated callback.
- **`tree_view.c`**:
  - `rvn_tree_set_expanded` calls it when `notify` is set. That covers the arrow, a double click,
    and Left and Right; `Rtree_item_set_expanded` passes no `notify`.
  - `rvn_tree_select_ex` opens a hidden item's closed branches and reports each, outermost
    first. It does so whoever selected the item, `Rtree_view_set_selected_item` included, since
    the application didn't ask for those branches to open.
  - Ids are saved before each call, so a callback that destroys the tree view or the item ends the
    work safely.
- **The collapse around the selection** already called the selection-changed callback, which
  `test_list_and_tree` checks. The plan was wrong to say it skipped it. The collapse is now
  reported first, then the selection.
- **`test_tree_expand_callback`** in `tests/test_widgets.c` checks:
  - the arrow each way, a double click, and Left and Right, each reported once with the item and
    its new state;
  - four `Rtree_item_set_expanded` calls reporting nothing;
  - a hidden item selected reporting both its ancestors, outermost first;
  - the collapse around the selection followed by the selection change;
  - no callback reporting nothing.

  All 108 C tests pass, with `test_pdf` skipped as before; `test_widgets` has 368 checks.
- **Bindings** are regenerated (20 files), and `bindgen.py --check` passes.
- **Spec** section 11's change 14 is marked made. The upstream proposal is
  [Propose the tree expand callback upstream](../0311-propose-tree-expand-callback-upstream.md),
  on hold.
