---
status: open
---

# Surface, FlowRow and Expander

Three containers for the `components` port. Spec:
[8: Surface, FlowRow and Expander](../spec.md#phase-2-surface-flowrow-and-expander).

**Depends on:** [Color, Shape and BorderStroke](archive/0060-color-shape-and-borderstroke.md).

## Deliverables

- [ ] `Surface(modifier, shape, color, border, shadowElevation) { }` over Border, its content in an
      internal Box. `color = Unspecified` keeps the theme's panel colour; `shape` sets the corner
      radius, `border` uses `Rborder_set_outline`, and `shadowElevation` becomes a shadow with
      blur = elevation and y offset = elevation / 2. Inner padding is the content's own
      `Modifier.padding`.
- [ ] `FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(x)) { }` over WrapBox, `x`
      being the gap both between items and between lines.
- [ ] `Expander(title, expanded, onExpandedChange, modifier) { }`, controlled, its content in an
      internal Box.
- [ ] Tests: Surface's colour, shape, border and shadow follow their parameters and return to the
      theme's when unset; FlowRow wraps onto a second line when narrow; an ignored Expander toggle
      reverts.
