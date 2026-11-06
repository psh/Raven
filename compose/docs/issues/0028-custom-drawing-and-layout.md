---
status: open
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: null
---

# Custom drawing and custom layout

## Question

How does compose-raven offer content Raven's widgets can't express? Decide the shape of a `Canvas`-like composable over Raven's custom widget (`Rcustom_set_paint_callback`, `Rcustom_set_measure_callback`, `RPainter` in `raven_draw.h`), how `Viewport` (`R_WIDGET_VIEWPORT`, textures, OpenGL/Vulkan) is exposed for the `editor`, `effects` and viewport examples, and whether custom `Layout { }` gets a Raven arrange hook as a required fork change.
