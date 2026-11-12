---
status: closed
---

# RavenTheme and theme files

`RavenTheme` at application level and inside a window, theme files, and `RavenTheme.colors`, for
the `themes` port and `notepad`'s View menu. Spec: [8: theming](../spec.md#phase-2-theming).

The accent, `saveTheme` and `themeNames` were decided in
[Theme extras the themes sample uses](archive/0077-theme-extras-the-themes-sample-uses.md).

**Depends on:** [Color, Shape and BorderStroke](archive/0060-color-shape-and-borderstroke.md).

## Deliverables

- [x] `RavenTheme(look: Look? = null, dark: Boolean? = null, theme: String? = null) { }` at
      application level emits no node: `look` and `dark` become one `Rlook_set`, `theme` uses
      `Rtheme_use`, and its CompositionLocals reach every window through
      `rememberCompositionContext`. In `singleWindowApplication`, the `RavenTheme` at the top of
      the content counts as application level.
- [x] Inside a window, `RavenTheme(theme = ...)` gives its subtree that theme through an internal
      Box and `Rwidget_set_theme`. Setting `look` or `dark` there logs an error and does nothing.
- [x] `Look`: `Raven`, `Glass`, `HighContrast`, `Windows`, `Macos`, `Gnome`, `Native` and
      `MacosGlass`.
- [x] `loadTheme(path): String` and `loadThemeFromString(xml): String` (`Rtheme_load`,
      `Rtheme_load_from_memory`), valid only while Raven runs.
- [x] `RavenTheme(accent = ...)` at application level through `Rtheme_set_accent`, applied again
      after each look or theme change, which resets it.
- [x] `saveTheme(path, name): Boolean` through `Rtheme_save`, valid only while Raven runs.
- [x] `RavenTheme.themeNames`, snapshot state from `Rtheme_get_count`/`_name_at`, refreshed after
      `loadTheme`, a look change, and Raven's reload of a theme file.
- [x] Live reload, decided in [Raven's live reload under compose-raven](archive/0084-ravens-live-reload-under-compose-raven.md):
      `application(liveReload = Platform.isDebugBinary)` and the same on `singleWindowApplication`,
      calling `Rxml_set_live_reload` after `Rinit` unless `RAVEN_LIVE_RELOAD` is set;
      compose-raven owns `Rxml_set_reload_callback` (inside the C-boundary catch), with no public
      reload event.
- [x] Harness: the host runs with live reload off, and an internal `checkFiles()` over
      `Rxml_check_files` lets a test trigger a reload.
- [x] `RavenTheme.colors`: one `Color` per Raven colour role, read from the app theme with
      `Rtheme_get_color`, and recomposing readers when the look or theme changes.
- [x] Tests: changing `look`, `dark` or `theme` changes Raven's; a subtree theme affects only its
      subtree; `RavenTheme.colors` follows an app-level change; the window-level error is logged; an accent survives a
      change of look; a saved theme loads back with its accent; `themeNames` lists a loaded theme and follows it when
      the file is rewritten under a new name and `checkFiles()` runs; a test host never reloads by
      itself.

## Resolution

Done on macOS, on top of [Window, WindowState and its parameters](archive/0086-window-windowstate-and-its-parameters.md).
`./gradlew build allTests` passes: 216 tests, 76 of them in ui and 74 in foundation.

- **Where it lives**: `RavenTheme`, `Look`, `RavenColors`, `loadTheme`, `loadThemeFromString` and
  `saveTheme` are in `compose-raven-ui`, package `raven.compose.ui.theme`, with the host's
  plumbing they need. The spec names no package. ui is where the later settings land too
  (`material` with `Modifier.material`, `fontFamily` and `typography` with phase 3's text types),
  and the window-level Box is a `WidgetDescriptor` over an Overlay, so nothing new crosses into
  foundation behind `@InternalComposeRavenApi`.
- **Placement**: a static `LocalThemePlacement` (`Application`, `TopOfSingleWindow`, `Window`).
  The application composition is `Application`, each window's composition provides `Window`, and
  `singleWindowApplication` wraps its content in `TopOfSingleWindow`, which its outermost
  `RavenTheme` turns back into `Window` for its content. At application level, a
  `DisposableEffect` keyed on the settings calls `HostTheme.apply`, which runs before the windows'
  own effects create and fill them. It makes only what changed: `look` and `dark` as one
  `Rlook_set` (a null one keeps what `Rlook_get` has), then `Rtheme_use`, again after a look
  change since that can replace a theme that names its own look, then `Rtheme_set_accent` after
  any look or theme change. A null setting leaves Raven's as it is.
- **Inside a window**: the content always goes in a Box (an Overlay with `R_ROLE_NONE`) whose
  theme is `Rwidget_set_theme`, null following the parent, so changing `theme` to or from null
  keeps the content's state. `look`, `dark` or `accent` there send one error to
  `themeErrorSink` (stdout by default) and do nothing.
- **`RavenTheme.colors`** is a real CompositionLocal (`LocalRavenColors`), which the host provides
  at the root of the application composition from its own snapshot state, so every window sees it
  through `rememberCompositionContext`, with or without a `RavenTheme`. `RavenColors` has one
  property per `RColorRole`, read with `Rtheme_get_color`.
- **`RavenTheme.themeNames`** is a plain property over the host's snapshot state, so it is
  readable in callbacks too. It and the colours are refreshed after each apply, `loadTheme`,
  `loadThemeFromString`, `checkFiles()` and reload callback. When the colours changed and an accent
  is set, the refresh applies the accent again first: a theme file loaded or reloaded under the
  name in use replaces the app theme, which resets it.
- **Theme files**: `loadTheme` and `loadThemeFromString` throw `IllegalArgumentException` with
  Raven's reason when Raven returns no name. They and `saveTheme(path, name: String? = null)` (the
  spec's signature) check that a host is running on the UI thread, through
  `RavenHost.running`, which the host sets after `Rinit` and clears before `Rshutdown`.
- **Live reload**: `application(liveReload = Platform.isDebugBinary)` and the same on
  `singleWindowApplication`. `RavenHost(liveReload = false)` calls `Rxml_set_live_reload` right
  after `Rinit` unless `RAVEN_LIVE_RELOAD` is set. The host owns `Rxml_set_reload_callback`,
  registered with its `StableRef`, cleared before `Rshutdown`, and run inside the C-boundary catch.
- **Found here, a gap in the spec**: Raven calls the reload callback only for interface files
  (`rvn_live_file_changed` in `src/xml/xml_watch.c`), not for theme files (`rvn_theme_watch` in
  `src/widget/theme.c`). So the callback alone would never refresh `themeNames`. The smallest
  working choice, with no fork change: while live reload is on and a theme file was loaded, the
  frame callback refreshes the theme state before it drains (after each window's read-back of
  its position and placement), after the task in which Raven
  reloaded the file; and `checkFiles()` refreshes it itself. A fork change making
  `rvn_theme_watch` call the reload callback would let the per-frame refresh go; it is worth
  deciding in the fork section.
- **Harness**: the host runs with `liveReload = false`, and `checkFiles()` calls the host's
  `checkFiles()` over `Rxml_check_files`.
- **Tests** (ui's `RavenThemeTest`, foundation's `ThemeFilesTest`, theme files written to a
  `mkdtemp` directory):
  - an application-level `look` and `dark` become Raven's look, a null one keeping Raven's;
  - an application-level `theme` becomes the app theme;
  - the outermost `RavenTheme` of a single window is application level and emits no node, and a
    nested one is a subtree setting;
  - a subtree theme affects only its subtree, and follows the app theme again when unset;
  - `RavenTheme.colors` follows a look and a theme change;
  - `look` and `accent` in a window log one error and change nothing;
  - the accent survives a change of look;
  - a saved theme loads back with its accent after a look's theme reset it;
  - `themeNames` lists a loaded theme, and follows it when the file is rewritten under a new name
    and `checkFiles()` runs;
  - a test host never reloads by itself.

  Without the accent applied again after a look change, the accent test fails; with the harness's
  live reload on, the never-reloads test fails, since Raven's watcher reloads and the per-frame
  refresh picks it up.
