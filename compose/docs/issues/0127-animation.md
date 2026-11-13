---
status: open
---

# Animation

compose-raven's own animation set, Raven's motions, and the rest of transitions. Spec: [8: Transitions and Island](../spec.md#transitions-and-island-phase-3).

**Depends on:** nothing in phase 3.

## Deliverables

- [ ] `animateFloatAsState`, `animateDpAsState`, `animateColorAsState`, `animateOffsetAsState`; `Animatable(Float)`; `spring` and `tween` with the standard easings; `RavenMotion` presets ([Animations beyond appear transitions](archive/0103-animations-beyond-appear-transitions.md)).
- [ ] `RavenAnimation.speed` and `.enabled` over Raven's settings, starting from reduced motion, obeyed by compose-raven's animations.
- [ ] `AnimatedVisibility(visible, enter, exit)` (enter specs mapped to Raven transitions, `fadeOut` exits) and `TransitionRequester` with `Modifier.transitionRequester`.
- [ ] Tests with `mainClock`: springs and tweens settle, speed and disabled animations, a requested transition plays.
