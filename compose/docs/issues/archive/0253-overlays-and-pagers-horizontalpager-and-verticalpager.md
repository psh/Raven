---
status: closed
---

# Overlays and pagers: HorizontalPager and VerticalPager

Bind Raven's Carousel as `HorizontalPager` and `VerticalPager` with a `PagerState`. Spec:
[8: Pagers](../../spec.md#pagers-phase-5) and section 12. Planned in
[Overlays and pagers: Carousel](0242-overlays-and-pagers-carousel.md).

**Depends on:** [Lists at scale: TreeView expansion and revealItem](0238-lists-at-scale-treeview-expansion-and-revealitem.md).

## Deliverables

- [x] `HorizontalPager` and `VerticalPager(state, modifier, loop, indicators, arrows, autoplay, content)` over `Rcarousel_*`, every page composed as a child, `pageCount` changes adding and removing pages.
- [x] `PagerState` and `rememberPagerState(initialPage) { pageCount }`, saveable: `currentPage` following Raven's page changed callback, `scrollToPage` and `animateScrollToPage` clamped to the pages.
- [x] `autoplay: Duration?` over `Rcarousel_set_autoplay`; `loop`, `indicators` and `arrows` over their setters.
- [x] Tests: the moves of the state (clamped, not wrapping with `loop`), the user's keys and a dot updating `currentPage`, `loop` at the last page, a change of `pageCount`, the autoplay advancing, and the orientation.
- [x] The XML translator's `<Carousel>` as `HorizontalPager` or `VerticalPager`, with `Page`, `Loop`, `Autoplay`, `Indicators` and `Arrows`.
- [x] The spec's sections checked against what was built; `api-coverage.md` updated (the Carousel row and the numbers).

## Resolution

Built on 2026-11-27.

- **`HorizontalPager`**, **`VerticalPager`**, **`PagerState`** and **`rememberPagerState`** are in
  `raven.compose.foundation.pager`, as in Compose, with the spec's parameters.
  - Each page is composed in an internal Box, keyed by its index. `autoplay` goes to Raven in
    seconds, and null or zero is never.
  - ui gains `CarouselPageChanged`, over `Rcarousel_set_page_changed_callback`.
- **The state**: `currentPage` follows Raven's callback.
  - `scrollToPage` and `animateScrollToPage` clamp to `pageCount`, whatever `loop` says.
  - A page Raven doesn't have yet waits, and is shown in the follower's `settle` step once the
    page is composed. It is clamped to `pageCount` first, so `initialPage = 3` with two pages
    shows page 1.
  - When pages go, Raven keeps or clamps the page shown, and the state reads it back.
  - The saver keeps the page; the page count is the composition's.
- **Tests** are `PagerTest` (5):
  - every page composed, the orientation, and the moves clamped and not wrapped by `loop`;
  - Right, End, the last page without and with `loop`, and a click on the third dot;
  - a clamped initial page, then `pageCount` growing and shrinking;
  - the autoplay advancing, in a `VerticalPager` with no dots or arrows;
  - the page saved across a `SaveableStateHolder`.
- **The XML translator** maps `<Carousel>` to either pager by `Orientation`, over a hoisted
  `rememberPagerState`.
  - Each child element is a page, chosen by a `when (page)`.
  - `Page`, `Loop`, `Autoplay` (as `.seconds`), `Indicators` and `Arrows` become parameters, and
    `OnChanged` is a warning.
  - The example is in `TranslatorTest`, and the examples check now compiles `carousel.xml`'s
    translation.
- **Docs**: the spec's section records what was built. Section 12's row stands.
  `api-coverage.md` is at 596 of 1,705 functions, with `raven_controls.h` at 178, 45 of 58 widget
  types, and the Carousel row marked built.
- **Suites**: the ui, foundation, xml and mcp suites pass.
