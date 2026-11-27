---
status: open
wayfinder: grilling
parent: 0287
blocked_by: []
assignee: null
---

# Layout and direction: custom Layout

## Question

Whether and how compose-raven offers Compose's `Layout(content, modifier) { measurables, constraints -> }` and `Modifier.layout`. It needs a Raven change: custom widgets that take children, a measure callback given the available size, an arrange callback, `Rwidget_measure_child` / `Rwidget_arrange_child`. The smallest change that serves it, the `Constraints` and `Placeable` it can honestly offer, and the sample or test app that needs it. From [compose-raven phase 5 spec](0204-compose-raven-phase-5-spec.md#not-yet-specified).
