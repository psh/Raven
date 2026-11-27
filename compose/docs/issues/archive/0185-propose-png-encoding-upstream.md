---
status: wont-fix
---

# Propose PNG encoding upstream

> **Won't fix (2026-11-26).** Upstream shipped `Rimage_encode_png` and `Rimage_save_png` of its own
> (`src/render/image_encode.c`, no stb). Merging upstream into compose-raven, the fork took upstream's
> and dropped its encoder, `stb_image_write.h` and `R_ERROR_IO`: there is nothing left to propose.

Propose `Rimage_encode_png`, `Rimage_save_png` and `R_ERROR_IO` to Raven's `main`, on their own, as
the one-line way for every binding to save pixels as an image. Split out of
[PNG encoding in Raven's C](0166-png-encoding-in-ravens-c.md), which built them.

**Depends on:** [PNG encoding in Raven's C](0166-png-encoding-in-ravens-c.md).

## Deliverables

- [x] ~~One pull request to Raven's `main`, carrying the encoder, `stb_image_write.h` and its `THIRD_PARTY.md` line, the test in `tests/test_tools.c`, and the bindings regenerated against upstream's headers.~~ Not needed: upstream has it.
