---
status: open
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: null
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
