---
status: open
---

# samples/animations

Port Raven's `examples/animations`: transitions, springs, cards that come and go, the island. Spec: [10. Samples](../spec.md#10-samples).

**Depends on:** [Generic input, focus and menus](archive/0113-generic-input-focus-and-menus.md), [Custom drawing, viewports and effects](0124-custom-drawing-viewports-and-effects.md), [Animation](0127-animation.md), [Island](0128-island.md).

## Deliverables

- [ ] `samples/animations` with the C example's look: the transition list, the stage card, the springs, the cards and the island.
- [ ] Transitions played on demand with `TransitionRequester`, staggered for Play them all; cards popping in with `appearTransition`.
- [ ] The springs as a `Canvas` driven by four `Animatable`s with `RavenMotion` specs, following the pointer and kicked with an initial velocity.
- [ ] Speed and Reduce motion through `RavenAnimation.speed` and `.enabled`.
- [ ] Runs from Gradle, documented in `compose/README.md`.
- [ ] A headless test with `mainClock`: a transition requested, cards added and removed, springs settling.

## Gaps

None: its gaps were decided into phase 3 on [compose-raven phase 3 sample gaps](0095-compose-raven-phase-3-sample-gaps.md).
