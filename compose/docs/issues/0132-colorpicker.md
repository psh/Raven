---
status: open
---

# ColorPicker

Raven's colour picker as a controlled composable. Spec: [8: ColorPicker](../spec.md#colorpicker-phase-3).

**Depends on:** nothing in phase 3.

## Deliverables

- [ ] `ColorPicker(color, onColorChange, modifier, popup, alpha, compact, swatches, enabled)`, controlled ([Music's accent picker](archive/0100-musics-accent-picker.md)).
- [ ] Harness: `performColorChange(color)`, typing the hex code into the picker (its popup opened first).
- [ ] Tests: a change reported, a refused change reverting, the popup form.
