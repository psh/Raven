---
status: closed
wayfinder: grilling
parent: 0241
blocked_by: [0244]
assignee: claude
---

# Charts: one model for Plot and Chart

## Question

How are Raven's Plot and Chart bound, together? Decide the composables (one per chart kind, or a
`Plot` and a `Chart(kind)`), the data model they share (series of points, values with labels),
how updates reach Raven without rebuilding the whole chart, styling against the theme, and what
interaction is reported. Uses
[Charts: how Compose chart libraries shape their APIs](0244-charts-how-compose-chart-libraries-shape-their-apis.md).
The answer is the spec section and its implementation tickets, with the `plot` and `charts`
ports.

## Resolution

Resolved on 2026-11-27, with the user, in three rounds: designed into
[spec section 8: Charts](../../spec.md#charts-phase-5), section 10's
[samples/plot](../../spec.md#samplesplot-phase-5) and [samples/charts](../../spec.md#samplescharts-phase-5),
and section 12.

- **One composable per kind**: `Plot`, `PieChart` (a `hole` for the donut), `Gauge`, `Heatmap`,
  `Sparkline`; the four Chart kinds share one node.
- **Plot's series in a keyed DSL** over `DoubleArray`s compared by reference, set only when they
  change; flat style parameters at Raven's defaults; drag lines in the same DSL, controlled.
- **Axes as `PlotAxis` objects**, printf formats, `range = null` to fit the data; **the view stays
  Raven's**, observed and moved through `PlotState` (`fit`, `setRange`); the legend and series
  visibility are Raven's.
- **The Chart kinds take lists of data classes**, compared by value; the pie's selected slice is
  controlled, the heatmap's click an event.
- **Bars out of scope**: Raven has none; filed as
  [Raven: a bar chart widget, proposed upstream](../0258-raven-a-bar-chart-widget-proposed-upstream.md).
- No Raven change and no new harness API; both ports without port gaps; the XML translator covers
  both widgets.

Filed as [Charts: Plot and PlotState](../0259-charts-plot-and-plotstate.md),
[samples/plot](../0260-samples-plot.md),
[Charts: PieChart, Gauge, Heatmap and Sparkline](../0261-charts-piechart-gauge-heatmap-and-sparkline.md)
and [samples/charts](../0262-samples-charts.md).
