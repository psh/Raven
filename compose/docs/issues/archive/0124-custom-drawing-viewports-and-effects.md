---
status: closed
---

# Custom drawing, viewports and effects

`Canvas`, the texture and native viewports, and shader effects. Spec: [8: Custom drawing, viewports and effects](../../spec.md#custom-drawing-viewports-and-effects-phase-3).

**Depends on:** [Images and resources](0115-images-and-resources.md), [Modifier.offset and LocalDensity](0118-layout-offset-and-density.md).

## Deliverables

- [x] `Canvas(modifier) { }` with `DrawScope` (rect, round rect, circle, line, `clipRect`, `drawText`, `drawImage`, `drawCubic`, `drawTriangle`, `drawGlass`, `drawShader`), read-observed so state it reads invalidates it.
- [x] `Viewport(image, modifier, stretch, flipY, onSizeChanged)`, the size in pixels ([The editor's scene viewport](0101-the-editors-scene-viewport.md)).
- [x] `NativeViewport(modifier, onCreated, onResize)`; on macOS the handle carries the child's `NSView`, found in the window's view tree by its `CAMetalLayer`, and the layer ([Verify GL and Metal on macOS child views](0108-verify-gl-and-metal-on-macos-child-views.md)).
- [x] `ShaderEffect.builtin/fromFile/fromBytes` cached per host; `Surface(effect, effectStyle)` with `EffectStyle(color, color2, params, animate, pointer)` ([Animated effects and continuous redraw](0104-animated-effects-and-continuous-redraw.md)).
- [x] Tests: a Canvas repainting on state, a viewport's reported size, an effect's style re-applied without a restart.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 455 tests, 6 of them `DrawingTest`'s.

- **`Canvas(modifier) { }`**, in `Canvas.kt`, is a custom widget (`R_WIDGET_CUSTOM`) sized by its
  Modifier. Raven's paint callback (`CustomPaint`, `Binding.kt`) runs `onDraw` in a **`DrawScope`**
  (`raven.compose.ui.graphics.drawscope`) over Raven's painter, in logical units: `drawRect`,
  `drawRoundRect`, `drawCircle` (filled or `Stroke(width)`; Raven has no circle outline, so a
  stroked circle is a fully rounded rectangle's), `drawLine`, `clipRect { }`, `drawText` (theme font
  and size unless given), `drawImage`, and Raven's `drawCubic`, `drawTriangle`, `drawGlass` and
  `drawShader`. `size` is a new `geometry.Size`. Raven draws a texture whole, so `drawImage` of a
  `BitmapPainter` of part of an image draws all of it.
  - **Read-observed**: the drawing runs in a `SnapshotStateObserver`, and a change to state it read
    calls `Rwidget_invalidate`, without recomposing; a new `onDraw` (its captured values changed)
    draws it again too.
- **`Viewport(image, modifier, stretch = ContentScale.FillBounds, flipY = false, onSizeChanged)`**,
  in `Viewport.kt`: `ContentScale` maps to Raven's stretch (Fit, Crop as cover, FillBounds as fill,
  None), the viewport is one of the image's viewers so `update` redraws it, and `onSizeChanged`
  gets the size in pixels from `Rviewport_set_resize_callback` (`ViewportResized`).
- **`NativeViewport(modifier, onCreated, onResize)`** makes the viewport's native child window once
  it is in a window (`Rviewport_set_native`, in an effect) and passes a **`NativeWindowHandle`**
  (`system`, `display`, `window`, `visual`, `view`). On macOS `display` is the `NSWindow`, `window`
  the child's `CAMetalLayer`, and `view` its `NSView`, found in the window's view tree by that layer
  (`appleMain`'s `cocoaViewOfLayer`), as [the spike](0108-verify-gl-and-metal-on-macos-child-views.md)
  found. A window of no window system (a headless host) can't have one: nothing is made and
  `onCreated` isn't called, with a warning. The real child window is not tested, the tests being
  headless.
- **Shader effects**: `ShaderEffect.builtin(Effect.Aurora | Shimmer | Neon | Ripple | Plasma | Grain
  | Spotlight | Mesh)`, `fromFile(path, backdrop, animated)` and `fromBytes(spirv, backdrop,
  animated)` (one array, one shader), each made once per host (`HostCaches`). Code that isn't
  SPIR-V gives `R_INVALID_ID`, which Raven reports. `Surface(effect, effectStyle)` sets
  `Rborder_set_effect` with an **`EffectStyle(color, color2, params, animate, pointer)`** filled
  over the shader's defaults (`Rshader_look_defaults`), the corner radius the shape's; a changed
  style sets it again on the same shader, which only replaces the look. `DrawScope.drawShader` uses
  `Rdraw_shader`. Raven has no getter for a border's effect, so the tests see it through an
  `effectCalls` seam.
- **Tests** (`DrawingTest`): a Canvas drawn once, not again while nothing changes, and again,
  without recomposing, when state it read changes; every `DrawScope` call drawing without a Raven
  error; a viewport's size in pixels, and again after it grows; a headless `NativeViewport`'s
  warning; an effect's style set again on the same shader, and removed; shaders made once per host,
  and the effects' equality.
