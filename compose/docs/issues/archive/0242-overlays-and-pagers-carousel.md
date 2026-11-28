---
status: closed
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: claude
---

# Overlays and pagers: Carousel

## Question

How is Raven's Carousel (pages shown one at a time and swiped, with dots, arrows, loop and
autoplay: `Rcarousel_*`) bound? Decide whether it follows Compose Foundation's
`HorizontalPager(state = rememberPagerState { pageCount })` and `PagerState` (`currentPage`,
`animateScrollToPage`), what of dots, arrows, loop and autoplay are parameters, and whether pages
are composable content. The answer is the spec section and its implementation tickets, with the
`carousel` port.

## Resolution

Resolved on 2026-11-26, with the user, in three rounds: designed into
[spec section 8: Pagers](../../spec.md#pagers-phase-5), section 10's
[samples/carousel](../../spec.md#samplescarousel-phase-5) and section 12.

- **Compose's pagers**: `HorizontalPager` and `VerticalPager`, not a `Carousel(orientation)`
  (Material 3's carousels are a different thing, several items in view).
- **A state object, not a controlled page**: `PagerState` (`rememberPagerState(initialPage) {
  pageCount }`) with only `currentPage`, `pageCount`, `scrollToPage` and `animateScrollToPage`,
  clamped as Compose's are. The position is scroll-like state, not a choice the app accepts.
- **Raven's extras as parameters**, at Raven's defaults: `loop = false`, `indicators = true`,
  `arrows = true`, and `autoplay: Duration? = null` on Raven's timer, which keeps its pauses.
- **Every page composed**; lazy page composition goes to the map's fog. Foundation's other
  parameters are left out and recorded in section 12.
- **No Raven change, no new harness API**; the `carousel` port has no port gaps.

Filed as [Overlays and pagers: HorizontalPager and VerticalPager](0253-overlays-and-pagers-horizontalpager-and-verticalpager.md)
and [samples/carousel](0254-samples-carousel.md).
