---
status: open
---

# The Map widget on Windows and Linux

Build and run the Map widget, and the drawing additions it draws with, on Windows and Linux. Only
macOS has run them so far: the [native Map widget](archive/0140-a-native-map-widget.md) was built,
tested and measured on Apple Silicon alone. Both upstream proposals wait for this.

**Depends on:** nothing open. The work is on `spike/maps-native` (the widget) and `spike/maps`
(the drawing additions, which `spike/maps-native` contains).

## Deliverables

- [ ] `RAVEN_WITH_MAP` builds with MSVC on Windows and with GCC on Linux, without warnings: `src/map/`, `src/widgets/map.c`, `rvn_inflate` in `render/image_decode.c`, `tests/test_map.c` and `tests/map_benchmark.c`.
- [ ] `test_map` passes on both, with `MAPS_DATA` set to the spike's Chicago data; the drawing additions' tests too.
- [ ] `examples/vector_map` draws Chicago on both, checked by a capture against the macOS one, under each renderer the platform has.
- [ ] `bindings/python/map_example.py --test` passes on both (the `binding_python_map` CTest).
- [ ] CRC-32 on MSVC for ARM64: its CRC32 intrinsics (`__crc32d`, `__crc32b`) where the target has them, as clang and GCC's `__ARM_FEATURE_CRC32` path does; the byte table elsewhere. x86's `crc32` instruction is another polynomial, so x86 keeps the table.
- [ ] `raven_map_benchmark` run on each, its numbers recorded here beside 0140's macOS ones.
