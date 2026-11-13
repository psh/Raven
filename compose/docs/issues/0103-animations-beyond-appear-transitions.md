---
status: open
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: null
---

# Animations beyond appear transitions

## Question

`animations` does three things phase 3 doesn't describe. It plays a transition on a widget that is
already shown, on demand and staggered (`Rwidget_play_transition`), where phase 3 only plays one
when a widget appears. It draws four springs with Raven's motions (`RSpring`, `Rdraw_spring`, the
Snappy, Smooth, Bouncy and Wobbly `RMotion`s) and kicks them with a velocity (`Rspring_kick`),
where the spec says only that `animate*AsState` and `Animatable` are "compose-raven's own small
set". And it changes animation speed and turns animations off app-wide (`Rset_animation_speed`,
`Rset_animations_enabled`), beside the host following the system's reduced motion
([Accessibility semantics](archive/0026-accessibility-semantics.md)). Decide an on-demand
transition, the contents of that small set (specs, the Raven motions as spring specs, an initial
velocity), and the app-wide animation settings.
