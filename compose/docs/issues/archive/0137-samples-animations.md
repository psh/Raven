---
status: closed
---

# samples/animations

Port Raven's `examples/animations`: transitions, springs, cards that come and go, the island. Spec: [10. Samples](../../spec.md#10-samples).

**Depends on:** [Generic input, focus and menus](0113-generic-input-focus-and-menus.md), [Custom drawing, viewports and effects](0124-custom-drawing-viewports-and-effects.md), [Animation](0127-animation.md), [Island](0128-island.md).

## Deliverables

- [x] `samples/animations` with the C example's look: the transition list, the stage card, the springs, the cards and the island.
- [x] Transitions played on demand with `TransitionRequester`, staggered for Play them all; cards popping in with `appearTransition`.
- [x] The springs as a `Canvas` driven by four `Animatable`s with `RavenMotion` specs, following the pointer and kicked with an initial velocity.
- [x] Speed and Reduce motion through `RavenAnimation.speed` and `.enabled`.
- [x] Runs from Gradle, documented in `compose/README.md`.
- [x] A headless test with `mainClock`: a transition requested, cards added and removed, springs settling.

## Gaps

None: its gaps were decided into phase 3 on [compose-raven phase 3 sample gaps](../0095-compose-raven-phase-3-sample-gaps.md).

## Resolution

Done on macOS. `./gradlew build allTests` passes: 512 tests, 4 of them `AnimationsTest`'s. The
sample was launched and looks as the C example does: the transition list, the stage card, the
springs, the cards and the island.

- **`samples/animations`** (`settings.gradle.kts`, `launch-animations`, documented in
  `compose/README.md`): one `Animations` state, remembered by `AnimationsWindow`, shared by the
  window's content and its island.
- **Transitions**: each button of the list plays its transition on the stage card through a
  `TransitionRequester` and shows its name there; Play them all plays a transition on each card of
  the list, 60 ms apart, through each card's own requester, and drops the stage card. The panels
  arrive with `appearTransition` and staggered delays, as the C example's `Appear` and
  `AppearDelay`.
- **Cards**: a keyed list, each card popping in with `appearTransition(Pop)`; the first three are
  staggered. Adding or removing one unfolds the island for 1.6 s (`Risland_expand_for` in the C
  example is an `expanded` state and a `LaunchedEffect`), whose folded side counts the cards.
- **Springs**: a `Canvas` drawing four balls from four `Animatable`s, each animated to the pointer's
  place with one of `RavenMotion`'s specs, so only the way they get there differs, and a glow from
  `Animatable`s on `Bouncy`; a click kicks them with an initial velocity of 1400 dp/s added to
  their own. The C example's `Rdraw_spring` is driven from its paint callback; here the
  animations run on the frame clock and the Canvas, read-observed, draws what they read.
- **Tuning**: the island's speed slider and reduce-motion switch set `RavenAnimation.speed` and
  `.enabled`, which both Raven's animations and compose-raven's obey.
- **Tests** (`AnimationsTest`, headless, on `mainClock`): a transition requested and Play them all;
  cards added and removed, with the island's count and unfolding; the springs settled in the
  middle, following the pointer each its own way, kicked past where they rest and settling again;
  the speed slider and the reduce-motion switch.
