---
status: closed
---

# samples/viewport-vulkan

Port Raven's `examples/viewport_vulkan` to `compose/samples/viewport-vulkan`, over
`compose-raven-vulkan`: the scene rendered into the app's own image and shown as a texture, and
drawn in Raven's frame by `VulkanViewport(onDraw)`. Spec:
[10: `samples/viewport-vulkan`](../spec.md#samples-viewport-vulkan-phase-5). Planned in
[Designed samples: the three ports' plan](archive/0205-designed-samples-the-three-ports-plan.md).

**Depends on:** [samples/viewport-opengl](archive/0219-samples-viewport-opengl.md).

## Deliverables

- [x] A cinterop local to the sample over `vulkan/vulkan.h`, its headers found through a Gradle property, with Homebrew's MoltenVK headers as the default on macOS and the system's `vulkan-headers` on Linux; a clear build message when they are missing.
- [x] The module `samples/viewport-vulkan`, with its launch script, on Raven's device (`VulkanFrame`'s handles) and with the queue lock Raven's example takes.
- [x] The left view: the app's image rendered and shown through `ImageBitmap.fromVulkanImageView`; the right view: `VulkanViewport(realtime, onDraw)` drawing in Raven's frame. The example's shaders from `shaders_spv.h`.
- [x] Headless tests: the controls work and neither view draws. With `RAVEN_GPU_TESTS=1`: both views draw a frame.
- [x] The port gaps, if any, on this ticket and in `roadmap.md`. Shipping Vulkan bindings with the module stays in graphics extras.

## Resolution

Built on 2026-11-26: `compose/samples/viewport-vulkan` (`Main.kt`, `Scene.kt`, `VulkanLoader.kt`,
`Shaders.kt`, the `vulkan.def` cinterop, `launch-viewport-vulkan`), registered in
`settings.gradle.kts`, listed in `compose/README.md` and the roadmap.

- **Headers**: Homebrew's MoltenVK ships only its own headers, no `vulkan/vulkan.h`, so the default
  the ticket named doesn't exist. The cinterop defaults instead to the headers Raven's own build found
  or fetched (`build/raven/<host>/_deps/raven_vulkan_headers-src/include`, Vulkan-Headers v1.3.290,
  after `:compose-raven-native:configureRaven`), the Vulkan libraven is compiled against;
  `-PravenVulkanInclude=<folder>` names an SDK's or the system's. Missing headers fail the cinterop
  task with what to do. Spec section 10 updated.
- **No linking**: the C example links the Vulkan loader; Raven links nothing and opens the library at
  run time. The cinterop is `VK_NO_PROTOTYPES`, and `VulkanFunctions` opens the library by Raven's
  names in Raven's order (the same library) and resolves the 37 functions with
  `vkGetInstanceProcAddr` and `vkGetDeviceProcAddr` on Raven's instance and device. Nothing to install
  on Linux beyond what Raven needs.
- **Shaders**: the C example's SPIR-V read from `shaders_spv.h` where it is (`EXAMPLE_DIR`).
- **Both modes**, as the C example: the app's image rendered on the UI thread each frame and shown
  by `Viewport(ImageBitmap.fromVulkanImageView(...))`, resized with `setVulkanImageView`; and
  `VulkanViewport(realtime = !paused)` drawing in Raven's pass on the render thread, its pipeline made
  for Raven's render pass on first use, the angle negated. A scene lock guards the angle, that
  pipeline and closing; the queue lock guards submits and the final `vkDeviceWaitIdle`. The scene
  closes first-made-last-gone, before Raven's device.
- **Port gaps**, of `compose-raven-vulkan`, recorded in `roadmap.md` and the map's graphics extras:
  1. No queue lock for Raven's own device: only `VulkanSetup.External` takes one, so the sample calls
     `Rvulkan_set_queue_lock_callbacks` itself before `application` (`QueueLock.install`).
  2. No public redraw for a bitmap whose Vulkan image changed on the GPU: the sample opts into the
     internal `ImageBitmap.textureChanged()` after each submit, where the C example's viewport is
     `Realtime="true"` and foundation's `Viewport` has no `realtime`.
- **Tests** (4, passing): the SPIR-V read from the header (642 and 124 words, the magic number), a
  malformed array refused; headless, no scene, the message in place of the info, the callback
  viewport present and the pause switch working. With `RAVEN_GPU_TESTS=1`, both views draw on a real
  window (the image rendered, the callback called): passed run from the test binary, as Gradle doesn't
  pass the variable on (see [samples/viewport-opengl](0219-samples-viewport-opengl.md)).
- Checked by eye in the release build: both triangles turning, the buttons over them, the splitter.
  A Column doesn't stretch its children across as the XML's VBox does, so the splitter and each pane
  fill the width explicitly (found on screen).
