---
status: on-hold
---

# Propose the maps spike's drawing additions upstream

> **On hold (2026-11-27).** Nothing is released beyond the local machine for now, and opening a
> pull request on Raven's `main` publishes it.

Propose the drawing calls the maps spike added to Raven, to Raven's `main`, on their own: they are
useful to any custom drawing, map or not, and the Map widget draws with them. First of the two
proposals the [native Map widget](../archive/0140-a-native-map-widget.md) recommends.

**Depends on:** [0141](../0141-map-widget-on-windows-and-linux.md), the additions run on Windows and
Linux.

The additions, each its own commit on `spike/maps`:

- `6353256` `Rdraw_triangles`, `Rdraw_polyline`: many triangles or lines in one call
- `b335c93` `R_INSTANCE_ALIASED`: meshes filled without anti-aliasing
- `b3a1d00` `Rdraw_text_rotated`: text turned about its top left corner
- `e7602a3` `Rdraw_image_region`: part of a texture, such as an icon of a sprite sheet
- `6f844b2` `Rdraw_image_rotated`: part of a texture turned about its centre
- `3b6a27c` `Rdraw_polyline` line caps: round, butt or square

## Deliverables

- [ ] A branch off `main` with the additions alone, rebased and tested there (no map, no compose-raven).
- [ ] Each documented in its header as Raven's other drawing calls are; bindings regenerated (`bindings_up_to_date`).
- [ ] Tests for each, and a capture or example showing them, independent of the map.
- [ ] A pull request against `main`, its description saying what each is for and what uses it; its link recorded here.
