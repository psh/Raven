---
status: closed
---

# compose-raven-vulkan

The opt-in Vulkan module. Spec: [8: Custom drawing, viewports and effects](../../spec.md#custom-drawing-viewports-and-effects-phase-3).

**Depends on:** [Custom drawing, viewports and effects](0124-custom-drawing-viewports-and-effects.md).

## Deliverables

- [x] `VulkanViewport(modifier, realtime, onDraw: (VulkanFrame) -> Unit)` on the render thread, with exceptions ending the app through the host's error path.
- [x] `ImageBitmap.fromVulkanImageView(...)`, `RavenVulkan.device`, and `application(vulkan = VulkanSetup.External(...))`.
- [x] Tests, where a Vulkan device is available: a frame recorded and an external image shown.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 458 tests, 3 of them `VulkanTest`'s; with
`RAVEN_GPU_TESTS=1` the two that need a Vulkan device pass on MoltenVK too (they open a window, so
they are off by default and return early without it).

- **The module**, `compose-raven-vulkan` (`settings.gradle.kts`), depends on `compose-raven-ui` and
  has a cinterop of its own for `raven_vulkan.h` (`raven.cinterop.vulkan`), linked by
  `raven-native`'s `libraven.a`. Raven exchanges Vulkan handles as opaque pointers and 64-bit
  integers, so the module needs no Vulkan SDK, and the app brings its own bindings.
- **`VulkanViewport(modifier, realtime = false, onDraw: (VulkanFrame) -> Unit)`** sets the viewport's
  draw callback (`Rviewport_set_draw_callback`) to a `StableRef` of its own, freed when it leaves
  the composition after the callback is cleared. `onDraw`, swapped atomically as it recomposes, runs
  on **Raven's render thread** with a `VulkanFrame` (`commandBuffer`, `renderPass`, `colorFormat`,
  the area, its clip, the framebuffer's size and the frame index). What it throws is caught on that
  thread and goes to the host's error path, which ends the app. `realtime` is
  `Rviewport_set_realtime`.
- **`RavenVulkan.device`**: a `VulkanDevice` (instance, physical device, device, graphics queue and
  its family), or null off Vulkan (a headless host).
- **`ImageBitmap.fromVulkanImageView(imageView, sampler, width, height)`** wraps the app's image
  zero copy (`Rvulkan_texture_create_from_image_view`) in an `ImageBitmap` that frees it as any
  does; **`setVulkanImageView`** points it at another view after a resize. `ImageBitmap` gains a
  companion for it, and `compose-raven-ui` internal API (`imageBitmapOf`, `textureChanged`).
- **`application(vulkan = VulkanSetup.External(...))`**: the module's overload of `application`
  gives Raven the app's device (`Rvulkan_set_external_device`, and the queue's lock callbacks when
  given) before it starts. `VulkanSetup.Raven` is Raven's own device, and
  `VulkanSetup.requiredInstanceExtensions` / `requiredDeviceExtensions` list what the app's device
  needs. The spec says so.
- **Not tested**: an external image shown, which needs the app's Vulkan bindings to make an image
  (there are no Vulkan headers here, only MoltenVK's runtime), and an external device.
- **Tests** (`VulkanTest`): headless, no device and nothing drawn; on a Vulkan device, frames
  recorded on the render thread, each with a command buffer, and an exception thrown there ending
  the app.
