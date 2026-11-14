---
status: closed
---

# samples/glass

Port Raven's `examples/glass`: floating glass bars over a page that scrolls under them. Spec: [10. Samples](../../spec.md#10-samples).

**Depends on:** [Generic input, focus and menus](0113-generic-input-focus-and-menus.md), [Images and resources](0115-images-and-resources.md), [Lists and trees](0120-lists-and-trees.md), [Tabs and grids](0122-tabs-and-grids.md).

## Deliverables

- [x] `samples/glass` with the C example's look: the frosted side bar, tinted tool bar and clear player bar over the scrolling page, with their appear transitions.
- [x] The cards as a `LazyVerticalGrid(GridCells.Fixed(3), minCellSize)` of `Image`s with `ContentScale.Crop` and rounded clips under glass chips.
- [x] The library as a `TreeView`; the More button's menu as a `DropdownMenu` with a checkable item and a shortcut; dark and light glass from a switch.
- [x] The player's time from a `withFrameNanos` loop while playing.
- [x] Runs from Gradle, documented in `compose/README.md`.
- [x] A headless test: the theme switch, the menu, the seek slider.

## Gaps

None: its gaps were decided into phase 3 on [compose-raven phase 3 sample gaps](../0095-compose-raven-phase-3-sample-gaps.md).

## Resolution

Done on macOS. `./gradlew build allTests` passes: 511 tests, 3 of them `GlassTest`'s. The sample was
launched and looks as the C example does: the three bars of glass over the page of cards.

- **`samples/glass`** (`settings.gradle.kts`, `launch-glass`, documented in `compose/README.md`;
  `glass.kexe light` starts with the light glass, as the C example's argument does).
- **The look**: an application-level `RavenTheme(look = Look.Glass, dark = dark)`, where the C
  example applies `R_THEME_GLASS_DARK` or `_LIGHT`; the side bar's switch changes `dark`.
- **The bars**: `Surface`s with `Modifier.material(Material.FrostedGlass | TintedGlass | Glass)`,
  rounded and shadowed, aligned in a `Box` over the page and padded off its edges, each arriving
  with its `appearTransition` and delay.
- **The page**: a `VerticalScroll` under everything, with the cards as a
  `LazyVerticalGrid(GridCells.Fixed(3), minCellSize = 200 x 150 dp, spacing = 16.dp)` of `Image`s
  of generated 96 x 96 pictures (`ContentScale.Crop`, `Modifier.clip(RoundedCornerShape(24.dp))`)
  under chips of clear glass, popping in one after the other; then the controls.
- **The side bar**: the library as a `TreeView` (Playlists expanded at first), the dark-glass and
  crossfade switches, and the volume.
- **The tool bar's More button** opens a `DropdownMenu` under it: two entries, Lossless audio as a
  `CheckboxItem`, a separator, and Quit with Ctrl+Q.
- **The player**: Play and Pause, and its time from a `withFrameNanos` loop while it plays (the C
  example's frame callback and continuous redraw); the seek slider and the page's progress bar
  follow it, and dragging the slider moves it.
- **Tests** (`GlassTest`, headless, with `mainClock.autoAdvance` off since the time recomposes every
  frame): the switch turning the glass light; the More menu opening, its checkable entry, Quit,
  and Ctrl+Q; the time moving while playing, still while paused, and the slider seeking.
