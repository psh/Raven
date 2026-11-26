---
status: open
---

# samples/viewport-vulkan

Port Raven's `examples/viewport_vulkan` to `compose/samples/viewport-vulkan`, over
`compose-raven-vulkan`: the scene rendered into the app's own image and shown as a texture, and
drawn in Raven's frame by `VulkanViewport(onDraw)`. Spec:
[10: `samples/viewport-vulkan`](../spec.md#samples-viewport-vulkan-phase-5). Planned in
[Designed samples: the three ports' plan](archive/0205-designed-samples-the-three-ports-plan.md).

**Depends on:** [samples/viewport-opengl](0219-samples-viewport-opengl.md).

## Deliverables

- [ ] A cinterop local to the sample over `vulkan/vulkan.h`, its headers found through a Gradle property, with Homebrew's MoltenVK headers as the default on macOS and the system's `vulkan-headers` on Linux; a clear build message when they are missing.
- [ ] The module `samples/viewport-vulkan`, with its launch script, on Raven's device (`VulkanFrame`'s handles) and with the queue lock Raven's example takes.
- [ ] The left view: the app's image rendered and shown through `ImageBitmap.fromVulkanImageView`; the right view: `VulkanViewport(realtime, onDraw)` drawing in Raven's frame. The example's shaders from `shaders_spv.h`.
- [ ] Headless tests: the controls work and neither view draws. With `RAVEN_GPU_TESTS=1`: both views draw a frame.
- [ ] The port gaps, if any, on this ticket and in `roadmap.md`. Shipping Vulkan bindings with the module stays in graphics extras.
