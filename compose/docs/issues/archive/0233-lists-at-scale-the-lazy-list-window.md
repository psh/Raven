---
status: closed
---

# Lists at scale: the lazy list window

Make `LazyColumn` and `LazyRow` compose only the items in view, between two Spacers in their
ScrollBox, over an internal, pure lazy list window. Spec: [8: Lazy virtualisation](../../spec.md#lazy-virtualisation-phase-5)
and section 12. Planned in [Lists at scale: lazy virtualisation](0214-lists-at-scale-lazy-virtualisation.md).

**Depends on:** [Small widgets: taskbar progress, badge and Dock menu](0232-small-widgets-taskbar-progress-badge-and-dock-menu.md).

## Deliverables

- [x] The lazy list window, internal and pure: from cached sizes, keys, offset, viewport and the focused item, the range to compose, the Spacers' sizes, the offset correction and the layout info; tested on its own with plain numbers.
- [x] The lists hold a Spacer, the items in view plus one past each edge, and a Spacer; the range is worked out in `follow` before recomposing, so the items arrive in the same frame.
- [x] Sizes read from each item's Box after layout and cached by key (by place without one); unmeasured items at the average, 48 dp at first; the first frame filled by the estimate and corrected on the next.
- [x] The first visible item held by key when sizes above it are corrected or items are inserted or removed above it.
- [x] The focused item pinned while scrolled out of view; `rememberSaveable` in items kept by a `SaveableStateHolder` under the item's key.
- [x] `firstVisibleItemIndex`, its offset and `scrollToItem` over the cached sizes, an unmeasured target corrected over the next frames; the existing saving and restoring kept.
- [x] Host tests: 10,000 items composing only a screenful of widgets, scrolling, keys moving items, saveable state, the pinned focus, `scrollToItem` far down.
- [x] The phase 3 KDoc that points long lists at `ListView` updated; the spec's section and section 12's rows checked against what was built.

## Resolution

Built on 2026-11-27.

- **The window** is `lazy/LazyListWindow.kt`, internal and pure:
  - `LazySizes` caches sizes by key, with the estimate: the average of those measured, 48 dp first.
  - `LazyPlaces` holds each item's start and end, and finds the item at a position.
  - `lazyListWindow(count, keyOf, sizes, gap, anchor, viewport, pinned)` gives the slots, the
    offset, the anchor as clamped, the first visible item and the items in view. The items in view
    get one more past each edge, and a pinned item splits the Spacer it is in.
  - Spacers are whole dp, since Raven rounds a scroll box's offset to whole dp. Without that, items
    below an estimated Spacer sat a fraction off.
- **The lists**: `LazyListItems` holds the DSL as intervals and is rebuilt only when `content`
  changes (a `derivedStateOf`). Its key map is made then, with the unique-key check.
  - Each slot is one keyed group, so moved items keep their widgets. A `key` inside a `when`
    branch lost them.
  - Each item is in a `SaveableStateHolder` provider under its key.
- **No Raven change, one ui addition**: `WidgetFollower.settle`, called by `RavenHost.sendFrame`
  once the frame's composition is applied, before layout and paint.
  - `Rscroll_box_set_offset` clamps to the content as laid out, so the offset can only be set once
    the new Spacers are in.
  - `follow`, at the start of the frame, reads the user's scrolling, the viewport, the focus and
    the sizes. It recomposes only when the slots change.
  - `settle` puts the anchor item at its real place, reads the first visible item, and measures
    what was just composed.
- **The place** is an anchor: an index, its key, and an offset into the item. The user's scroll
  moves it, found from the items' real places, or estimated inside a Spacer. Content changes find
  it again by key.
- **`scrollToItem`** sets the anchor and scrolls the box to its estimated place at once. A
  position asked for while there are no items waits for them. Saving and restoring are unchanged.
- **Tests**: `performScrollToIndex` on a lazy list now goes through ui's `ScrollsToIndex` hook,
  as the box's children aren't its items any more.
  - `LazyListWindowTest` (12) covers the window with plain numbers.
  - `LazyVirtualisationTest` (8) covers 10,000 items composing a screenful, scrolling into a
    Spacer and by a little, `scrollToItem(9_000)` over unmeasured sizes, `performScrollToIndex`,
    the end clamped, and the place kept as sizes above are corrected and items come and go above
    it. It also covers keyed moves keeping widgets, `rememberSaveable` kept and `remember` lost,
    the pinned focus, and a `LazyRow`.
  - `BringIntoViewTest`'s lazy case now targets the item one past the edge, as item 20 of 30 is no
    longer composed.
  - Every module's suite passes (631 tests).
- **Docs**: the `LazyColumn` KDoc now says what is composed and points text rows at `ListView`;
  `LazyVerticalGrid`'s says it still composes every item. The spec's section records the settle
  step, whole-dp Spacers, requesters in items not composed, and drawing a `frame()` in tests after
  scrolling the box. Section 12's rows stand. No C API is newly used (547 of 1,704), so
  `api-coverage.md` is unchanged.
