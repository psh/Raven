---
status: closed
---

# Theming extras

The colour, metric and header additions this map decided. Spec: [8: theming](../../spec.md#phase-2-theming), [8: menus and the header bar](../../spec.md#phase-2-menus-and-the-header-bar).

**Depends on:** [Typography in RavenTheme](0117-typography-in-raventheme.md).

## Deliverables

- [x] Explicit hovered and pressed colours on `ButtonDefaults.buttonColors`, `CheckboxDefaults.colors`, `RadioButtonDefaults.colors` and `SliderDefaults.colors`, each used as given when set ([Music's class-styled themes](0099-musics-class-styled-themes.md)).
- [x] `TextFieldDefaults.colors(unfocusedIndicatorColor)` over `CONTROL_BORDER` and `BORDER`.
- [x] `RavenTheme(metrics: Map<ThemeMetric, Float>)` at application level, applied again after look and theme changes; a changed `FontSize` moves `Typography.fromTheme()`'s base ([The editor's run-time extras](0102-the-editors-run-time-extras.md)).
- [x] `HeaderBar(background)` over `Rheader_bar_set_background` ([DockArea and HeaderBar extras](0105-dockarea-and-headerbar-extras.md)).
- [x] Tests: explicit and derived shades, a frameless TextField, metrics surviving a look change, a header bar without background.

## Resolution

Done on macOS. 365 tests pass across the build, 6 of them new (`ThemingExtrasTest`, in
compose-raven-foundation). No Raven change, and no deviation from the spec.

- **Explicit shades** (`ComponentStyle.kt`): `WidgetColors` now takes a hovered or pressed role
  given beside its `ACCENT` or `CONTROL` base. For each shade it sets the colour given, else the
  shade derived from a base colour, else clears it, so each is used on its own, with or without the
  base: a transparent button keeps a faint hover and the theme's press. Dropping a given shade goes
  back to the derived one. The new parameters, last in each, are
  `ButtonDefaults.buttonColors(hoveredContainerColor, pressedContainerColor)` (the container role's
  shades, `ACCENT_` or `CONTROL_`), `CheckboxDefaults.colors(hoveredCheckedColor,
  pressedCheckedColor)`, `RadioButtonDefaults.colors(hoveredSelectedColor, pressedSelectedColor)` and
  `SliderDefaults.colors(hoveredActiveTrackColor, pressedActiveTrackColor)`, all over
  `ACCENT_HOVERED`/`_PRESSED` but the outlined and text buttons'.
- **`TextFieldDefaults.colors(unfocusedIndicatorColor)`**, last, sets both `CONTROL_BORDER` and
  `BORDER`, since Raven's looks draw a field's frame in one or the other.
- **`ThemeMetric`** (`compose-raven-ui`, `theme/ThemeMetric.kt`) mirrors Raven's 20 metrics, the
  glass ones and `BlurRadius` included. **`RavenTheme(metrics)`**, after `typography`: `HostTheme`
  sets the metrics that changed with `Rtheme_set_metric`, and all of them again after a look or
  theme change. Raven can't reset a theme metric, so the first time compose-raven sets one it
  remembers Raven's own value, and a metric left out of the map again gets it back; a look or
  theme change forgets those values, since it resets the metrics. In a window, metrics log an error,
  as the other app-wide settings do. `refresh()` follows every change, so a new `FontSize` resizes
  `Typography.fromTheme()`'s roles. The spec now says what a dropped metric does.
- **`HeaderBar(background: Boolean = true)`**, before `content`, over `Rheader_bar_set_background`.
  Raven has no getter for it, so the test only checks both values reach Raven without an error.
- **Tests**: a button's derived shades, a given one beside a derived one, and a faint hover given
  alone with the theme's press; a given shade dropped goes back to the derived one; given shades on
  a Checkbox, a RadioButton without a selected colour, and a Slider; a frameless TextField and a
  framed one; metrics applied, kept through a look change, moving the typography, and a dropped
  font size going back to the look's; a header bar without background.
