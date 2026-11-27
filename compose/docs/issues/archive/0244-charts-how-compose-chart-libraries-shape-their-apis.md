---
status: closed
wayfinder: research
parent: 0241
blocked_by: []
assignee: claude
---

# Charts: how Compose chart libraries shape their APIs

## Question

Compose has no charts. How do the well-known Compose chart libraries (Vico, compose-charts,
YCharts, Compose Multiplatform ones) shape their APIs: how data and series are given (models,
producers, lists), how axes, legends and styling are set, how interaction (a point selected, zoom,
scroll) is reported, and what state is hoisted? Facts for
[Charts: one model for Plot and Chart](0245-charts-one-model-for-plot-and-chart.md), set beside what
Raven's Plot (`Rplot_*`: series, axes, scales) and Chart (`Rchart_*`: pie, donut, gauge, heatmap,
sparkline) take.

## Resolution

Resolved on 2026-11-26 by research: the findings are on the local branch
`research/charts-compose-chart-apis` (commit `739a54b3`), in
`compose/docs/research/charts-how-compose-chart-libraries-shape-their-apis.md`; the branch is never
merged or pushed. Libraries read from source: Vico, Compose Charts, YCharts, KoalaPlot, AAY-chart.

- **Data**: immutable lists of data classes, replaced to update (Compose Charts, YCharts,
  AAY-chart, KoalaPlot); only Vico keeps a long-lived `CartesianChartModelProducer` updated by
  suspending transactions, which it diffs and animates. No library keys its series.
- **Axes**: Vico's axis objects in four edge slots; KoalaPlot's hoisted axis models
  (`rememberFloatLinearAxisModel`, owning `viewRange`, the only log axis); YCharts' builder of
  constants; flat flags elsewhere. Labels are formatted by lambdas everywhere.
- **Legends**: a slot on the chart (Vico), a composable the app lays out (KoalaPlot), or a flag.
- **Interaction**: callbacks with the item (`onSliceClick`, `onBarClick`); selection lives in the
  app's data. Only Vico and KoalaPlot hoist pan and zoom (`rememberVicoZoomState`, axis models);
  none has a view-changed callback.
- **Status**: YCharts archived and Android-only; KoalaPlot pre-1.0; Vico's pie experimental.
- **Raven** is retained and imperative: Plot's series by index with append, a rolling window and
  NaN gaps; four axes, linear, log or symlog, printf formats; Raven owns pan and zoom, its view
  callback carries no range; no point-selected callback, but drag lines. Chart is one widget of
  five kinds, Raven keeping the slice selection. Raven has no bar or column chart; every library has.
- **Open trade-offs** for the decision: diffing lists onto index-addressed series, or a producer /
  `PlotState` that fits Raven's streaming (the million-point example); the view left to Raven or
  hoisted through `ControlledValue`; controlled slice selection; a composable per kind or one
  `Chart(kind)`; printf formats or Kotlin lambdas; where drag lines, symlog and second axes fit;
  bar charts.
