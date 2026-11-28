---
status: open
---

# Lists at scale: TreeView expansion and revealItem

Make `TreeView`'s expansion controlled (`expanded`, `onExpandedChange`), ask for children lazily,
and add `TreeViewState.revealItem(key)`. Spec: [8: TreeView expansion](../spec.md#treeview-expansion-phase-5)
and section 12. Planned in [Lists at scale: TreeView expansion and item operations](archive/0217-lists-at-scale-treeview-expansion-and-item-operations.md).

**Depends on:** [Raven: tree expand callback](archive/0237-raven-tree-expand-callback.md).

## Deliverables

- [ ] `expanded: Set<Any>` and `onExpandedChange` replacing `initiallyExpanded`, over the expand callback; a refused change reverts; a collapse moving the selection calls `onSelectionChange`.
- [ ] `children(item)` asked for only for the shown items and one level below them; the mirror keeps Raven items added once.
- [ ] `TreeViewState` with `revealItem(key)`: ancestors opened through `onExpandedChange`, then scrolled to.
- [ ] Tests: each user path reported, a refusal reverting, the lazy children (a counter of `children` calls), `revealItem` into a closed branch, saveable expansion, and a tab renamed through `Tab(title)`.
- [ ] The XML translator's `Expanded` on `<Item>` as the initial `expanded` set; the samples and examples using `initiallyExpanded` moved over.
- [ ] The spec's sections checked against what was built; `api-coverage.md` updated.
