---
status: on-hold
---

# Propose the tree expand callback upstream

> **On hold (2026-11-27).** Nothing is released beyond the local machine for now, and opening a
> pull request on Raven's `main` publishes it.

Propose `Rtree_view_set_expanded_callback` and `RTreeExpandCallback` to Raven's `main`, on their
own, as the way for an application to hold a tree view's expansion as its own state (to save it,
or load children lazily as branches open), while Raven opens and closes items itself and reports
none of it today. It doesn't mention compose-raven. Split out of
[Raven: tree expand callback](../archive/0237-raven-tree-expand-callback.md), which built it (spec
section 11, change 14; commit `41af6b4`).

**Depends on:** [Raven: tree expand callback](../archive/0237-raven-tree-expand-callback.md).

## Deliverables

- [ ] One pull request to Raven's `main`, carrying the setter, the callback type and their documentation in `raven_controls.h`, the calls in `src/widgets/tree_view.c` (`rvn_tree_set_expanded` and `rvn_tree_select_ex`), `test_tree_expand_callback` in `tests/test_widgets.c`, and the bindings regenerated against upstream's headers.
