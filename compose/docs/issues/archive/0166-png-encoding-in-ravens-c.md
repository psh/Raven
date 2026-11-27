---
status: closed
---

# PNG encoding in Raven's C

Give Raven a PNG encoder beside its decoder, so every binding can save pixels as an image in one
line: what previews, PR screenshots and Raven MCP's `ui.screenshot` need. Design:
[Images to files](../../agent-skills.md#images-to-files).

**Depends on:** nothing open.

## Deliverables

- [x] `Rimage_encode_png(rgba, width, height, &size)` (bytes, freed with `Rpixels_free`) and `Rimage_save_png(rgba, width, height, path)` in `raven_resources.h`, beside `Rimage_decode`. Thread-safe, straight-alpha RGBA8.
- [x] `stb_image_write.h` vendored in `third_party/stb/`, compiled `static` in the same file as `stb_image` under `RAVEN_WITH_STB_IMAGE`; the calls fail and report it with the switch off. Listed in `THIRD_PARTY.md`.
- [x] `R_ERROR_IO`, added at the end of `RError`, for a file that can't be written.
- [x] Tests: a round trip through `Rimage_decode` gives the same pixels; a failure to write reports `R_ERROR_IO`.
- [x] The bindings regenerated with `tools/bindgen`.
- [ ] The upstream proposal opened, on its own: moved to [Propose PNG encoding upstream](0185-propose-png-encoding-upstream.md), won't fix: upstream has its own.

## Resolution

Done, on macOS (arm64), except the upstream proposal: that publishes outside this machine, so it
moved to [Propose PNG encoding upstream](0185-propose-png-encoding-upstream.md), won't fix: upstream has its own.
`test_tools` passes with stb (457 checks) and without it (`RAVEN_WITH_STB_IMAGE=OFF`, 441). The
rest of `ctest` passes apart from `test_shell` and `test_themes`, which already failed (see
[Idle and the window list](0168-idle-and-the-window-list.md)). `./gradlew :raven-mcp:check`
passes.

- **The calls** (`raven_resources.h`, `render/image_decode.c`): `Rimage_encode_png(rgba, width,
  height, &size)` returns the bytes from Raven's allocator, so `Rpixels_free` releases them.
  `Rimage_save_png(rgba, width, height, path)` writes through `rvn_write_file`, so the path is UTF-8
  on Windows too. Both are thread-safe, and every failure is reported:
  - `R_ERROR_INVALID_VALUE` for no pixels, a size of 0 or less, or one too large for
    stb_image_write's `int`s. A missing path is reported the same way.
  - `R_ERROR_OUT_OF_MEMORY` when encoding fails.
  - `R_ERROR_IO` when the file can't be written.
  - `R_ERROR_INVALID_OPERATION` when built without `RAVEN_WITH_STB_IMAGE`.
- **`R_ERROR_IO`**: added at the end of `RError`, with its name in `Rerror_to_string`.
- **stb_image_write 1.19**, from `nothings/stb`'s master branch, unchanged, in
  `third_party/stb/stb_image_write.h`. It is compiled `static` in `image_decode.c` beside stb_image,
  with no stdio and on Raven's allocator, and listed in `THIRD_PARTY.md`.
- **Tests** (`tests/test_tools.c`, beside `Rimage_decode`'s): a 3 x 2 image with straight
  alpha (0, 40, 128, 254, 255) comes back from `Rimage_decode` byte for byte, in memory and through
  a file, and starts with PNG's signature. A folder that doesn't exist reports `R_ERROR_IO` naming
  the file. A bad size, no pixels and no path are refused and reported. Without stb, both calls
  fail with `R_ERROR_INVALID_OPERATION`.
- **Bindings**: regenerated (1159 functions). Python's `Rimage_encode_png` returns `(address,
  size)` like the binding's other raw calls, checked by hand. Friendlier forms belong to each
  binding's idioms.
