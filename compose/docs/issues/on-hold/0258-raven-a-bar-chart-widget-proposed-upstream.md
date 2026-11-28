---
status: on-hold
---

# Raven: a bar chart widget, proposed upstream

Raven has no bar or column chart, the one chart kind every Compose chart library has
([Charts: how Compose chart libraries shape their APIs](../archive/0244-charts-how-compose-chart-libraries-shape-their-apis.md)).
Design and build one in Raven's C, as Plot and Chart are built (retained, index-addressed, themed,
values gliding as they change), and propose it to Raven's `main`. compose-raven binds it later, in
a map of its own: [compose-raven phase 5 widgets](../maps/0241-compose-raven-phase-5-widgets.md) rules bars
out of its scope.

> **On hold (2026-11-27).** Nothing is released beyond the local machine for now, and
> opening a pull request on Raven's `main` publishes it. The widget itself can be built on
> `compose-raven` meanwhile.

## Deliverables

- [ ] A design, recorded here before building: a kind of `Chart` (`R_CHART_BAR`) or a widget of its own; vertical columns and horizontal bars; one series or several, grouped or stacked; category labels, a value axis with a printf format, the legend, and a click on a bar.
- [ ] The widget in C with its header documentation, XML (`<Chart Kind="Bar">` or its own element), and the themes' colours.
- [ ] A C test, and an example in `examples/` (or a section of `examples/charts`).
- [ ] The bindings regenerated and the reference updated, in one commit on `compose-raven`.
- [ ] The pull request to Raven's `main`, once releasing is no longer on hold.
