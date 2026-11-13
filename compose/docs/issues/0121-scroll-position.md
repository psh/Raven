---
status: open
---

# Scroll position

Scroll state for the scroll containers, the lazy lists and `ListView`. Spec: [8: Scroll position](../spec.md#scroll-position-phase-3).

**Depends on:** [Lists and trees](archive/0120-lists-and-trees.md).

## Deliverables

- [ ] `ScrollState` (`value` polled, `scrollTo`, jumping `animateScrollTo`, `scrollToEnd()`), the `state` of `VerticalScroll`/`HorizontalScroll`.
- [ ] `LazyListState` (`firstVisibleItemIndex`/`ScrollOffset` polled, `scrollToItem` to the top, jumping `animateScrollToItem`).
- [ ] Both saveable; positions asked for before attach applied on attach; one scrollable per state.
- [ ] `ListViewState` with `revealItem(index)`; the `state` of `ListView`.
- [ ] Harness: `performScrollTo()` and `performScrollToIndex(i)` ([Scroll position](archive/0096-scroll-position.md)).
- [ ] Tests: positions read back, restored, applied on attach, and revealed rows.
