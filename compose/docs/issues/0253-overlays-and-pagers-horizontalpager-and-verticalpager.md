---
status: open
---

# Overlays and pagers: HorizontalPager and VerticalPager

Bind Raven's Carousel as `HorizontalPager` and `VerticalPager` with a `PagerState`. Spec:
[8: Pagers](../spec.md#pagers-phase-5) and section 12. Planned in
[Overlays and pagers: Carousel](archive/0242-overlays-and-pagers-carousel.md).

**Depends on:** [Lists at scale: TreeView expansion and revealItem](0238-lists-at-scale-treeview-expansion-and-revealitem.md).

## Deliverables

- [ ] `HorizontalPager` and `VerticalPager(state, modifier, loop, indicators, arrows, autoplay, content)` over `Rcarousel_*`, every page composed as a child, `pageCount` changes adding and removing pages.
- [ ] `PagerState` and `rememberPagerState(initialPage) { pageCount }`, saveable: `currentPage` following Raven's page changed callback, `scrollToPage` and `animateScrollToPage` clamped to the pages.
- [ ] `autoplay: Duration?` over `Rcarousel_set_autoplay`; `loop`, `indicators` and `arrows` over their setters.
- [ ] Tests: the moves of the state (clamped, not wrapping with `loop`), the user's keys and a dot updating `currentPage`, `loop` at the last page, a change of `pageCount`, the autoplay advancing, and the orientation.
- [ ] The XML translator's `<Carousel>` as `HorizontalPager` or `VerticalPager`, with `Page`, `Loop`, `Autoplay`, `Indicators` and `Arrows`.
- [ ] The spec's sections checked against what was built; `api-coverage.md` updated (the Carousel row and the numbers).
