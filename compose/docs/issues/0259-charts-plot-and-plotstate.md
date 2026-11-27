---
status: open
---

# Charts: Plot and PlotState

Bind Raven's Plot as `Plot { series(...); dragLine(...) }` with `PlotAxis` and a `PlotState`.
Spec: [8: Charts](../spec.md#charts-phase-5) and section 12. Planned in [Charts: one model for Plot and Chart](archive/0245-charts-one-model-for-plot-and-chart.md).

**Depends on:** [Overlays and pagers: SnackbarHost](0257-overlays-and-pagers-snackbarhost.md), the ticket before it in phase 5's implementation sequence.

## Deliverables

- [ ] `Plot(...)` over `Rplot_*` with `title`, the four `PlotAxis` parameters, `legend` and the crosshair, mouse text and tooltips.
- [ ] The `series(key, ...)` DSL: arrays compared by reference and set only when changed, keys mapped to Raven's shifting indices, the style parameters, `axes`.
- [ ] `dragLine(key, axis, value, onValueChange, label, color)`, controlled.
- [ ] `PlotAxis`: scale, threshold, printf format, `range` (null fits, a range applied when it changes), grid, inverted, locked.
- [ ] `PlotState`: observable visible ranges read on the view callback, `fit()`, `setRange(axis, range)`.
- [ ] Tests: data set only on a new array, series added, removed and reordered by key, a drag line moved and refused, the view read after a callback, `fit` and `setRange`.
- [ ] The XML translator's `<Plot>`, `<Series>`, `<Scatter>` and `<DragLine>`.
- [ ] The spec's sections checked against what was built; `api-coverage.md` updated (the Plot row and the numbers).
