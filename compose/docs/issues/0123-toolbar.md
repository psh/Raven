---
status: open
---

# Toolbar

A row with the toolbar role. Spec: [8: Toolbar](../spec.md#toolbar-phase-3).

**Depends on:** nothing in phase 3.

## Deliverables

- [ ] `Toolbar(modifier, contentPadding, horizontalArrangement) { }`, an HBox with `R_ROLE_TOOLBAR`, children centred vertically, and `ToolbarSeparator()` in `ToolbarScope`.
- [ ] Tests: the role, the separator, and a toolbar inside a `HeaderBar`.
