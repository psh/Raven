---
status: open
---

# Charts: PieChart, Gauge, Heatmap and Sparkline

Bind Raven's Chart as four composables over one node. Spec: [8: Charts](../spec.md#charts-phase-5)
and section 12. Planned in [Charts: one model for Plot and Chart](archive/0245-charts-one-model-for-plot-and-chart.md).

**Depends on:** [samples/plot](0260-samples-plot.md).

## Deliverables

- [ ] One node over `Rchart_*`, its kind set by the composable; lists compared by value, items given to Raven by index.
- [ ] `PieChart(slices, selectedSlice, onSelectedSliceChange, hole, centerText, centerCaption, title, format, legend)`: the donut from `hole`; the selection controlled.
- [ ] `Gauge(value, range, bands, title, format, color)`, `Heatmap(values, rowLabels, columnLabels, colorScale, range, onCellClick, title, format)`, `Sparkline(values, range, fill, lastPointMarker, color)`.
- [ ] Tests: slices changed in place, the selection reported and refused, a gauge's bands, heatmap cells and a click, a sparkline's values.
- [ ] The XML translator's `<Chart Kind=...>` with `<Slice>`, `<Band>` and `<Row>`.
- [ ] The spec's sections checked against what was built; `api-coverage.md` updated (the Chart row and the numbers).
