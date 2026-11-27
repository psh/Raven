---
status: closed
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: claude
---

# Specialist widgets: Timeline

## Question

How is Raven's Timeline (`Rtimeline_*`, 59 functions: tracks of clips over time, a playhead,
selection, zoom and scroll, dragging and trimming clips) bound? Decide how tracks and clips live in
Compose state (keyed lists the app owns), which edits are controlled (a clip moved, trimmed, split)
and which Raven's, the playhead (controlled, or a state the app drives while playing), and the view,
following the earlier batches' patterns. `studio` uses it with NodeGraph. The answer is the spec
section and its implementation tickets, and with
[Specialist widgets: NodeGraph](0276-specialist-widgets-nodegraph.md) the `studio` port. NodeGraph is decided ([Specialist widgets: NodeGraph, the composable](../0281-specialist-widgets-nodegraph-the-composable.md)): the `studio` port is filed here.

## Resolution

Resolved on 2026-11-27, with the user, in two rounds: designed into
[spec section 8: Timeline](../../spec.md#timeline-phase-5), section 10's
[samples/studio](../../spec.md#samplesstudio-phase-5) and section 12.

- **Tracks and items the app's**: keyed lists of `TimelineTrack` holding `Clip`s and `Key`s, and
  `TimelineMarker`s; the user's moves and trims, deletions and the selection controlled, as
  NodeGraph's are.
- **The playhead in a `TimelineState`**: observable `time` and `isPlaying`, `play`, `pause`,
  `seek`, `loop`, the view as commands; Raven plays.
- **Settings as parameters**, the finer parts left to Raven's theme styles; `onItemActivate`.
- Headless tests, the XML translator; the `studio` port without port gaps, its XML live reload
  having no counterpart.

Filed as [Specialist widgets: Timeline, the composable](../0283-specialist-widgets-timeline-the-composable.md)
and [samples/studio](../0284-samples-studio.md).
