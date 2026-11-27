---
status: closed
---

# samples/effects

Port Raven's `examples/effects` to `compose/samples/effects`: Raven's eight built-in effects as
cards, and the app's own `wave.spv` and `liquid.spv`. Spec:
[10: `samples/effects`](../../spec.md#samples-effects-phase-5). Planned in
[Designed samples: the three ports' plan](0205-designed-samples-the-three-ports-plan.md).

**Depends on:** nothing open. This is the first ticket of phase 5's implementation sequence.

## Deliverables

- [x] The module `samples/effects`, with a `main` and a `launch-effects` script as the other samples have.
- [x] A `Card(effect, caption, ...)` composable; the grid of four columns with aurora, mesh, plasma, grain, spotlight, shimmer, neon (on its dark background) and ripple over the card it runs across; the row of `wave.spv` and `liquid.spv` (backdrop) over mesh.
- [x] The build writes the path of Raven's `examples/effects` into the sample, as `samples/themes` does, and the `.spv` files are read from there.
- [x] Headless tests: each card has its effect and its style (colour, second colour, params); both of the app's shaders load.
- [x] `roadmap.md` and the samples list updated; the port gaps (none expected) on this ticket.

## Resolution

Built on 2026-11-26: `compose/samples/effects` (`Main.kt`, `launch-effects`, the build writing
`EXAMPLE_DIR`), registered in `settings.gradle.kts`, listed in `compose/README.md` and the roadmap.

- `Card(effect, caption, modifier, effectStyle, color)` stands for the XML's Card; the UniformGrid is
  two rows of four weighted cards, as every cell is the same 190 dp card.
- The app's shaders are `ShaderEffect.fromFile(..., animated = true)`, liquid with `backdrop = true`
  over a mesh card, inset 22 dp.
- Tests (3, passing): each card's built-in shader read back with `Rborder_get_effect`; both `.spv`
  files load from `EXAMPLE_DIR` headless; the three styles are the C example's colours and params.
  Raven gives no getter for a border's look, and `EffectCalls` is internal to foundation, so the
  styles are checked as the sample's values rather than on the widget.
- Checked by eye in the release build: the layout and every effect as the C example's.
- Port gaps: none.
