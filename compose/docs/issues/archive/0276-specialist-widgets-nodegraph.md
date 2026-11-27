---
status: closed
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: claude
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

## Resolution

Resolved on 2026-11-27, with the user, in three rounds: designed into
[spec section 8: NodeGraph](../../spec.md#nodegraph-phase-5) and section 12.

- **The graph is the app's**: keyed lists of `GraphNode` (pins inside) and `GraphLink`, diffed
  against Raven; the user's edits (move, link with `canLink` first, unlink, delete) and the
  selection are controlled, an edit not taken reverting.
- **Composable node content**; **the view Raven's**, behind a `NodeGraphState` of commands (no
  view-changed callback in Raven, fog).
- **Events** for activation, the context menu, the canvas, a dropped wire and a pin click.
- **Styling** by `preset` and `pinTypes`, the finer parts left to Raven's theme styles; the rarer
  extras in a ticket of their own.
- Headless tests, the XML translator; `studio` ported with Timeline.

Filed as [Specialist widgets: NodeGraph, the composable](../0281-specialist-widgets-nodegraph-the-composable.md)
and [Specialist widgets: NodeGraph extras](../0282-specialist-widgets-nodegraph-extras.md).
