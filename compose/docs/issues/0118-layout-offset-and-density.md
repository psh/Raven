---
status: open
---

# Modifier.offset and LocalDensity

Two small layout pieces the samples need. Spec: [6. Layout and Modifiers](../spec.md#6-layout-and-modifiers).

**Depends on:** nothing in phase 3.

## Deliverables

- [ ] `Modifier.offset(x, y)` on the slot padding (left +x, right −x, top +y, bottom −y), adding to `padding` ([Small port gaps](archive/0107-small-port-gaps.md)).
- [ ] `LocalDensity` with a minimal `Density` (`density` from `Rwindow_get_scale`, `Dp.toPx()`, `Int.toDp()`) ([The editor's scene viewport](archive/0101-the-editors-scene-viewport.md)).
- [ ] Tests: an offset moves a widget without moving its neighbours; density matches the window's scale.
