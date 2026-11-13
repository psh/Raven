---
status: open
---

# Custom drawing, viewports and effects

`Canvas`, the texture and native viewports, and shader effects. Spec: [8: Custom drawing, viewports and effects](../spec.md#custom-drawing-viewports-and-effects-phase-3).

**Depends on:** [Images and resources](archive/0115-images-and-resources.md), [Modifier.offset and LocalDensity](0118-layout-offset-and-density.md).

## Deliverables

- [ ] `Canvas(modifier) { }` with `DrawScope` (rect, round rect, circle, line, `clipRect`, `drawText`, `drawImage`, `drawCubic`, `drawTriangle`, `drawGlass`, `drawShader`), read-observed so state it reads invalidates it.
- [ ] `Viewport(image, modifier, stretch, flipY, onSizeChanged)`, the size in pixels ([The editor's scene viewport](archive/0101-the-editors-scene-viewport.md)).
- [ ] `NativeViewport(modifier, onCreated, onResize)`; on macOS the handle carries the child's `NSView`, found in the window's view tree by its `CAMetalLayer`, and the layer ([Verify GL and Metal on macOS child views](archive/0108-verify-gl-and-metal-on-macos-child-views.md)).
- [ ] `ShaderEffect.builtin/fromFile/fromBytes` cached per host; `Surface(effect, effectStyle)` with `EffectStyle(color, color2, params, animate, pointer)` ([Animated effects and continuous redraw](archive/0104-animated-effects-and-continuous-redraw.md)).
- [ ] Tests: a Canvas repainting on state, a viewport's reported size, an effect's style re-applied without a restart.
