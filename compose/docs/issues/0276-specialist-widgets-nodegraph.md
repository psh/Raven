---
status: open
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: null
---

# Specialist widgets: NodeGraph

## Question

How is Raven's NodeGraph (`Rnode_graph_*`, 87 functions: nodes with typed ports, links between
them, selection, the view panned and zoomed, many callbacks) bound? Decide how nodes and links live
in Compose state (a list of nodes keyed by the app and a list of links, diffed as `Table` and the
charts diff theirs, or a state object of Raven's), which of Raven's edits the app accepts as
controlled values (a node moved, a link made or broken) and which stay Raven's, what of a node's
content is composable, and the view's state, following the patterns the earlier batches settled
(`PlotState`, `TreeView`'s controlled expansion). `studio` uses it with Timeline. The answer is
the spec section and its implementation tickets, and with
[Specialist widgets: Timeline](0277-specialist-widgets-timeline.md) the `studio` port.
