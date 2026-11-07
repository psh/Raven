---
status: closed
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: Paul Hawke
---

# Custom drawing and custom layout

## Question

How does compose-raven offer content Raven's widgets can't express? Decide the shape of a `Canvas`-like composable over Raven's custom widget (`Rcustom_set_paint_callback`, `Rcustom_set_measure_callback`, `RPainter` in `raven_draw.h`), how `Viewport` (`R_WIDGET_VIEWPORT`, textures, OpenGL/Vulkan) is exposed for the `editor`, `effects` and viewport examples, and whether custom `Layout { }` gets a Raven arrange hook as a required fork change.

## Resolution

Facts: a custom widget (`R_WIDGET_CUSTOM`, `raven_draw.h`) has a paint callback on the UI thread and a measure callback that returns a desired size and gets no constraints. `Rdraw_*` covers rounded rectangles (fill or outline), circles, lines, cubic curves, triangles, images, text, glass, shaders and clip rectangles, and `Rwidget_invalidate` repaints. Viewports (`raven_viewport.h`) have three modes: texture, a draw callback on the **render thread** with Vulkan handles, and a native child window (an `NSView` with its own `CAMetalLayer` on macOS). `raven_vulkan.h` passes Vulkan handles as `void*`/`uint64_t` and needs no Vulkan SDK. Raven never touches OpenGL, and `viewport_opengl` makes its own WGL/GLX context with no macOS branch. Raven has **no Metal backend**: on macOS it renders Vulkan through MoltenVK onto a `CAMetalLayer`. Shader effects are SPIR-V fragment shaders, with built-ins, applied with `Rborder_set_effect` or `Rdraw_shader`. Custom widgets can't place children.

- **`Canvas(modifier) { onDraw: DrawScope.() -> Unit }`** is a custom widget whose size comes from its Modifier, as in Compose.
  - `DrawScope` has `drawRect`, `drawRoundRect`, `drawCircle` and `drawLine`, with `style = Fill | Stroke(width)` where Raven supports it, plus `clipRect { }` and `drawText(text, topLeft, style)`. `drawImage` waits for [Image and resources](0035-image-and-resources.md).
  - Raven-only extensions (documented): `drawCubic`, `drawTriangle`, `drawGlass` and `drawShader`. There is no `Path`.
  - `onDraw` runs under a snapshot read observer, and a change in state it read calls `Rwidget_invalidate`, so `animate*AsState` repaints naturally. Raven's springs are not exposed.
- **Viewports** (in foundation):
  - `Viewport(texture: Texture, modifier, stretch, flipY)` uses texture mode. `Texture` comes from Image and resources, updated with CPU pixels (`Rtexture_update`). It serves `editor`.
  - `NativeViewport(modifier, onCreated: (NativeWindowHandle) -> Unit, onResize)` uses `Rviewport_set_native` and hands over the platform handle (on macOS the `NSView` and its `CAMetalLayer`). It sits above the UI and is not clipped (documented).
  - Input to both comes through the generic input modifiers.
- **`compose-raven-vulkan`**, a separate opt-in module, gives a path to Vulkan code:
  - **Cinterop**: over `raven_vulkan.h` (`raven.vulkan.cinterop`), public by design.
  - **`VulkanViewport(modifier, realtime = false, onDraw: (VulkanFrame) -> Unit)`** uses `Rviewport_set_draw_callback`. `VulkanFrame` carries `commandBuffer`/`renderPass` (`COpaquePointer`), `colorFormat`, the viewport and clip rectangles, and `frameIndex`. **`onDraw` runs on Raven's render thread**: it may record Vulkan commands and read plain data, but must not write Compose state or call Raven's UI API (documented). `realtime` uses `Rviewport_set_realtime`, and otherwise a change in state calls `Rwidget_invalidate` from the UI thread.
  - **Textures**: `Texture.fromVulkanImageView(imageView: Long, sampler: Long, width, height)` and `setImageView(...)`, zero copy.
  - **Device**: `RavenVulkan.device` (`instance`, `physicalDevice`, `device`, `graphicsQueue`, `queueFamily`) once Raven runs. `application(vulkan = VulkanSetup.External(instance, physicalDevice, device, queueLock))` shares the app's device, with `RavenVulkan.requiredInstanceExtensions()`/`requiredDeviceExtensions()`.
  - **Vulkan bindings for Kotlin** (`vkCmd...`) come from the app: a cinterop over the SDK headers, or a KMP Vulkan library.
- **`compose-raven-opengl`**, a separate opt-in module, manages the GL context for the app:
  - **`OpenGLViewport(modifier, realtime = false, onInit, onDraw: GLContextScope.(width, height) -> Unit, onDispose)`** uses the native child window with a context created by the module: **EGL on Linux** (X11 and Wayland) and `NSOpenGLContext` on macOS. The callbacks run on the UI thread, and `onDraw` runs after compose-raven's frame callback with the context current and buffers swapped. It sits above the UI and is not clipped (documented).
  - **`OpenGLTexture(width, height)`** renders into an FBO and reads the pixels back into a `Texture`, for GL under the UI.
  - **Cinterop**: only EGL on Linux, plus Kotlin/Native's OpenGL and AppKit platform libraries on macOS. GL function bindings come from the app.
  - **Verify first**: a GL context on Raven's macOS child view, which has a Metal layer. If it fails, macOS gets `OpenGLTexture` only.
- **Metal**: no module, because Raven has no Metal interop to wrap. Apps draw with Kotlin/Native's `platform.Metal` into the `CAMetalLayer` from `NativeViewport`. **Verify first** that this works while Raven renders with MoltenVK in the parent view. A `MetalViewport` convenience goes to the fog, and a Metal backend for Raven is out of scope.
- **Shader effects**: `ShaderEffect.builtin(Effect.Aurora | Shimmer | ...)`, `ShaderEffect.fromFile(path)` and `fromBytes(spirv)`, cached like fonts. `Surface(effect: ShaderEffect? = null, effectStyle = EffectStyle(color, color2, params))` uses `Rborder_set_effect`, and `DrawScope.drawShader(effect, ...)` uses `Rdraw_shader`. These are compose-raven names, because Compose's `RuntimeShader`/AGSL are platform-specific.
- **Custom `Layout {}`**: deferred, because no phase 3 sample needs it. The plan for when one does is a fork change: custom widgets that take children, a measure callback given the available size, an arrange callback, and `Rwidget_measure_child`/`Rwidget_arrange_child`.
- **Samples**: `effects` ports through shader effects. `viewport_opengl` ports through `OpenGLViewport`, and `viewport_vulkan` through `VulkanViewport` and Vulkan textures.
