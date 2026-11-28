---
status: open
---

# Lists at scale: layoutInfo and contentType

Give `LazyListState` Compose's `layoutInfo` and the `canScroll` flags, and the DSL its
`contentType`. Spec: [8: Lazy virtualisation](../spec.md#lazy-virtualisation-phase-5) and section 12.
Planned in [Lists at scale: lazy virtualisation](archive/0214-lists-at-scale-lazy-virtualisation.md).

**Depends on:** [Lists at scale: the lazy list window](archive/0233-lists-at-scale-the-lazy-list-window.md).

## Deliverables

- [ ] `LazyListLayoutInfo` and `LazyListItemInfo` (`index`, `key`, `offset`, `size`), with `visibleItemsInfo`, `totalItemsCount`, `viewportStartOffset` and `viewportEndOffset` in dp; `LazyListState.layoutInfo` as snapshot state, read once a frame from the window.
- [ ] `canScrollForward` and `canScrollBackward`.
- [ ] `contentType` on `item`, `items` and the list extensions, accepted and ignored.
- [ ] Tests: the layout info as the list scrolls and its items change; the flags at each end; a recomposition reading `layoutInfo` (a "scroll to top" button shown once past the first item).
- [ ] Section 12's rows checked against what was built.
