---
status: open
---

# samples/studio

Port Raven's `examples/studio`. Spec: [10: samples/studio](../spec.md#samplesstudio-phase-5). Planned in
[Specialist widgets: Timeline](archive/0277-specialist-widgets-timeline.md) and [Specialist widgets: NodeGraph](archive/0276-specialist-widgets-nodegraph.md).

**Depends on:** [Specialist widgets: Timeline, the composable](0283-specialist-widgets-timeline-the-composable.md) and [Specialist widgets: NodeGraph, the composable](0281-specialist-widgets-nodegraph-the-composable.md).

## Deliverables

- [ ] `samples/studio`: the node graph (typed pins, loops refused, the selection shown and coloured from the ColorPicker), the timeline (tracks, clips, keys, markers, Play, Pause, Loop, the time), the presets from a ComboBox.
- [ ] A headless test checking each control.
- [ ] No port gaps (the XML live reload has no counterpart); the roadmap's sample table and `api-coverage.md` updated.
