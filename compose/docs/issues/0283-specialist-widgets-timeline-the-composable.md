---
status: open
---

# Specialist widgets: Timeline, the composable

Bind Raven's Timeline as `Timeline(tracks, state, ...)` with controlled edits and a `TimelineState`.
Spec: [8: Timeline](../spec.md#timeline-phase-5) and section 12. Planned in [Specialist widgets: Timeline](archive/0277-specialist-widgets-timeline.md).

**Depends on:** [Specialist widgets: NodeGraph extras](0282-specialist-widgets-nodegraph-extras.md), the ticket before it in phase 5's implementation sequence.

## Deliverables

- [ ] `TimelineTrack`, `TimelineItem` (`Clip`, `Key`), `TimelineMarker`, `TimelinePreset`; the lists diffed by key against `Rtimeline_*`'s tracks and items.
- [ ] The controlled edits: `onItemChange` over the changed callback, `onItemDelete`, the selection; refused edits reverting; `onItemActivate`.
- [ ] `TimelineState`: `time` and `isPlaying` following the time callback, `play`, `pause`, `seek`, `loop`, `setView`, `view()`.
- [ ] The settings: `duration`, `frameRate`, `snapping`, `preset`, `headerWidth`, `rulerHeight`, `trackHeight`.
- [ ] Headless tests: each edit taken and refused, items added, moved and removed by key, markers, the selection, playing and seeking.
- [ ] The XML translator's `<Timeline>` and its children.
- [ ] The spec's sections checked against what was built; `api-coverage.md` updated (the Timeline row and the numbers).
