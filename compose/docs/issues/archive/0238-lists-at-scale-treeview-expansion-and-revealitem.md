---
status: closed
---

# Lists at scale: TreeView expansion and revealItem

Make `TreeView`'s expansion controlled (`expanded`, `onExpandedChange`), ask for children lazily,
and add `TreeViewState.revealItem(key)`. Spec: [8: TreeView expansion](../../spec.md#treeview-expansion-phase-5)
and section 12. Planned in [Lists at scale: TreeView expansion and item operations](0217-lists-at-scale-treeview-expansion-and-item-operations.md).

**Depends on:** [Raven: tree expand callback](0237-raven-tree-expand-callback.md).

## Deliverables

- [x] `expanded: Set<Any>` and `onExpandedChange` replacing `initiallyExpanded`, over the expand callback; a refused change reverts; a collapse moving the selection calls `onSelectionChange`.
- [x] `children(item)` asked for only for the shown items and one level below them; the mirror keeps Raven items added once.
- [x] `TreeViewState` with `revealItem(key)`: ancestors opened through `onExpandedChange`, then scrolled to.
- [x] Tests: each user path reported, a refusal reverting, the lazy children (a counter of `children` calls), `revealItem` into a closed branch, saveable expansion, and a tab renamed through `Tab(title)`.
- [x] The XML translator's `Expanded` on `<Item>` as the initial `expanded` set; the samples and examples using `initiallyExpanded` moved over.
- [x] The spec's sections checked against what was built; `api-coverage.md` updated.

## Resolution

Built on 2026-11-27.

- **`TreeView`** takes `expanded: Set<Any>` and `onExpandedChange` after `onSelectionChange`, both
  required, as a default that refused every change would leave the arrows dead. It also takes
  `state: TreeViewState` last. `initiallyExpanded` is gone.
- **ui** gains:
  - `TreeViewExpansion`, a controlled set of item handles, read and written over every item;
  - `TreeViewExpandedChanged`, over `Rtree_view_set_expanded_callback`;
  - a deferred `bind(key, controlled) { value }`.

  The deferred bind resolves the expanded keys to handles as the change is applied, after the
  mirror has added the items new to the tree, so an item restored open opens once it is there.
  `onExpandedChange` gets what Raven shows now, plus the keys of the set that have no items yet.
  That way the two branches a selection opens arrive as one set, with no state kept between
  events. A refused change reverts through the controlled value, and a collapse that moves the
  selection reports it through `onSelectionChange`.
- **Lazy children**: the mirror asks `children` only of the shown items. The roots are shown,
  and so are the children of an open shown item. The children of a closed branch keep the items
  Raven already has.
- **`TreeViewState.revealItem(key)`** searches the data for the branches above the item. If any
  is closed, it calls `onExpandedChange` with them added, and a `TreeReveal` binding scrolls to the
  item (`Rtree_view_scroll_to_item`) on the next composition, once only. A shown item is scrolled
  to at once, and a key not in the tree does nothing.
- **Tests** are `TreeViewExpansionTest` (5):
  - the arrow each way, a double click, Left and Right, and the branches Raven's selection opens
    arriving as one set;
  - a collapse moving the selection, a refusal reverting, and the app's own change;
  - the lazy children, counting the `children` calls;
  - `revealItem` into a closed branch, before and after it is given to a tree, on a shown item,
    and on an unknown key;
  - the expansion saved by `rememberSaveable` across a `SaveableStateHolder`;
  - a tab renamed through `Tab(title)`. Raven has no title getter, so the test checks the tab
    view's desired width, which Raven measures from the titles.

  `ListsTest` now passes the expansion; its "only new items start expanded" test went with
  `initiallyExpanded`.
- **XML**: the translator maps `<TreeView>` and its nested `<Item>`s, each known by its place.
  Their `Expanded="true"` is the initial `expanded` set, and `Icon` is a warning. Its example is in
  `TranslatorTest`, and the examples check now translates and compiles `editor.xml`'s tree.
- **Samples**: `glass` holds its library's expansion in `rememberSaveable`, Playlists open, and
  `editor` passes an empty set for its childless outliner.
- **Docs**: the spec's section records the signature, the bindings and the translation, and
  section 12's row now says the expansion is controlled. `api-coverage.md` is at 587 of 1,705
  functions, with `raven_controls.h` at 169.
- **Suites**: every module's suite and the samples' pass.
