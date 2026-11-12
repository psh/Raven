---
status: open
---

# RavenTheme and theme files

`RavenTheme` at application level and inside a window, theme files, and `RavenTheme.colors`, for
the `themes` port and `notepad`'s View menu. Spec: [8: theming](../spec.md#phase-2-theming).

The accent, `saveTheme` and `themeNames` were decided in
[Theme extras the themes sample uses](archive/0077-theme-extras-the-themes-sample-uses.md).

**Depends on:** [Color, Shape and BorderStroke](0060-color-shape-and-borderstroke.md).

## Deliverables

- [ ] `RavenTheme(look: Look? = null, dark: Boolean? = null, theme: String? = null) { }` at
      application level emits no node: `look` and `dark` become one `Rlook_set`, `theme` uses
      `Rtheme_use`, and its CompositionLocals reach every window through
      `rememberCompositionContext`. In `singleWindowApplication`, the `RavenTheme` at the top of
      the content counts as application level.
- [ ] Inside a window, `RavenTheme(theme = ...)` gives its subtree that theme through an internal
      Box and `Rwidget_set_theme`. Setting `look` or `dark` there logs an error and does nothing.
- [ ] `Look`: `Raven`, `Glass`, `HighContrast`, `Windows`, `Macos`, `Gnome`, `Native` and
      `MacosGlass`.
- [ ] `loadTheme(path): String` and `loadThemeFromString(xml): String` (`Rtheme_load`,
      `Rtheme_load_from_memory`), valid only while Raven runs.
- [ ] `RavenTheme(accent = ...)` at application level through `Rtheme_set_accent`, applied again
      after each look or theme change, which resets it.
- [ ] `saveTheme(path, name): Boolean` through `Rtheme_save`, valid only while Raven runs.
- [ ] `RavenTheme.themeNames`, snapshot state from `Rtheme_get_count`/`_name_at`, refreshed after
      `loadTheme`, a look change, and Raven's reload of a theme file (its reload callback, kept
      internal).
- [ ] `RavenTheme.colors`: one `Color` per Raven colour role, read from the app theme with
      `Rtheme_get_color`, and recomposing readers when the look or theme changes.
- [ ] Tests: changing `look`, `dark` or `theme` changes Raven's; a subtree theme affects only its
      subtree; `RavenTheme.colors` follows an app-level change; the window-level error is logged; an accent survives a
      change of look; a saved theme loads back with its accent; `themeNames` lists a loaded theme.
