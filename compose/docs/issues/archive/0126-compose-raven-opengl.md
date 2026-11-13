---
status: closed
---

# compose-raven-opengl

The opt-in OpenGL module. Spec: [8: Custom drawing, viewports and effects](../../spec.md#custom-drawing-viewports-and-effects-phase-3).

**Depends on:** [Custom drawing, viewports and effects](0124-custom-drawing-viewports-and-effects.md).

## Deliverables

- [x] `OpenGLViewport(modifier, realtime, onInit, onDraw, onDispose)` on a context the module creates on a native child window (NSOpenGLContext on the child's `NSView` on macOS, GLX on Linux).
- [x] `OpenGLTexture(width, height)` rendering to an FBO read back into an `ImageBitmap`.
- [x] Tests, where a GL context is available: a cleared frame read back.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 461 tests, 3 of them `OpenGLTest`'s; with
`RAVEN_GPU_TESTS=1` the one that needs a real window passes too (it opens a window, so it is off by
default and returns early without it).

- **The module**, `compose-raven-opengl` (`settings.gradle.kts`), depends on
  `compose-raven-foundation` for `NativeViewport`. The app brings its own GL bindings: on macOS,
  Kotlin/Native's `platform.OpenGL3`.
- **`OpenGLViewport(modifier, realtime = false, onInit = {}, onDraw: (width, height) -> Unit,
  onDispose = {})`**: a `NativeViewport` whose child `NSView` gets an `NSOpenGLContext` (3.2 core,
  double-buffered, at the view's full resolution), as [the spike](0108-verify-gl-and-metal-on-macos-child-views.md)
  verified. Everything runs on the UI thread, the context current: `onInit` once it exists,
  `onDraw` with the size in pixels, then `flushBuffer`, and `onDispose` as the viewport leaves.
  `onDraw` is read-observed as a `Canvas` is: it runs again when state it read changes, on a resize,
  when it changes, and every frame with `realtime`; otherwise nothing draws while nothing changes.
  Where no context can be made (a headless host), nothing runs, with `NativeViewport`'s warning.
- **`OpenGLTexture(width, height)`**: a framebuffer (RGBA8 colour, depth and stencil) in an
  offscreen context of its own; `render { }` binds it, sets the viewport, draws, and reads it back
  into `image`, an `ImageBitmap`, turned to Raven's top row first; `resize` and `close`.
- **Deviation**: **GLX on Linux is not built**. Kotlin/Native has no GL platform library on Linux, so
  it needs a cinterop of `GL/glx.h` that can't be built or checked before the Linux build (phase 4);
  `linuxMain` makes no context for now, so the viewport warns and `OpenGLTexture` throws. The spec
  says so.
- **Tests** (`OpenGLTest`, in `appleTest`): a texture cleared green with its top row red, read back
  top row first, then resized and cleared blue; a headless viewport making no context; on a real
  window, `onInit`, `onDraw` at the size in pixels with no GL error, and `onDispose`, which also
  checks 0124's lookup of the child's `NSView` by its layer.
