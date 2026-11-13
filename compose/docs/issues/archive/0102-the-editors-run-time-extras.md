---
status: closed
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: Paul Hawke
---

# The editor's run-time extras

## Question

`editor` shows Raven's frame statistics (`Rwindow_get_frame_stats`: widgets, primitives, draw
calls, layout, paint and GPU times) in its tool bar and draws frame times in its Profiler tab; its
View > Scale menu changes the theme's metrics at run time (`Rtheme_set_metric`: font size, control
height, row height, for Compact and Comfortable); and its log starts with `Rget_version()` and the
worker count. Phase 3 sets theme metrics only in theme files and has no frame statistics. Decide
which of these get a compose-raven API (for example `LocalWindowInfo.frameStats`, or
`RavenTheme(metrics)`), and which the port leaves out as port gaps with the phase that completes
them.

## Resolution

Decided 2026-11-12. Written into spec section 3 (`RavenInfo`), section 4 (`frameStats()`),
section 8 (theming) and section 12.

- **Frame statistics**: `LocalWindowInfo.current.frameStats(): FrameStats`, an immutable value from
  `Rwindow_get_frame_stats` (`layoutMs`, `paintMs`, `renderMs`, `widgetCount`, `primitiveCount`,
  `drawCallCount`, `backdropCount`, `paintJobCount`, `frameIndex`). A plain call, not snapshot
  state: as state it would change every frame, and reading it in composition would recompose
  every frame and change it again. `editor` samples it every 0.25 s from its own loop. The
  Profiler's frame-time graph needs nothing new: `withFrameNanos` deltas give it.
- **Run-time metrics**: `RavenTheme(metrics: Map<ThemeMetric, Float> = emptyMap())`, application
  level only, like `accent`. `ThemeMetric` mirrors every Raven metric, glass ones included, in
  Raven's own values; `Rtheme_set_metric`, applied again after each look or theme change, which
  resets it; a changed `FontSize` moves `Typography.fromTheme()`'s base. Theme files couldn't do
  it: `editor`'s Scale and its theme are independent choices, and Raven has one theme slot. The
  spec's "glass metrics in theme files only" became "theme files or `metrics`".
- **Version**: `RavenInfo.version` (`Rget_version`), read-only, in `compose-raven-ui`. The port
  doesn't use Raven's workers ([The editor's scene viewport](0101-the-editors-scene-viewport.md)),
  so its log prints its own rendering parallelism, not `Rget_worker_thread_count`.
