---
status: closed
---

# samples/viewport-opengl

Port Raven's `examples/viewport_opengl` to `compose/samples/viewport-opengl`, over
`compose-raven-opengl`'s `OpenGLViewport`. Spec:
[10: `samples/viewport-opengl`](../spec.md#samples-viewport-opengl-phase-5). Planned in
[Designed samples: the three ports' plan](archive/0205-designed-samples-the-three-ports-plan.md).

**Depends on:** [samples/effects](archive/0218-samples-effects.md).

## Deliverables

- [x] The module `samples/viewport-opengl`, with its launch script.
- [x] The scene drawn in OpenGL 3.2 core (a vertex and a fragment shader, a vertex buffer): the same spinning shape as the C example's GL 1.1 scene, which the module's core context can't run. The deviation is written in the sample and on this ticket.
- [x] The speed slider, the wireframe switch and the label showing `GL_RENDERER`, or the C example's message when no context can be made.
- [x] Headless tests: the controls work and the viewport gives its warning. With `RAVEN_GPU_TESTS=1`: a frame is drawn and read back.
- [x] The port gap on this ticket and in `roadmap.md`: Linux, which waits for GLX on Linux (graphics extras).

## Resolution

Built on 2026-11-26: `compose/samples/viewport-opengl` (`Main.kt`, `Cube.kt`, `Scene.apple.kt`, a
`Scene.linux.kt` giving no scene, `launch-viewport-opengl`), registered in `settings.gradle.kts`,
listed in `compose/README.md` and the roadmap.

- **The deviation**: the C example's OpenGL 1.1 (`glBegin(GL_QUADS)`, `glFrustum`, `glRotatef`)
  can't run in the module's 3.2 core context, so the cube is a vertex buffer drawn by a `#version
  150` vertex and fragment shader, its matrix `cubeTransform` worked out as glFrustum, glTranslatef
  and glRotatef would. Same faces, colours, angle and background. Written in `Main.kt`'s header.
- **Wireframe** draws each face's four edges with `GL_LINES`, not `glPolygonMode(GL_LINE)` on the
  triangles, which would add a diagonal across every face that the C example's quads don't have (a
  test caught it).
- **The angle** is state a `withFrameNanos` loop advances by the speed each second, running only
  while the speed isn't 0; the viewport draws again when the angle or the wireframe changes, so no
  `realtime`.
- **The label** shows the C example's message until `onInit` gives `GL_RENDERER`; where no context
  is made (headless, Linux) it stays.
- **Tests** (8, passing): the controls, the headless warning and message, the angle at speed 1 and 0,
  the transform's numbers; the scene drawn into an `OpenGLTexture` and read back with
  `glReadPixels` (front face in the middle over the background, the wireframe's middle empty with an
  edge across, another face when turned). This read-back runs headless on every macOS run, not only
  with `RAVEN_GPU_TESTS=1`. With it, the sample on a real window gives its renderer and turns:
  passed run from the test binary (`RAVEN_GPU_TESTS=1 .../debugTest/test.kexe`); through Gradle the
  variable didn't reach the test process (the daemon's environment), so the test returned early.
- Checked by eye in the release build: the turning cube and "Apple M1 Max".
- **Port gap**: Linux, which waits for GLX on Linux (graphics extras), as `roadmap.md` says.
