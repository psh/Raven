---
status: open
---

# Specialist widgets: NodeGraph, the composable

Bind Raven's NodeGraph as `NodeGraph(nodes, links, ...)` with controlled edits and a
`NodeGraphState`. Spec: [8: NodeGraph](../spec.md#nodegraph-phase-5) and section 12. Planned in [Specialist widgets: NodeGraph](archive/0276-specialist-widgets-nodegraph.md).

**Depends on:** [samples/pdf](0280-samples-pdf.md), the ticket before it in phase 5's implementation sequence (batch nine follows batch ten).

## Deliverables

- [ ] `GraphNode`, `GraphPin`, `GraphLink`, `PinRef`, `PinType`, `NodeGraphPreset`; the lists diffed by key against `Rnode_graph_*`'s nodes, pins and links.
- [ ] The controlled edits: `onNodeMove`, `canLink` over the connect callback, `onLink`, `onUnlink`, `onNodeDelete`, the selection; refused edits reverting.
- [ ] `content` composed in each node's content.
- [ ] The events: `onNodeActivate`, `onContextMenu`, `onCanvasActivate`, `onWireDrop`, `onPinClick`.
- [ ] `NodeGraphState`: `frameAll`, `setView`, `view()`, `toCanvas`, `fromCanvas`; `preset`, `pinTypes`, `readOnly`, `grid`, `flow`.
- [ ] Headless tests: each edit taken and refused, the selection, nodes and links added, changed and removed by key, content composed, each event.
- [ ] The XML translator's `<NodeGraph>` and its children.
- [ ] The spec's sections checked against what was built; `api-coverage.md` updated (the NodeGraph row and the numbers).
