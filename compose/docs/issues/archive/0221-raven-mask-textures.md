---
status: closed
---

# Raven: mask textures

Add `Rtexture_create_mask_from_file_async(const char* path)` to Raven, so an image, SVG or raster,
can be drawn as a mask that a tint colours exactly. Google's icons draw black and Raven's tint
multiplies, so they can't be coloured today. Spec:
[11: change 12](../../spec.md#11-required-raven-changes). Planned in
[Icons and resources: vector icons](0207-icons-and-resources-vector-icons.md).

**Depends on:** [samples/viewport-vulkan](0220-samples-viewport-vulkan.md), the ticket before it in
phase 5's implementation sequence.

## Deliverables

- [x] `Rtexture_create_mask_from_file_async(path)` in `raven_resources.h`, documented: the image's alpha only, in `R_TEXTURE_ALPHA8`, which the shader samples as white with that alpha.
- [x] An SVG mask is redrawn at each scale as a colour texture is (the same `svg_wanted` path); a raster mask keeps its alpha channel.
- [x] A C test: a Material Symbol and a two-tone Material Icon decode with coverage where the icon is and none outside, the two-tone layer lighter; a raster image with alpha; a missing file.
- [x] One commit on `compose-raven`, the bindings regenerated, spec section 11's change 12 marked made.
- [x] An on-hold upstream ticket, as [Propose text input insets upstream](../0202-propose-text-input-insets-upstream.md).

## Resolution

Built on 2026-11-26 as `67da73d` on `compose-raven`: `Rtexture_create_mask_from_file_async(path)` in
`raven_resources.h`; in `src/render/texture.c` the async job keeps only the alpha of what it decodes
for a mask (in place, one byte a pixel) and the texture is `R_TEXTURE_ALPHA8`, which Vulkan samples as
(1, 1, 1, value) through its view's swizzle and WebGL through its alpha-only shader path, as for the
glyph atlas. An SVG mask's redraws at a new scale stay masks (the same `svg_wanted` path), and after a
lost device it is remade from its file as a mask. `Rtexture_update` refuses it, as any non-RGBA8.

- **Test** `tests/test_mask_textures.c` (43 checks, passing three runs in a row, no leaks): headless
  with a backend of its own recording `CreateTexture`, since uploads happen on the render thread at a
  frame. A Symbol-shaped SVG (grey fill, the `0 -960 960 960` box) covers its square fully and nothing
  outside, at 48 x 48; a two-tone icon's `.3` layer covers 74 to 80 of 255 and its `fill="none"` frame
  nothing; a PNG keeps its alpha whatever the colour; a missing file is reported and uploads nothing,
  a NULL path refused; shown four times bigger it is drawn again at 96 pixels, still a mask, and so
  after `Rbackend_device_lost`. The fixtures are inline shapes of Google's formats; the real
  downloaded and repository icons are [Icon and Google's icons](0222-icon-and-googles-icons.md)'s.
- The full C suite: all pass but `test_shell` and `test_themes`, which fail the same way without the
  change (`test_themes`: this machine's macOS 26 look names its theme `MacOSGlassLight`).
- The bindings regenerated; spec section 11's change 12 marked made; `api-coverage.md` updated
  (1,223 functions, the new one unbound until `Icon`).
- Upstream: [Propose mask textures upstream](../0239-propose-mask-textures-upstream.md), on hold.
