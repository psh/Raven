---
status: closed
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: Paul Hawke
---

# Scroll position

## Question

Three samples move a scroll position, and phase 3 has no way to: `editor` and `workspace` keep their
log at its last line (`Rlist_view_scroll_to_item` after each line), and `music` puts an album page
back at the top when it shows another album (`Rscroll_box_set_offset`). Phase 1's
`VerticalScroll`/`HorizontalScroll` containers have no state (spec section 12 already notes they
stand in for `Modifier.verticalScroll(rememberScrollState())`), and `ListView` has none either.
Decide whether compose-raven gains a `ScrollState` (`rememberScrollState()`, `value`, `scrollTo`,
`animateScrollTo`) for the scroll containers, a `LazyListState` for `LazyColumn`/`LazyRow`, and a
scroll-to-item for `ListView`, and which of them read Raven's position back (Raven reports no
scroll changes, so a read is polled, as `CodeEditorState.cursor` is). Settle it before
[Music's pages and back navigation](0098-musics-pages-and-back-navigation.md), which depends on
whether a page can keep its scroll position.

## Resolution

Decided 2026-11-12. All three get a state holder, as in Compose; Raven reports no scroll changes,
so what a state reads is polled each frame, as `CodeEditorState.cursor` is. Written into spec
section 8 ([Scroll position](../../spec.md#scroll-position-phase-3)), section 9 and section 12.

- **`ScrollState`** (`rememberScrollState()`) is a `state` parameter of `VerticalScroll` and
  `HorizontalScroll` (the containers stay, as phase 1 settled), defaulting so phase 1 code compiles:
  `value: Int` (dp, polled from `Rscroll_box_get_offset`), `scrollTo` (`Rscroll_box_set_offset`),
  `animateScrollTo` (jumps, since Raven doesn't animate scrolling), and the extension
  `scrollToEnd()` (`Rscroll_box_scroll_to_end`) in place of `scrollTo(maxValue)`. No `maxValue` or
  `isScrollInProgress`.
- **`LazyListState`** (`rememberLazyListState()`): `firstVisibleItemIndex` and
  `firstVisibleItemScrollOffset` (polled from the offset and the item widgets, since phase 3
  composes every item), `scrollToItem(index, scrollOffset)` putting the item at the top, and
  `animateScrollToItem` (jumps). No `layoutInfo`, `isScrollInProgress` or `canScrollForward`.
- **Saveable**: both through `ScrollState.Saver` and `LazyListState.Saver`, with the window's
  saved-state owner; this is what [Music's pages and back navigation](0098-musics-pages-and-back-navigation.md)
  can rely on.
- **`ListViewState`** (`rememberListViewState()`) has only `revealItem(index)`
  (`Rlist_view_scroll_to_item`, which scrolls just enough to show the row). Raven can't report
  the list's position, so it isn't saveable. `LazyListState` wasn't reused, because its
  `firstVisibleItemIndex` would be wrong and its `scrollToItem` wouldn't put the row at the top.
- **Before attach**: the scroll states keep the requested (or restored) position and apply it when
  attached; `revealItem` on an unattached `ListViewState` does nothing, as `CodeEditorState`'s
  commands do.
- **One scrollable per state**: a second scrollable given the same state logs an error, and only
  the first uses it.
- **Tests**: the harness gains `performScrollTo()` (`Rwidget_scroll_into_view`) and
  `performScrollToIndex(i)` (`scrollToItem`, or `revealItem` on a `ListView`).
- **The samples**: `editor`'s and `workspace`'s logs use
  `LaunchedEffect(lines.size) { state.revealItem(lines.lastIndex) }`; `music` calls `scrollTo(0)`
  when it shows another album.
