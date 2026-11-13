---
status: open
---

# A compose-raven-lottie module

An idea to revisit after phase 3: an opt-in module that plays Lottie animations with Compose's
familiar API, over ThorVG. No sample needs it, so it waits in the phase 5 fog
([compose-raven phase 3 spec](0023-compose-raven-phase-3-spec.md#not-yet-specified)). This ticket
keeps what was found on 2026-11-12, so nothing has to be looked up again.

## What was found

### Existing Lottie libraries for Compose don't work with compose-raven

Both need Compose UI, which compose-raven doesn't use (Raven lays out and draws its own widgets):

- **Compottie** (`io.github.alexzhirkevich:compottie` 2.3.3): published for Android, JVM desktop,
  iOS, `macosArm64`, JS and Wasm; no `linuxX64`. Its macOS artifact depends on JetBrains' Compose
  Foundation, and `compottie-core` adds Skiko (Skia): an animation is a Compose `Painter` drawn into
  Compose UI's `DrawScope`. Rendering it off-screen into an `ImageBitmap` would bring a second
  rendering stack (Skia beside Raven's Vulkan), copy every frame through the CPU, and still not run
  on Linux.
- **Kottie** (`io.github.ismai117:kottie` 2.3.0): published for Android, iOS, JVM, JS and Wasm; no
  `macosArm64` and no `linuxX64`. Its common dependencies are Compose UI, Foundation, Material 3,
  Compose resources and Ktor.

### Native renderers that could sit under compose-raven

| Library | API | Lottie | Fit |
|---|---|---|---|
| **ThorVG** | C++ with an official C API (`thorvg_capi.h`) | yes, actively developed; also SVG | **chosen**: its CPU engine renders into a buffer we own; GPU engines are OpenGL and WebGPU, no Vulkan |
| rlottie (Samsung) | C++ with a C API | yes, quieter development, some gaps | CPU buffer, the same route |
| Skia + Skottie | C++ only | yes, the most complete | has a Vulkan backend that could render on Raven's device (`RavenVulkan.device`, `ImageBitmap.fromVulkanImageView`), but needs a C++ wrapper and is very large |
| NanoVG (+ community Vulkan ports) | C | no, vector drawing only | the animation part would be ours |
| Vello / Velato | Rust over wgpu | Velato does | its own GPU device; not a C fit |

compose-raven's phase 3 offers three ways in: `Viewport(image: ImageBitmap)` with
`ImageBitmap.update` (pixels), `VulkanViewport(onDraw)` in `compose-raven-vulkan` (commands into
Raven's command buffer), and `ImageBitmap.fromVulkanImageView` (zero copy). All are ordinary
widgets, so they layer over a `Canvas` in a `Box`; `NativeViewport` doesn't (it sits above the UI).

### The ThorVG spike passes

[`spikes/thorvg/`](../../spikes/thorvg/), with ThorVG 1.1.3 from Homebrew (`brew install thorvg`:
CPU engine, all loaders, the C API, `libomp`; bottles for macOS arm64 and Linux arm64/x86_64). The
result is also in [`spikes/README.md`](../../spikes/README.md).

- **Real window** (`run.sh`, `window.c`): a hand-written Lottie (`dot.json`, an orange dot sliding
  over 60 frames on a transparent background) rendered each frame by ThorVG's CPU canvas into a
  straight-alpha RGBA buffer (`TVG_COLORSPACE_ABGR8888S`, the bytes `Rtexture_create_from_pixels`
  takes), shown in a texture viewport above a custom widget painting stripes, in an Overlay.
  Captures at frames 0 and 59 show the dot moving with the stripes through and around it. A 256×256
  frame took about **0.07 ms** on the CPU.
- **Kotlin/Native** (`./gradlew :spikes:thorvg:allTests`): a cinterop for `thorvg_capi.h`
  (`thorvg.def`); a small player renders frames into native memory (ThorVG keeps the pointer for
  the canvas's life, so not a pinned array) with the right pixels at frames 0 and 59, and hands a
  frame to a Raven texture, then updates it, inside compose-raven's harness.
- Seen: a texture rendered at 256 and shown at 512 on a Retina screen has soft edges; render at the
  viewport's pixel size instead.

## The idea

Copy the API Compose developers know: Airbnb's `lottie-compose`, which Compottie mirrors.

```kotlin
val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(json)) // or .File(path), .Resource(...)
val progress by animateLottieCompositionAsState(
    composition, iterations = LottieConstants.IterateForever, isPlaying = true, speed = 1f,
)
LottieAnimation(composition, progress = { progress }, modifier = Modifier.size(120.dp),
    contentScale = ContentScale.Fit)
```

- **`LottieComposition`** wraps a parsed ThorVG animation: the picture loaded once, its total
  frames, frame rate and size. Loaded asynchronously and cached per host, like `painterResource`,
  and freed with the host (the host epoch).
- **`LottieAnimation`** is a `Viewport(image)` over an `ImageBitmap` it owns. It renders at the
  viewport's pixel size (`Viewport(onSizeChanged)`), so edges stay sharp; the frame is `progress`
  times the total frames; `ImageBitmap.update` redraws it. Rendering can run off the UI thread, as
  [The editor's scene viewport](archive/0101-the-editors-scene-viewport.md) decided, with `update`
  on the UI thread.
- **`animateLottieCompositionAsState`** is a `withFrameNanos` loop on compose-raven's frame clock, so
  it obeys `RavenAnimation.speed`/`.enabled` and the system's reduced motion
  ([Animations beyond appear transitions](archive/0103-animations-beyond-appear-transitions.md)), and
  tests drive it with `mainClock`.
- **Layering**: an ordinary widget, so `Box { Canvas { ... }; LottieAnimation(...) }` puts it over a
  custom canvas, as the spike does.
- **Module**: opt-in `compose-raven-lottie`, like `compose-raven-vulkan` and `compose-raven-opengl`,
  with its own cinterop to ThorVG, so apps without Lottie don't link it. It needs only phase 3's
  `Viewport`, `ImageBitmap`, `onSizeChanged` and animation clock.
- **Vector icons too**: ThorVG also loads SVG, so the same native dependency could answer phase 5's
  vector-icons fog, for example `painterResource("icon.svg")` rasterised at the right size.

## Open questions

- **Linking and packaging**: link ThorVG statically, or bundle its library (on macOS in
  `Contents/Frameworks`, beside MoltenVK); build it from source with only the loaders needed
  (Lottie, maybe SVG) to keep it small; how CI gets it on macOS and Linux; Windows (phase 4's
  `mingwX64`).
- **Formats**: plain Lottie JSON only, or dotLottie (`.lottie`, zipped, with themes and several
  animations) too.
- **Dynamic properties**: Compottie-style run-time changes to layers (colours, text), and whether
  ThorVG's Lottie extensions (`thorvg_lottie.h`, slots and markers) cover them.
- **Rendering thread and cost**: when to render off the UI thread, and caching frames for small
  looping animations.
- **API scope**: which parts of `lottie-compose` to keep (clip specs, markers, `LottieConstants`,
  `LottieCompositionSpec` kinds) under "keep it slim" and "favour Compose conventions".
- **Vector icons**: whether to fold SVG icons into this module or keep them separate.
