---
status: closed
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: Paul Hawke
---

# Animated effects and continuous redraw

## Question

`music`'s backdrop, album hero and artist card are animated shader effects whose colours follow the
cover, which can stop moving (`EffectAnimate`) and never follow the pointer (`EffectPointer="false"`).
Phase 3's `Surface(effect, effectStyle)` has neither setting. `music` and `glass` also turn on
`Rwindow_set_continuous_redraw` while something moves. Decide `EffectStyle`'s animate and pointer
settings, and whether continuous redraw is the host's business (frame awaiters already ask for
frames; does Raven animate an effect without continuous redraw?) or a `Window` parameter.

## Resolution

Decided 2026-11-12. Written into spec section 8
([Custom drawing, viewports and effects](../../spec.md#custom-drawing-viewports-and-effects-phase-3)).

- **`EffectStyle`** gains `animate: Boolean? = null` (`RShaderLook.animate`: null moves as the shader
  does, `true`/`false` force it; `music`'s "Animate the background") and `pointer: Boolean = true`
  (`music` turns it off). The `hover`/`press` overrides (Raven eases them itself) and an effect
  texture are left out; the corner radius is `Surface(shape)`.
- **Colours that follow the cover**: a changed `EffectStyle` calls `Rborder_set_effect` again, which
  only replaces the look (no per-border start time), so the motion carries on unbroken.
- **No continuous redraw API**: shaders made `R_SHADER_ANIMATED` (Raven's animated built-ins) keep
  their window drawing while shown; compose-raven's animations and `withFrameNanos` loops keep
  frames coming through the frame clock; a `Canvas` reading changing state invalidates itself. The
  C examples turn continuous redraw on only for want of a frame clock. The ports' visualizer
  (`music`) and playing timer (`glass`) run from `withFrameNanos` loops while playing.
