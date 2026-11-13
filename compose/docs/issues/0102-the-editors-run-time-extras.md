---
status: open
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: null
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
