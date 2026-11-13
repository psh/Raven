---
status: closed
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: Paul Hawke
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

## Resolution

Decided 2026-11-12. Written into spec section 6 (`LocalDensity`), section 8 (viewports, and
`ImageBitmap`) and section 12.

- **The size to render at**: `Viewport(onSizeChanged: ((IntSize) -> Unit)? = null)`, in pixels,
  from `Rviewport_set_resize_callback`: what a render target needs, and what Compose's
  `onSizeChanged` reports. A generic `Modifier.onSizeChanged` waits until a sample needs it (Raven
  has no size callback but the viewport's, so it would poll).
- **`LocalDensity`** (new, minimal): `density` is the window's scale (`Rwindow_get_scale`), with
  `Dp.toPx()` and `Int.toDp()`, so the app maps pointer positions (dp) into the scene's pixels.
- **No `realtime` flag**: `ImageBitmap.update(...)` invalidates every `Viewport` showing the image,
  since Raven doesn't redraw for `Rtexture_update`. The app paces itself:
  `LaunchedEffect(playing) { while (true) withFrameNanos { render(); image.update(...) } }`; paused,
  nothing updates or redraws, as `Rviewport_set_realtime(false)` gives the C example.
- **Resizing**: `ImageBitmap.update(width, height, rgba)` may change the size (`Rtexture_update`
  resizes), and `width`/`height` follow; one image lasts the session, and `stretch` fits it to the
  widget, through the viewport's size changes and the Full/Half/Quarter resolution.
- **The job system**: no counterpart. The port renders bands with coroutines on
  `Dispatchers.Default` and calls `update` on the UI thread, to which `ImageBitmap.update` stays
  confined like the rest of Raven's API. "Render on all cores" off renders inline. (The worker
  count belongs to [The editor's run-time extras](0102-the-editors-run-time-extras.md).)
- **Already covered**: dragging is `onPointerEvent`, the crosshair `pointerHoverIcon(PointerIcon.Crosshair)`.
