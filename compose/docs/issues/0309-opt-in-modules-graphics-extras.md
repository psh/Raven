---
status: open
wayfinder: grilling
parent: 0287
blocked_by: []
assignee: null
---

# Opt-in modules: graphics extras

## Question

Which graphics extras to design: a `MetalViewport` convenience on macOS, zero-copy GL textures (`GL_EXT_memory_object`) on Linux, a Vulkan-headers cinterop for `compose-raven-vulkan`, and the two gaps [samples/viewport-vulkan](archive/0220-samples-viewport-vulkan.md) found: a queue lock for Raven's own device (only `VulkanSetup.External` takes one) and a public redraw for a bitmap whose Vulkan image changed (`textureChanged` is internal). From [compose-raven phase 5 spec](maps/0204-compose-raven-phase-5-spec.md#not-yet-specified).
