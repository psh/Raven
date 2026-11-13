---
status: open
---

# compose-raven-vulkan

The opt-in Vulkan module. Spec: [8: Custom drawing, viewports and effects](../spec.md#custom-drawing-viewports-and-effects-phase-3).

**Depends on:** [Custom drawing, viewports and effects](0124-custom-drawing-viewports-and-effects.md).

## Deliverables

- [ ] `VulkanViewport(modifier, realtime, onDraw: (VulkanFrame) -> Unit)` on the render thread, with exceptions ending the app through the host's error path.
- [ ] `ImageBitmap.fromVulkanImageView(...)`, `RavenVulkan.device`, and `application(vulkan = VulkanSetup.External(...))`.
- [ ] Tests, where a Vulkan device is available: a frame recorded and an external image shown.
