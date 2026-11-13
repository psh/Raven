---
status: open
---

# Island

The pill at the top of the window. Spec: [8: Transitions and Island](../spec.md#transitions-and-island-phase-3).

**Depends on:** [Windows that come and go, and their owners](archive/0112-windows-that-come-and-go-and-their-owners.md).

## Deliverables

- [ ] `Island(expanded, onExpandedChange, modifier, expandOnHover, expandOnClick, attached, notch, folded, unfolded)`, controlled.
- [ ] `Window(island = { Island(...) })` over `Rwindow_set_island`, in its own composition.
- [ ] Tests: a refused expansion reverts; hover and click expansion report back.
