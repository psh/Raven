---
status: open
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: null
---

# Specialist widgets: Timeline

## Question

How is Raven's Timeline (`Rtimeline_*`, 59 functions: tracks of clips over time, a playhead,
selection, zoom and scroll, dragging and trimming clips) bound? Decide how tracks and clips live in
Compose state (keyed lists the app owns), which edits are controlled (a clip moved, trimmed, split)
and which Raven's, the playhead (controlled, or a state the app drives while playing), and the view,
following the earlier batches' patterns. `studio` uses it with NodeGraph. The answer is the spec
section and its implementation tickets, and with
[Specialist widgets: NodeGraph](archive/0276-specialist-widgets-nodegraph.md) the `studio` port. NodeGraph is decided ([Specialist widgets: NodeGraph, the composable](0281-specialist-widgets-nodegraph-the-composable.md)): the `studio` port is filed here.
