---
status: closed
---

# Color, Shape and BorderStroke

The graphics types every other phase 2 family takes: `Color`, `Shape` and `BorderStroke` in
`raven.compose.ui.graphics`, and `Text(color)`, the first parameter to use one. Spec:
[8: Surface, FlowRow and Expander](../spec.md#phase-2-surface-flowrow-and-expander),
[8. Component vocabulary](../spec.md#8-component-vocabulary).

**Depends on:** nothing open (phase 1's [Text, buttons and the first finders](archive/0047-text-buttons-and-first-finders.md)).

## Deliverables

- [x] `Color` as a value class: `Color(0xFF3B82F6)`, `Color(red, green, blue, alpha)`, and the
      constants `Black`, `White`, `Gray`, `Red`, `Green`, `Blue`, `Yellow`, `Transparent` and
      `Unspecified`, with an internal conversion to Raven's colour.
- [x] `Shape`, sealed: `RectangleShape`, `RoundedCornerShape(all)`,
      `RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)` and `CircleShape` (a large
      radius Raven clamps).
- [x] `BorderStroke(width: Dp, color: Color)`.
- [x] `Text(color = ...)` uses `Rlabel_set_color`; `Unspecified` leaves the theme's colour, and a
      change back to `Unspecified` restores it.
- [x] Tests: the conversions round-trip, and a `Text` follows a colour that changes and returns to
      `Unspecified`.

## Resolution

Done on macOS. 162 tests pass across the build, 7 of them new in compose-raven-ui and 1 in
foundation.

- **`Color`**, in `raven.compose.ui.graphics` (`Color.kt`), is a value class over a `ULong`
  holding `0xAARRGGBB` in its low 32 bits, 8 bits a channel, as Raven stores colours
  (`rvn_color_t`). `Unspecified` sets bit 32, so it is no colour, not even `Transparent`. Its
  constructor is internal; apps use `Color(0xFF3B82F6)` (a `Long`, bits above 32 ignored) or
  `Color(red, green, blue, alpha = 1f)`, which clamps and rounds each channel as `rvn_rgba` does,
  so a colour read back from Raven is the colour given. It has `red`, `green`, `blue` and `alpha`,
  `isSpecified`/`isUnspecified`, and the constants with Compose's values (`Gray` is `0xFF888888`).
- **Raven conversion**: `toRaven { r, g, b, a -> }` passes the channels to a Raven setter and fails
  on `Unspecified`, whose meaning (keep the theme's) is each caller's to apply; `ravenColor { r, g,
  b, a -> }` reads one back from a getter's four pointers, for `Rwidget_get_color` now and
  `Rtheme_get_color` in theming. Both are `@InternalComposeRavenApi`, since foundation uses them:
  the ticket's "internal" read as compose-raven's internal API.
- **`Shape`** is a sealed class: `RectangleShape` (a data object), and
  `RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)` (a data class of `Dp`s) with a
  one-radius constructor. `CircleShape` is `RoundedCornerShape(100_000.dp)`; Raven's painter clamps
  each radius to half the shorter side (`src/render/draw.c`). Start and end are left and right, as
  in `Alignment`, since right-to-left isn't exposed. Converting a shape to
  `Rborder_set_corner_radius` is left to `Surface` (0064), the first user.
- **`BorderStroke(width: Dp, color: Color)`** is a data class; `Surface` will pass it to
  `Rborder_set_outline`.
- **`Text(text, modifier, color = Color.Unspecified, fontSize)`**: a specified colour uses
  `Rlabel_set_color`, and `Unspecified` calls `Rwidget_clear_color(R_COLOR_TEXT)`, so a change back
  restores the theme's colour (and one inherited from an ancestor). `color` comes before `fontSize`,
  as in the spec's row; every existing caller names `fontSize`.
- **Tests** (`GraphicsTest` in compose-raven-ui, `ComponentsTest` in foundation):
  - a hex colour has its channels and prints as hex;
  - every constant round-trips through its channels;
  - channels round to 8 bits and clamp as Raven does;
  - `Unspecified` is no colour, differs from `Transparent`, and has no Raven colour;
  - colours round-trip through Raven (`Rlabel_set_color`, then `Rwidget_get_color`);
  - a one-radius `RoundedCornerShape` and `CircleShape` round every corner alike;
  - a `BorderStroke` compares by width and colour;
  - a `Text` follows a colour that changes, and returns to the theme's colour at `Unspecified`.
