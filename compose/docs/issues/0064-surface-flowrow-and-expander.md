---
status: closed
---

# Surface, FlowRow and Expander

Three containers for the `components` port. Spec:
[8: Surface, FlowRow and Expander](../spec.md#phase-2-surface-flowrow-and-expander).

**Depends on:** [Color, Shape and BorderStroke](archive/0060-color-shape-and-borderstroke.md).

## Deliverables

- [x] `Surface(modifier, shape, color, border, shadowElevation) { }` over Border, its content in an
      internal Box. `color = Unspecified` keeps the theme's panel colour; `shape` sets the corner
      radius, `border` uses `Rborder_set_outline`, and `shadowElevation` becomes a shadow with
      blur = elevation and y offset = elevation / 2. Inner padding is the content's own
      `Modifier.padding`.
- [x] `FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(x)) { }` over WrapBox, `x`
      being the gap both between items and between lines.
- [x] `Expander(title, expanded, onExpandedChange, modifier) { }`, controlled, its content in an
      internal Box.
- [x] Tests: Surface's colour, shape, border and shadow follow their parameters and return to the
      theme's when unset; FlowRow wraps onto a second line when narrow; an ignored Expander toggle
      reverts.

## Resolution

Done on macOS. 178 tests pass across the build, 6 of them new in foundation, and Raven's
`test_layout` gains one.

- **A Raven change** (its own commit): Border had setters but no getters for its outline, corners
  and shadow, so nothing could check them. `Rborder_get_outline`, `Rborder_get_corner_radius` and
  `Rborder_get_shadow` read them back; the corners are those it is drawn with (its own, else a
  style's `R_METRIC_CORNER_RADIUS`, else 0). Any pointer may be NULL, and another widget reads as
  nothing, with an error. `test_border_getters` covers them; the bindings are regenerated and the
  reference lists them. Spec section 11 doesn't list this change yet.
- **`Surface(modifier, shape, color, border, shadowElevation) { }`**, in `raven.compose.foundation`
  (`Containers.kt`), is a Border with a Box content node. `color` uses `Rborder_set_background`,
  and `Unspecified` clears the border's own `R_COLOR_PANEL`, so the theme's shows again. `shape`
  uses `Rborder_set_corner_radius`, start and end being left and right. `RectangleShape` is
  explicit square corners, which is also a Border's default; it then overrides a style's radius, as
  a Compose `RectangleShape` would. `border = null` is outline width 0, and a `BorderStroke` with an
  unspecified colour takes the theme's `R_COLOR_BORDER` when it is set. `shadowElevation` is blur =
  elevation and y offset = elevation / 2, and 0 is none, so a theme's Border shadow shows again.
  There is no `contentPadding`: the content's own `Modifier.padding` is its slot padding inside the
  Border. The content lambda has no scope, as Compose's Surface has none.
- **`FlowRow(modifier, horizontalArrangement = Arrangement.Start) { }`**, in
  `raven.compose.foundation.layout`, is a WrapBox whose `Rpanel_set_spacing` is the arrangement's
  spacing, so `spacedBy(x)` is the gap between items and between lines. The default is
  `Arrangement.Start` (no gap), as `Row`'s is: the spec's `spacedBy(x)` reads as the usual call,
  not a default. Its content has no scope, since WrapBox gives each child its own measured size, so
  `weight` and per-line alignment would mean nothing.
- **`Expander(title, expanded, onExpandedChange, modifier) { }`**, in `raven.compose.foundation`,
  is an Expander with a Box content node. The binding layer gains `ExpanderExpanded`
  (`Rexpander_is_expanded`/`_set_expanded`) and the `ExpanderChanged` event, which marks it, so a
  toggle the app ignores reverts at the end of the frame. `onExpandedChange` isn't nullable, as in
  the spec's row.
- **Tests** (`ContainersTest`, and `LayoutsTest` for FlowRow):
  - a Surface's colour, corners, outline and shadow follow their parameters, and return to the
    theme's colour, square corners, no outline and no shadow when unset;
  - `CircleShape` rounds every corner alike, and an unspecified stroke colour is the theme's
    border colour;
  - a Surface's inner padding is its content's `Modifier.padding`;
  - a FlowRow keeps three items on one line when wide, and wraps the third under the tallest of the
    first line, the same gap down, when narrowed;
  - an Expander the app ignores calls back once and reverts to closed;
  - an Expander the app follows opens, shows its content, and closes when the app says so.
