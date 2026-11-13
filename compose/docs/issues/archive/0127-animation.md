---
status: closed
---

# Animation

compose-raven's own animation set, Raven's motions, and the rest of transitions. Spec: [8: Transitions and Island](../../spec.md#transitions-and-island-phase-3).

**Depends on:** nothing in phase 3.

## Deliverables

- [x] `animateFloatAsState`, `animateDpAsState`, `animateColorAsState`, `animateOffsetAsState`; `Animatable(Float)`; `spring` and `tween` with the standard easings; `RavenMotion` presets ([Animations beyond appear transitions](0103-animations-beyond-appear-transitions.md)).
- [x] `RavenAnimation.speed` and `.enabled` over Raven's settings, starting from reduced motion, obeyed by compose-raven's animations.
- [x] `AnimatedVisibility(visible, enter, exit)` (enter specs mapped to Raven transitions, `fadeOut` exits) and `TransitionRequester` with `Modifier.transitionRequester`.
- [x] Tests with `mainClock`: springs and tweens settle, speed and disabled animations, a requested transition plays.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 352 tests, 26 of them this ticket's
(`AnimationSpecTest` 8, `AnimationTest` 7, `AnimatedVisibilityTest` 5, and one more in
`AppearTransitionTest`).

- **The animation set**, in `raven.compose.animation.core` (`compose-raven-ui`):
  `animateFloatAsState`, `animateDpAsState`, `animateColorAsState`, `animateOffsetAsState`, and
  `Animatable(Float)` with `value`, `velocity`, `targetValue`, `isRunning`, `animateTo`, `snapTo` and
  `stop`. Each animated type is a vector of floats (`VectorAnimation.kt`); a spring is solved in
  closed form, as Compose's `SpringSimulation`, so a run is the same whatever frames sample it. A
  new target mid-way keeps the velocity, and a newer animation cancels the one running.
  `animate*AsState` starts at its first target without animating. `Offset` gains `times`, `div`
  and `unaryMinus`.
- **Specs**: `spring(dampingRatio, stiffness, visibilityThreshold)` and `tween(durationMillis,
  delayMillis, easing)`, with `FastOutSlowInEasing`, `LinearOutSlowInEasing`,
  `FastOutLinearInEasing`, `LinearEasing` and `CubicBezierEasing`, and Compose's `Spring` constants.
  `AnimationSpec` is covariant, so one `RavenMotion` spec serves every type.
- **`RavenMotion.Snappy | Smooth | Bouncy | Wobbly`**: Raven's response and damping
  (`src/widget/anim.c`) with `stiffness = (2π / response)²`.
- **`RavenAnimation.speed` and `.enabled`** are snapshot state that set `Rset_animation_speed` and
  `Rset_animations_enabled`. The host reads Raven's settings as it starts and once a frame, so they
  start from the system's reduced motion and follow it. compose-raven's animations scale their play
  time by `speed`, and jump to their targets while `enabled` is false, also when it is turned off
  mid-way.
- **The frame clock**: with a fixed animation time step, the host's frame clock now steps by it
  each frame rather than reading `Rget_time()`, so compose-raven's animations are as deterministic
  in tests as Raven's.
- **`AnimatedVisibility(visible, modifier, enter, exit)`**, in `raven.compose.animation`
  (`compose-raven-foundation`), shows its content in a Box. Enter is played as the nearest Raven
  transition: `fadeIn` Fade, `scaleIn` Pop (from smaller) or Zoom (from larger), `slideInVertically`
  FlyDown or FlyUp and `slideInHorizontally` FlyRight or FlyLeft, by the sign of the offset; with
  `+` a slide wins over a scale and a scale over a fade. Content visible from the start appears
  without it, as in Compose. Exit is a fade of the Box's alpha (`Rwidget_set_opacity`) with
  `fadeOut`'s spec, then removal; the other exits fall back to `fadeOut`'s default fade, as the
  spec says. Coming back mid-fade fades back in.
- **`TransitionRequester`** and `Modifier.transitionRequester(r)`: the node attaches the requester
  to its widget, and detaches it when the modifier drops it or the node is released; `play` calls
  `Rwidget_play_transition`, and does nothing while detached.
- **Harness**: `mainClock` with `currentTime`, `advanceTimeByFrame()`, `advanceTimeBy(ms)` and
  `autoAdvance`. An animation read by the composition recomposes every frame, so waiting for idle
  runs it to its end, as in Compose; with `autoAdvance = false`, waiting for idle recomposes with
  compose-raven's animations held (`RavenHost.holdsFrameTime`, internal API). Raven's own
  animations go on regardless, since its time step can't be held.
- **Tests**: easings and spring settling without a host (`AnimationSpecTest`); a tween taking its
  duration on the clock, a spring changing course, the other types, an `Animatable` kicked,
  cancelled, stopped and snapped, speed and disabled animations, and the settings following Raven
  (`AnimationTest`); enter mappings, exit fallbacks, the fade before removal, coming back mid-fade,
  and immediate removal with no exit or animations disabled (`AnimatedVisibilityTest`); a requester
  playing on its widget, then detached and released (`AppearTransitionTest`).
