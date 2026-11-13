---
status: open
---

# samples/glass

Port Raven's `examples/glass`: floating glass bars over a page that scrolls under them. Spec: [10. Samples](../spec.md#10-samples).

**Depends on:** [Generic input, focus and menus](0113-generic-input-focus-and-menus.md), [Images and resources](archive/0115-images-and-resources.md), [Lists and trees](0120-lists-and-trees.md), [Tabs and grids](0122-tabs-and-grids.md).

## Deliverables

- [ ] `samples/glass` with the C example's look: the frosted side bar, tinted tool bar and clear player bar over the scrolling page, with their appear transitions.
- [ ] The cards as a `LazyVerticalGrid(GridCells.Fixed(3), minCellSize)` of `Image`s with `ContentScale.Crop` and rounded clips under glass chips.
- [ ] The library as a `TreeView`; the More button's menu as a `DropdownMenu` with a checkable item and a shortcut; dark and light glass from a switch.
- [ ] The player's time from a `withFrameNanos` loop while playing.
- [ ] Runs from Gradle, documented in `compose/README.md`.
- [ ] A headless test: the theme switch, the menu, the seek slider.

## Gaps

None: its gaps were decided into phase 3 on [compose-raven phase 3 sample gaps](0095-compose-raven-phase-3-sample-gaps.md).
