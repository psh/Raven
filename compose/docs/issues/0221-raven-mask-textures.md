---
status: open
---

# Raven: mask textures

Add `Rtexture_create_mask_from_file_async(const char* path)` to Raven, so an image, SVG or raster,
can be drawn as a mask that a tint colours exactly. Google's icons draw black and Raven's tint
multiplies, so they can't be coloured today. Spec:
[11: change 12](../spec.md#11-required-raven-changes). Planned in
[Icons and resources: vector icons](archive/0207-icons-and-resources-vector-icons.md).

**Depends on:** [samples/viewport-vulkan](archive/0220-samples-viewport-vulkan.md), the ticket before it in
phase 5's implementation sequence.

## Deliverables

- [ ] `Rtexture_create_mask_from_file_async(path)` in `raven_resources.h`, documented: the image's alpha only, in `R_TEXTURE_ALPHA8`, which the shader samples as white with that alpha.
- [ ] An SVG mask is redrawn at each scale as a colour texture is (the same `svg_wanted` path); a raster mask keeps its alpha channel.
- [ ] A C test: a Material Symbol and a two-tone Material Icon decode with coverage where the icon is and none outside, the two-tone layer lighter; a raster image with alpha; a missing file.
- [ ] One commit on `compose-raven`, the bindings regenerated, spec section 11's change 12 marked made.
- [ ] An on-hold upstream ticket, as [Propose text input insets upstream](0202-propose-text-input-insets-upstream.md).
