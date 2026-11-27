---
status: open
---

# samples/plot

Port Raven's `examples/plot`. Spec: [10: samples/plot](../spec.md#samplesplot-phase-5). Planned in [Charts: one model for Plot and Chart](archive/0245-charts-one-model-for-plot-and-chart.md).

**Depends on:** [Charts: Plot and PlotState](0259-charts-plot-and-plotstate.md).

## Deliverables

- [ ] `samples/plot`: three live series of 600 points (one on Y2); the Bode plot (log X, fixed locked Y ranges, `"%g dB"`, a cutoff drag line); the million-point random walk on symlog Y, its view read through `PlotState`.
- [ ] A headless test checking each plot.
- [ ] No port gaps; the roadmap's sample table and `api-coverage.md` updated.
