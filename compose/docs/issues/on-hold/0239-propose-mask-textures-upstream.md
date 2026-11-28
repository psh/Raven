---
status: on-hold
---

# Propose mask textures upstream

> **On hold (2026-11-26).** Nothing is released beyond the local machine for now, and opening a
> pull request on Raven's `main` publishes it.

Propose `Rtexture_create_mask_from_file_async` to Raven's `main`, on its own, as the way for any app
to colour an icon drawn in black (the Material icons, most icon sets) with a tint, the text colour
or a theme's: the image's alpha as an `R_TEXTURE_ALPHA8` texture. It doesn't mention compose-raven.
Split out of [Raven: mask textures](../archive/0221-raven-mask-textures.md), which built it (spec
section 11, change 12; commit `67da73d`).

**Depends on:** [Raven: mask textures](../archive/0221-raven-mask-textures.md).

## Deliverables

- [ ] One pull request to Raven's `main`, carrying the function and its documentation in `raven_resources.h`, the mask path in `src/render/texture.c` (decoding, SVG redraws, restoring after a lost device), `tests/test_mask_textures.c` and its line in `tests/CMakeLists.txt`, and the bindings regenerated against upstream's headers.
