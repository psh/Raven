---
status: open
---

# compose-raven-opengl

The opt-in OpenGL module. Spec: [8: Custom drawing, viewports and effects](../spec.md#custom-drawing-viewports-and-effects-phase-3).

**Depends on:** [Custom drawing, viewports and effects](archive/0124-custom-drawing-viewports-and-effects.md).

## Deliverables

- [ ] `OpenGLViewport(modifier, realtime, onInit, onDraw, onDispose)` on a context the module creates on a native child window (NSOpenGLContext on the child's `NSView` on macOS, GLX on Linux).
- [ ] `OpenGLTexture(width, height)` rendering to an FBO read back into an `ImageBitmap`.
- [ ] Tests, where a GL context is available: a cleared frame read back.
