---
status: open
---

# Propose PNG encoding upstream

> **On hold (2026-11-19).** Nothing is released beyond the local machine for now, and opening a
> pull request on Raven's `main` publishes it.

Propose `Rimage_encode_png`, `Rimage_save_png` and `R_ERROR_IO` to Raven's `main`, on their own, as
the one-line way for every binding to save pixels as an image. Split out of
[PNG encoding in Raven's C](archive/0166-png-encoding-in-ravens-c.md), which built them.

**Depends on:** [PNG encoding in Raven's C](archive/0166-png-encoding-in-ravens-c.md).

## Deliverables

- [ ] One pull request to Raven's `main`, carrying the encoder, `stb_image_write.h` and its `THIRD_PARTY.md` line, the test in `tests/test_tools.c`, and the bindings regenerated against upstream's headers.
