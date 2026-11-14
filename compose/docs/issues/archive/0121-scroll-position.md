---
status: closed
---

# Scroll position

Scroll state for the scroll containers, the lazy lists and `ListView`. Spec: [8: Scroll position](../../spec.md#scroll-position-phase-3).

**Depends on:** [Lists and trees](0120-lists-and-trees.md).

## Deliverables

- [x] `ScrollState` (`value` polled, `scrollTo`, jumping `animateScrollTo`, `scrollToEnd()`), the `state` of `VerticalScroll`/`HorizontalScroll`.
- [x] `LazyListState` (`firstVisibleItemIndex`/`ScrollOffset` polled, `scrollToItem` to the top, jumping `animateScrollToItem`).
- [x] Both saveable; positions asked for before attach applied on attach; one scrollable per state.
- [x] `ListViewState` with `revealItem(index)`; the `state` of `ListView`.
- [x] Harness: `performScrollTo()` and `performScrollToIndex(i)` ([Scroll position](0096-scroll-position.md)).
- [x] Tests: positions read back, restored, applied on attach, and revealed rows.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 494 tests, 7 of them `ScrollStateTest`'s.

- **Following**: Raven reports no scrolling, so each state follows its scroll box through a
  `WidgetFollower`, read once a frame before the frame recomposes, as `CodeEditorState.cursor` is.
  Raven lays out a window before it sets an offset, but only for a box in one, so a position asked
  for (or restored) before the box is shown waits, and is applied on the first frame it is. A state
  given to a second scrollable warns (`layoutWarningSink`) and follows only the first.
- **`ScrollState`** (`ScrollState(initial)`, `rememberScrollState(initial)`), the new `state`
  parameter of `VerticalScroll` and `HorizontalScroll`, second after `modifier`, null by default:
  `value` in dp, `scrollTo(value)` returning how far it moved, `animateScrollTo` (a jump, Raven
  not animating scrolling) and `scrollToEnd()`.
- **`LazyListState`** (`rememberLazyListState(initialFirstVisibleItemIndex,
  initialFirstVisibleItemScrollOffset)`), the `state` of `LazyColumn` and `LazyRow`, second as in
  Compose: `firstVisibleItemIndex` (the first item whose end is past the top) and
  `firstVisibleItemScrollOffset`, from the items' widgets' places; `scrollToItem(index,
  scrollOffset)` puts the item at the top, waiting until the item is there; `animateScrollToItem`
  jumps the same way. **Changed**: each lazy item is now in an internal Box (an Overlay without a
  role) filling the list across, as each of Compose's lazy items is a node of its own, so an item
  that emits several widgets still has one place. The spec says so; `ListsTest`'s rows follow.
- **Saved**: both are `rememberSaveable`, with `ScrollState.Saver` and `LazyListState.Saver`.
  `compose-raven-foundation` gains `runtime-saveable` (the version catalog's
  `compose-runtime-saveable`).
- **`ListViewState`** (`rememberListViewState()`), the new `state` of `ListView`, before
  `onActivate`: `revealItem(index)` is `Rlist_view_scroll_to_item`, and does nothing on a state not
  attached to a list. Raven has no getter for a list's position, so the reveal is only checked to
  reach Raven without an error.
- **Harness**: `performScrollTo()` (`Rwidget_scroll_into_view`) and `performScrollToIndex(i)`, which
  puts a lazy list's item at the top, or reveals a ListView's row; each draws a frame after, so the
  states read it.
- **Tests** (`ScrollStateTest`): a scroll state reading the user's scroll, `scrollTo`,
  `scrollToEnd`, a clamped `animateScrollTo` and `performScrollTo`; a position asked for before the
  box is shown; a position restored through a `SaveableStateHolder`, and the saver; a state given to
  two scrollables; a lazy list's first visible item through `scrollToItem`, the user's scroll and
  `performScrollToIndex`; a lazy row's position given before its items are in, and its saver; a
  ListView revealing rows.
