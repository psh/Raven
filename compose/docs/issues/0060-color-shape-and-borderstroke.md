---
status: open
---

# Color, Shape and BorderStroke

The graphics types every other phase 2 family takes: `Color`, `Shape` and `BorderStroke` in
`raven.compose.ui.graphics`, and `Text(color)`, the first parameter to use one. Spec:
[8: Surface, FlowRow and Expander](../spec.md#phase-2-surface-flowrow-and-expander),
[8. Component vocabulary](../spec.md#8-component-vocabulary).

**Depends on:** nothing open (phase 1's [Text, buttons and the first finders](archive/0047-text-buttons-and-first-finders.md)).

## Deliverables

- [ ] `Color` as a value class: `Color(0xFF3B82F6)`, `Color(red, green, blue, alpha)`, and the
      constants `Black`, `White`, `Gray`, `Red`, `Green`, `Blue`, `Yellow`, `Transparent` and
      `Unspecified`, with an internal conversion to Raven's colour.
- [ ] `Shape`, sealed: `RectangleShape`, `RoundedCornerShape(all)`,
      `RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)` and `CircleShape` (a large
      radius Raven clamps).
- [ ] `BorderStroke(width: Dp, color: Color)`.
- [ ] `Text(color = ...)` uses `Rlabel_set_color`; `Unspecified` leaves the theme's colour, and a
      change back to `Unspecified` restores it.
- [ ] Tests: the conversions round-trip, and a `Text` follows a colour that changes and returns to
      `Unspecified`.
