---
status: closed
---

# ColorPicker

Raven's colour picker as a controlled composable. Spec: [8: ColorPicker](../../spec.md#colorpicker-phase-3).

**Depends on:** nothing in phase 3.

## Deliverables

- [x] `ColorPicker(color, onColorChange, modifier, popup, alpha, compact, swatches, enabled)`, controlled ([Music's accent picker](0100-musics-accent-picker.md)).
- [x] Harness: `performColorChange(color)`, typing the hex code into the picker (its popup opened first).
- [x] Tests: a change reported, a refused change reverting, the popup form.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 360 tests, 8 of them `ColorPickerTest`'s.

- **`ColorPicker(color, onColorChange, modifier, popup = false, alpha = true, compact = false,
  swatches = emptyList(), enabled = true)`**, in `raven.compose.foundation` (`ColorPicker.kt`), as
  the spec has it. The colour is a controlled value, `ColorPickerColor`, and Raven's changed
  callback an event that marks it, `ColorPickerChanged` (both in `Binding.kt`), so a change the app
  doesn't take reverts on the next frame, as `Slider`'s does. Raven keeps the colour as hue,
  saturation and value; what it reads back rounds to the same 8-bit `Color`, so the composed
  colour isn't written again every frame. With `alpha = false` the composed colour is shown
  opaque, as Raven shows it, for the same reason. New `swatches` (by value) replace the old ones.
  A popup picker's changes come from the picker inside its popup; Raven passes them to the swatch,
  which calls back. `Color.Unspecified` is refused.
- **Harness: `performColorChange(color)`** types the colour's code (`RRGGBB`, or `RRGGBBAA` when
  not opaque) into the picker's code input and presses Enter. A popup picker is opened first with
  the keyboard (focus, then Enter, which is what it answers to) and its popup closed after; the
  code input is then the one in the popup's picker. A compact picker shows no code, and fails.
  Raven takes a colour from every keystroke, and a prefix of a code can be a colour (`F00` of
  `F00000`), so the code is typed after an `x` that is then deleted: the picker sees one colour,
  and reports it once.
- **Tests** (`ColorPickerTest`): a change reported once and taken, with opacity too; a refused
  change reverting; the app's change shown without a callback; the popup form opening, reporting
  and closing; `alpha = false` showing the colour opaque; a compact picker failing
  `performColorChange` without a change; swatches replaced; `enabled`.
