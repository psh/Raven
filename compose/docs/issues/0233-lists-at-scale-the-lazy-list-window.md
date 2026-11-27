---
status: open
---

# Lists at scale: the lazy list window

Make `LazyColumn` and `LazyRow` compose only the items in view, between two Spacers in their
ScrollBox, over an internal, pure lazy list window. Spec: [8: Lazy virtualisation](../spec.md#lazy-virtualisation-phase-5)
and section 12. Planned in [Lists at scale: lazy virtualisation](archive/0214-lists-at-scale-lazy-virtualisation.md).

**Depends on:** [Small widgets: taskbar progress, badge and Dock menu](archive/0232-small-widgets-taskbar-progress-badge-and-dock-menu.md).

## Deliverables

- [ ] The lazy list window, internal and pure: from cached sizes, keys, offset, viewport and the focused item, the range to compose, the Spacers' sizes, the offset correction and the layout info; tested on its own with plain numbers.
- [ ] The lists hold a Spacer, the items in view plus one past each edge, and a Spacer; the range is worked out in `follow` before recomposing, so the items arrive in the same frame.
- [ ] Sizes read from each item's Box after layout and cached by key (by place without one); unmeasured items at the average, 48 dp at first; the first frame filled by the estimate and corrected on the next.
- [ ] The first visible item held by key when sizes above it are corrected or items are inserted or removed above it.
- [ ] The focused item pinned while scrolled out of view; `rememberSaveable` in items kept by a `SaveableStateHolder` under the item's key.
- [ ] `firstVisibleItemIndex`, its offset and `scrollToItem` over the cached sizes, an unmeasured target corrected over the next frames; the existing saving and restoring kept.
- [ ] Host tests: 10,000 items composing only a screenful of widgets, scrolling, keys moving items, saveable state, the pinned focus, `scrollToItem` far down.
- [ ] The phase 3 KDoc that points long lists at `ListView` updated; the spec's section and section 12's rows checked against what was built.
