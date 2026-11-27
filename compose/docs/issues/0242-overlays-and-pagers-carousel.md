---
status: open
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: null
---

# Overlays and pagers: Carousel

## Question

How is Raven's Carousel (pages shown one at a time and swiped, with dots, arrows, loop and
autoplay: `Rcarousel_*`) bound? Decide whether it follows Compose Foundation's
`HorizontalPager(state = rememberPagerState { pageCount })` and `PagerState` (`currentPage`,
`animateScrollToPage`), what of dots, arrows, loop and autoplay are parameters, and whether pages
are composable content. The answer is the spec section and its implementation tickets, with the
`carousel` port.
