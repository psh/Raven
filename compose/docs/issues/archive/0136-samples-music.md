---
status: closed
---

# samples/music

Port Raven's `examples/music`: the player, its pages, its themes and its island. Spec: [10. Samples](../../spec.md#10-samples).

**Depends on:** [Generic input, focus and menus](0113-generic-input-focus-and-menus.md), [Accessibility semantics](0114-accessibility-semantics.md), [Images and resources](0115-images-and-resources.md), [Fonts and text styling](0116-fonts-and-text-styling.md), [Modifier.offset and LocalDensity](0118-layout-offset-and-density.md), [Theming extras](0119-theming-extras.md), [Lists and trees](0120-lists-and-trees.md), [Scroll position](0121-scroll-position.md), [Tabs and grids](0122-tabs-and-grids.md), [Custom drawing, viewports and effects](0124-custom-drawing-viewports-and-effects.md), [Animation](0127-animation.md), [Island](0128-island.md), [compose-raven-navigation3](0131-compose-raven-navigation3.md), [ColorPicker](0132-colorpicker.md).

## Deliverables

- [x] `samples/music` with the C example's look, its own `MusicStyle` palettes over the example's theme files ([Music's class-styled themes](0099-musics-class-styled-themes.md)) and its widgets as the app's composables (`IconButton`, `Cover`, `Visualizer`, ...).
- [x] The pages as a `NavDisplay`, brought to the front when visited again, Search's two views a plain `if`, back with `Window(backKeys)` ([Music's pages and back navigation](0098-musics-pages-and-back-navigation.md)).
- [x] The animated backdrop and heroes as `Surface(effect)` following the cover; the visualizer as a `Canvas` driven by a `withFrameNanos` loop; covers and icons as `ImageBitmap`s with `filterQuality = High` and `ColorFilter.textColor()`.
- [x] The accent as `ColorPicker(popup = true, alpha = false)`; the island in the notch; the player bar, search and settings.
- [x] Runs from Gradle, documented in `compose/README.md`.
- [x] A headless test: playing, navigating forward and back, searching, changing the look.

## Gaps

None. Deliberate differences from the C example, decided in [Music's pages and back navigation](0098-musics-pages-and-back-navigation.md): Back to an album shows that album; a page visited again moves to the top instead of repeating; Back is disabled at the root; Back also takes Cmd+[ and the mouse Back button.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 536 tests, 5 of them `MusicTest`'s and 1 new in
`ComponentsTest`. The sample was launched in Nocturne, Daylight and Raven light, on Home, an album,
a playlist, Search (genres and results) and Settings, and with the island unfolded. It looks as the
C example does.

- **`samples/music`** (`settings.gradle.kts`, `launch-music`, documented in `compose/README.md`):
  - Gradle writes the path of `examples/music` in as `EXAMPLE_DIR`, so the sample reads the
    example's theme files and covers where they are.
  - `Library` makes the same library up as `library.c`: the same generator, seed and call order,
    with the covers loaded or drawn.
  - `Player` plays by the frame clock.
  - `RAVEN_MUSIC_LOOK`, `_PAGE`, `_PAUSED` and `_ISLAND` work as in C.
- **Themes and style**:
  - The looks are `RavenTheme(look, dark, theme, accent)`. Each is a theme file of the example, or a
    look of Raven with `styles.xml` over it.
  - What the theme files say of their classes is a `MusicStyle` per theme, and one for any look
    ([Music's class-styled themes](0099-musics-class-styled-themes.md)). The app's composables read
    it through `LocalMusicStyle`.
  - The accent follows the cover playing, a darker shade on a light look. A colour picked in
    Settings (`ColorPicker(popup = true, alpha = false)`) replaces it.
- **Widgets** as composables:
  - `Icon` comes from a 32-glyph SDF atlas, as `BitmapPainter`s with `ColorFilter.textColor()` or a
    tint.
  - `Cover` (`FilterQuality.High`), `Heading`, `IconButton` (brighter under the pointer), `PlayButton`
    and `NavItem`.
  - `Chip`, `AlbumCard`, `QuickCard` and `TrackRow`, each using the C example's button style (flat
    or default).
  - `LibraryRow`, `GenreTile`, `SettingRow`.
  - `Visualizer` is a `Canvas` moved by a `withFrameNanos` loop with the C example's fake levels. It
    stops asking for frames once at rest.
- **Pages**:
  - A `NavDisplay` of `@Serializable` keys: Home, an album, a playlist, Search, the library and
    Settings.
  - A page visited again is brought to the front; Back is disabled at the root. Back also works
    with Alt+Left, Cmd+[ and the mouse's Back button (`Window(backKeys)`), as decided in
    [Music's pages and back navigation](0098-musics-pages-and-back-navigation.md).
  - Search's two views are a plain `if` on the query.
  - Liked songs is made again as hearts change.
- **Around the pages**:
  - The custom header has back, home, the search field (Ctrl+F focuses it) and settings.
  - The side bar has the library and its filters; the now playing panel has the artist and the
    queue.
  - The player bar has the transport, seek and volume.
  - The shortcuts are Space, Ctrl+Left/Right, Ctrl+L, Ctrl+F and Ctrl+P.
- **Effects**:
  - The backdrop is a `Surface(effect)` in the cover's colours: Mesh, Aurora, Plasma, Spotlight,
    Shimmer or none, animated or still.
  - The album hero and the artist card are Aurora `Surface`s following the cover.
- **The island**:
  - It sits in the notch (`attached`, `notch`). Folded, it shows the song, or a notice for 2.5 s by
    the frame clock.
  - Unfolded, it is a small player with the look's `ComboBox`.
- **Found while porting, filled with a test**:
  - A button's content Row was always centred and as wide as its children. `Modifier.weight` inside
    a wide button (a card, a track row) therefore had no room to take.
  - It now spans the button while a child is weighted, as Compose's Row given the button's width
    does. The spec and `Button`'s KDoc say so.
- **Tests** (`MusicTest`):
  - Space plays; the clock moves; Next, Ctrl+Left and the play button work.
  - A page visited again comes to the front. Back works with Alt+Left and with the button, and is
    disabled at the root. A playlist's play button plays it.
  - Ctrl+F focuses the search. A query matching nothing says so; a genre's tile searches for it; the
    top result opens its album.
  - Daylight lightens the window and moves the accent. A look of Raven applies, and a colour picked
    becomes the accent.
  - Ctrl+L likes the song into Liked songs, and the island says so, then shows the song again.
