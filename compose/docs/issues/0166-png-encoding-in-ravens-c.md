---
status: open
---

# PNG encoding in Raven's C

Give Raven a PNG encoder beside its decoder, so every binding can save pixels as an image in one
line: what previews, PR screenshots and Raven MCP's `ui.screenshot` need. Design:
[Images to files](../agent-skills.md#images-to-files).

**Depends on:** nothing open.

## Deliverables

- [ ] `Rimage_encode_png(rgba, width, height, &size)` (bytes, freed with `Rpixels_free`) and `Rimage_save_png(rgba, width, height, path)` in `raven_resources.h`, beside `Rimage_decode`. Thread-safe, straight-alpha RGBA8.
- [ ] `stb_image_write.h` vendored in `third_party/stb/`, compiled `static` in the same file as `stb_image` under `RAVEN_WITH_STB_IMAGE`; the calls fail and report it with the switch off. Listed in `THIRD_PARTY.md`.
- [ ] `R_ERROR_IO`, added at the end of `RError`, for a file that can't be written.
- [ ] Tests: a round trip through `Rimage_decode` gives the same pixels; a failure to write reports `R_ERROR_IO`.
- [ ] The bindings regenerated with `tools/bindgen`.
- [ ] The upstream proposal opened, on its own.
