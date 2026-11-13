---
status: closed
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: Paul Hawke
---

# Music's accent picker

## Question

`music`'s settings choose the accent with a `ColorPicker` in its popup form
(`Popup="true" Alpha="false"`), when it doesn't follow the cover. `ColorPicker` is one of phase 5's
specialist widgets, and `samples/themes` already ports with a row of swatches until then
([Theme extras the themes sample uses](0077-theme-extras-the-themes-sample-uses.md)).
Decide whether `music` does the same (a port gap completed in phase 5), or a slim
`ColorPicker(color, onColorChange, modifier, popup, alpha)` moves into phase 3, completing both
samples.

## Resolution

Decided 2026-11-12: a slim `ColorPicker` moves into phase 3. Raven's ColorPicker is a controlled
value widget no bigger than `ComboBox` (one colour, set without a callback, plus a few flags),
unlike the model-holding specialist widgets it was grouped with; Timeline, NodeGraph, WebView and
BlockEditor stay phase 5. Written into spec section 8 ([ColorPicker](../../spec.md#colorpicker-phase-3)),
section 9 and section 12.

- **Signature**: `ColorPicker(color, onColorChange, modifier, popup = false, alpha = true,
  compact = false, swatches = emptyList(), enabled = true)` in `compose-raven-foundation`
  (`raven.compose.foundation`), next to `ComboBox`. Controlled: a refused change reverts on the
  next frame; `onColorChange` fires on every change, during a drag too, as `Slider`'s does.
  `previous` is left out (a popup picker shows the colour it opened with), and `Numbers` stays
  Raven's default.
- **Tests**: the harness gains `performColorChange(color)`, through Raven's input: it opens the
  popup if there is one and types the colour's hex code into the picker's code field.
- **Samples**: `music` uses `ColorPicker(popup = true, alpha = false)`, as its C example does, and
  so does `themes` in phase 3, in place of its stand-in swatches: that port gap moved from phase 5
  to phase 3 ([samples/themes](0071-samples-themes.md), and the sample's own comment).
- **Fog and roadmap**: `ColorPicker` left the specialist widgets on
  [compose-raven phase 3 spec](../0023-compose-raven-phase-3-spec.md#not-yet-specified) and the
  roadmap's Later section, and joined its phase 3 list.
