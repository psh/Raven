---
status: open
wayfinder: grilling
parent: 0241
blocked_by: [0244]
assignee: null
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
