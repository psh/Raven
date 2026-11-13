---
status: closed
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: Paul Hawke
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

## Resolution

Decided 2026-11-12. Written into spec section 8 ([Transitions and Island](../../spec.md#transitions-and-island-phase-3))
and section 12. All in `compose-raven-foundation`; tests need nothing new, since `mainClock` and
the fixed animation time step drive these too.

- **On demand**: `TransitionRequester` (`raven.compose.animation`), in the manner of
  `FocusRequester`: `Modifier.transitionRequester(r)`, `r.play(transition, delay)`, over
  `Rwidget_play_transition`. Chosen over a declarative `playTransition(trigger)`, which needs a
  counter to replay the same transition twice. `animations`' "Play them all" staggers the delays.
- **The animation set** (`raven.compose.animation.core`), on compose-raven's frame clock:
  `animateFloatAsState`, `animateDpAsState`, `animateColorAsState`, `animateOffsetAsState`;
  `Animatable(Float)` (`value`, `velocity`, `targetValue`, `isRunning`, `animateTo(target, spec,
  initialVelocity)`, `snapTo`, `stop`); `spring(dampingRatio, stiffness)` (the default) and
  `tween(durationMillis, delayMillis, easing)` with the four standard easings. Left out:
  `updateTransition`, keyframes, infinite transitions, `Animatable` of other types.
- **`RavenMotion.Snappy | Smooth | Bouncy | Wobbly`**: spring specs from Raven's own motions
  (`src/widget/anim.c`: response 0.16 / 0.30 / 0.38 / 0.50 s, damping 0.90 / 1.0 / 0.52 / 0.32;
  `stiffness = (2π / response)²`). `Rspring_kick` is Compose's `initialVelocity`; a new target keeps
  the current velocity, as the springs track needs.
- **App-wide**: `RavenAnimation.speed` and `RavenAnimation.enabled`, snapshot-backed, over
  `Rset_animation_speed` and `Rset_animations_enabled`; they start from the system's reduced
  motion, and compose-raven's own animations obey them as Raven's do (speed scales time, as
  `MotionDurationScale`; disabled snaps).
