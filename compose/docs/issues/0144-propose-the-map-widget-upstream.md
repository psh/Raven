---
status: open
---

# Propose the Map widget upstream

Propose Raven's Map widget (`R_WIDGET_MAP`, `Rmap_*`) to Raven's `main`, behind `RAVEN_WITH_MAP`,
so every binding can show a vector map. Second of the two proposals the
[native Map widget](archive/0140-a-native-map-widget.md) recommends; compose-raven's `MapView`
follows it onto `compose-raven`.

**Depends on:** [0141](0141-map-widget-on-windows-and-linux.md) (Windows and Linux),
[0142](archive/0142-the-map-spikes-kotlin-drawing.md) (the spike's Kotlin drawing settled), and
[0143](0143-propose-the-drawing-additions-upstream.md) (the drawing calls it draws with, merged).

## Deliverables

- [ ] Decide whether `RAVEN_WITH_MAP` is on or off by default upstream, from what it adds to the library's size; record it here.
- [ ] A branch off `main` with the widget alone: `src/map/`, `src/widgets/map.c`, `rvn_inflate`, its tests, `examples/vector_map`, `bindings/python/map_example.py`, and the regenerated bindings. No compose-raven, and no spike data in the tree: tests that need it skip without `MAPS_DATA`.
- [ ] A pull request against `main`, its description carrying 0140's measurements; its link recorded here.
- [ ] Once merged: `compose-raven-map` (`MapView`) and the `MapCameraChanged` binding event moved from `spike/maps-native` onto `compose-raven`, with their tests.
