---
status: open
---

# Specialist widgets: NodeGraph extras

Bind NodeGraph's rarer extras, left out of the composable's first version: pin tags (`Rnode_graph_set_pin_tag`, `_set_pin_tag_lit`, `_get_tag_at`), pin emphasis, a pin's label width, hidden links (`_set_link_hidden`, `_set_link_shown`), shown and hidden nodes, and a badge's colour. Spec: [8: NodeGraph](../spec.md#nodegraph-phase-5). Planned in [Specialist widgets: NodeGraph](archive/0276-specialist-widgets-nodegraph.md).

**Depends on:** [Specialist widgets: NodeGraph, the composable](0281-specialist-widgets-nodegraph-the-composable.md).

## Deliverables

- [ ] Each extra as a field of `GraphPin`, `GraphLink` or `GraphNode` (`tag`, `tagLit`, `emphasis`, `labelWidth`, `hidden`, `shown`, `badgeColor`), diffed as the others are, and a click on a tag reported if Raven reports one.
- [ ] Headless tests for each.
- [ ] Spec section 8 and section 12's row updated; `api-coverage.md` updated.
