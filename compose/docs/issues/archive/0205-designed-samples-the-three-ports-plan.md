---
status: closed
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: claude
---

# Designed samples: the three ports' plan

## Question

What do the `effects`, `viewport_opengl` and `viewport_vulkan` ports need, measured against the
design in [Custom drawing and custom layout](archive/0028-custom-drawing-and-layout.md) and the
modules that exist now (`compose-raven-opengl`, `compose-raven-vulkan`, `ShaderEffect`)? For each C
example (`examples/effects`, `examples/viewport_opengl`, `examples/viewport_vulkan`): what ports
as is, which port gaps remain and the later batch or phase each names, whether any library change
is needed first, and how its test proves it. The answer is the sample section of the spec for each
and their implementation tickets, the first of the map's implementation order.

## Resolution

Resolved on 2026-11-25 in conversation. The three ports are written into spec section 10
(`samples/effects`, `samples/viewport-opengl`, `samples/viewport-vulkan`) and filed as
implementation tickets, built in this order:
[samples/effects](../0218-samples-effects.md), [samples/viewport-opengl](../0219-samples-viewport-opengl.md),
[samples/viewport-vulkan](../0220-samples-viewport-vulkan.md).

- **`effects`** ports as it is, with no library change: `Surface(effect, effectStyle)` and
  `ShaderEffect.fromFile(backdrop, animated)` cover every card, and a `Card` composable stands for
  the XML `<Widget>`. The `.spv` files are read from Raven's `examples/effects` at a path the build
  writes, as `samples/themes` does. Its web-only code has no counterpart. Headless can't draw
  effects, so the test checks the structure.
- **`viewport_opengl`**: the C example builds only on Windows and X11 (GL 1.1 on WGL or GLX, no
  macOS branch), and `compose-raven-opengl` makes a 3.2 core context on macOS only. Decided: the
  scene is rewritten for core GL (shaders and a vertex buffer), a deviation with no library change,
  rather than giving `OpenGLViewport` a legacy profile. Linux is a port gap that waits for GLX on
  Linux (graphics extras).
- **`viewport_vulkan`** needs Vulkan's API from Kotlin, which compose-raven doesn't ship (Raven has
  its own minimal loader and needs no SDK). Decided: a cinterop local to the sample against the
  system's headers (MoltenVK's from Homebrew on macOS, `vulkan-headers` on Linux), found through a
  Gradle property, rather than shipping bindings with `compose-raven-vulkan` (which stays in
  graphics extras) or leaving the sample out of the batch.
- **Tests**, as the modules' are: headless tests of the controls always, and GPU frame checks
  behind `RAVEN_GPU_TESTS=1`.
