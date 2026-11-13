---
status: open
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: null
---

# The editor's scene viewport

## Question

`editor`'s Scene tab renders a scene in software into a texture shown by a `Viewport`: it renders
at the viewport's size (`Rviewport_set_resize_callback`), redraws every frame only while the
animation plays (`Rviewport_set_realtime`), splits the rendering across Raven's job system
(`Rparallel_for`, `Rget_worker_thread_count`), and moves the selected object by dragging, with a
crosshair cursor. Phase 3's `Viewport(image, modifier, stretch, flipY)` has no size and no
realtime mode. Decide how the app learns the size it renders at (a `Viewport(onResize)`, or
Compose's `Modifier.onSizeChanged`), whether realtime is needed beside the frame clock, and whether
the job system gets a counterpart or the port uses Kotlin's own (coroutines on
`Dispatchers.Default`). Dragging and the cursor are phase 3's `onPointerEvent` and
`pointerHoverIcon`.
