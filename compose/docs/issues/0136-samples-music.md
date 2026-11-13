---
status: open
---

# samples/music

Port Raven's `examples/music`: the player, its pages, its themes and its island. Spec: [10. Samples](../spec.md#10-samples).

**Depends on:** [Generic input, focus and menus](archive/0113-generic-input-focus-and-menus.md), [Accessibility semantics](archive/0114-accessibility-semantics.md), [Images and resources](archive/0115-images-and-resources.md), [Fonts and text styling](archive/0116-fonts-and-text-styling.md), [Modifier.offset and LocalDensity](archive/0118-layout-offset-and-density.md), [Theming extras](archive/0119-theming-extras.md), [Lists and trees](archive/0120-lists-and-trees.md), [Scroll position](0121-scroll-position.md), [Tabs and grids](0122-tabs-and-grids.md), [Custom drawing, viewports and effects](0124-custom-drawing-viewports-and-effects.md), [Animation](archive/0127-animation.md), [Island](archive/0128-island.md), [compose-raven-navigation3](0131-compose-raven-navigation3.md), [ColorPicker](archive/0132-colorpicker.md).

## Deliverables

- [ ] `samples/music` with the C example's look, its own `MusicStyle` palettes over the example's theme files ([Music's class-styled themes](archive/0099-musics-class-styled-themes.md)) and its widgets as the app's composables (`IconButton`, `Cover`, `Visualizer`, ...).
- [ ] The pages as a `NavDisplay`, brought to the front when visited again, Search's two views a plain `if`, back with `Window(backKeys)` ([Music's pages and back navigation](archive/0098-musics-pages-and-back-navigation.md)).
- [ ] The animated backdrop and heroes as `Surface(effect)` following the cover; the visualizer as a `Canvas` driven by a `withFrameNanos` loop; covers and icons as `ImageBitmap`s with `filterQuality = High` and `ColorFilter.textColor()`.
- [ ] The accent as `ColorPicker(popup = true, alpha = false)`; the island in the notch; the player bar, search and settings.
- [ ] Runs from Gradle, documented in `compose/README.md`.
- [ ] A headless test: playing, navigating forward and back, searching, changing the look.

## Gaps

None. Deliberate differences from the C example, decided in [Music's pages and back navigation](archive/0098-musics-pages-and-back-navigation.md): Back to an album shows that album; a page visited again moves to the top instead of repeating; Back is disabled at the root; Back also takes Cmd+[ and the mouse Back button.
