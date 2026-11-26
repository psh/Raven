---
status: open
---

# samples/effects

Port Raven's `examples/effects` to `compose/samples/effects`: Raven's eight built-in effects as
cards, and the app's own `wave.spv` and `liquid.spv`. Spec:
[10: `samples/effects`](../spec.md#samples-effects-phase-5). Planned in
[Designed samples: the three ports' plan](archive/0205-designed-samples-the-three-ports-plan.md).

**Depends on:** nothing open. This is the first ticket of phase 5's implementation sequence.

## Deliverables

- [ ] The module `samples/effects`, with a `main` and a `launch-effects` script as the other samples have.
- [ ] A `Card(effect, caption, ...)` composable; the grid of four columns with aurora, mesh, plasma, grain, spotlight, shimmer, neon (on its dark background) and ripple over the card it runs across; the row of `wave.spv` and `liquid.spv` (backdrop) over mesh.
- [ ] The build writes the path of Raven's `examples/effects` into the sample, as `samples/themes` does, and the `.spv` files are read from there.
- [ ] Headless tests: each card has its effect and its style (colour, second colour, params); both of the app's shaders load.
- [ ] `roadmap.md` and the samples list updated; the port gaps (none expected) on this ticket.
