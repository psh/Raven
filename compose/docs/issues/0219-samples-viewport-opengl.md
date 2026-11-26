---
status: open
---

# samples/viewport-opengl

Port Raven's `examples/viewport_opengl` to `compose/samples/viewport-opengl`, over
`compose-raven-opengl`'s `OpenGLViewport`. Spec:
[10: `samples/viewport-opengl`](../spec.md#samples-viewport-opengl-phase-5). Planned in
[Designed samples: the three ports' plan](archive/0205-designed-samples-the-three-ports-plan.md).

**Depends on:** [samples/effects](0218-samples-effects.md).

## Deliverables

- [ ] The module `samples/viewport-opengl`, with its launch script.
- [ ] The scene drawn in OpenGL 3.2 core (a vertex and a fragment shader, a vertex buffer): the same spinning shape as the C example's GL 1.1 scene, which the module's core context can't run. The deviation is written in the sample and on this ticket.
- [ ] The speed slider, the wireframe switch and the label showing `GL_RENDERER`, or the C example's message when no context can be made.
- [ ] Headless tests: the controls work and the viewport gives its warning. With `RAVEN_GPU_TESTS=1`: a frame is drawn and read back.
- [ ] The port gap on this ticket and in `roadmap.md`: Linux, which waits for GLX on Linux (graphics extras).
