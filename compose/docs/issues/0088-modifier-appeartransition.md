---
status: open
---

# Modifier.appearTransition

`Modifier.appearTransition` arrives early, in phase 2, for `samples/components`' cards.
`AnimatedVisibility`, `animate*AsState` and `Island` stay in phase 3. Spec:
[Transitions and Island](../spec.md#transitions-and-island-phase-3). Decided in:
[Island and appear transitions](archive/0036-island-and-appear-transitions.md), and brought into
phase 2 by [ADR 0001](../adr/0001-phase-split-after-the-phase-2-sample-gaps.md).

**Depends on:** nothing open.

## Deliverables

- [ ] `Modifier.appearTransition(AppearTransition, delay)` with `Fade`, `Pop`, `Zoom`, `FlyUp`,
      `FlyDown`, `FlyLeft`, `FlyRight`, `Drop`, `SlideLeft` and `SlideRight`, playing Raven's
      transition each time the widget appears.
- [ ] Tests: a node added by recomposition gets its transition and delay; a modifier change doesn't
      replay it on a widget already shown.
