---
status: open
---

# Accessibility semantics

What the screen reader sees beyond Raven's own description of each widget. Spec: [7: Accessibility](../spec.md#accessibility-phase-3).

**Depends on:** nothing in phase 3.

## Deliverables

- [ ] `R_ROLE_NONE` on internal content nodes.
- [ ] `Modifier.semantics { }`: `contentDescription`, `role`, `heading()`, `hideFromAccessibility()`, and `accessibleRole`/`accessibleDescription`.
- [ ] Automatic labels: an input in a `Row`/`FlowRow` without a description or a `Text` before it is labelled by the `Text` after it; the Applier re-checks a parent after its children change.
- [ ] `announceForAccessibility(text, assertive)`; the host calls `Raccessibility_follow_system()` unless `followSystemAccessibility = false`.
- [ ] Tests, with `onNodeWithContentDescription`: names, roles, hidden subtrees and automatic labels reach Raven.
