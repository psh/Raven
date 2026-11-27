---
status: open
---

# Raven: the vector map's own names

Rename the fork's vector map widget's C API so that upstream's map keeps `Map`: `Rmap_*` to
`Rvector_map_*`, `R_WIDGET_MAP` to `R_WIDGET_VECTOR_MAP`, `RAVEN_WITH_MAP` to
`RAVEN_WITH_VECTOR_MAP`. Spec: [11: change 17](../spec.md#11-required-raven-changes). Planned in [Maps: raster tiles beside the vector map](archive/0250-maps-raster-tiles-beside-the-vector-map.md).

**Depends on:** [samples/web-view](0270-samples-web-view.md), the ticket before it in phase 5's implementation sequence.

## Deliverables

- [ ] The functions, types (`RMapStats` and the others), the widget type and the build option renamed in `raven_controls.h`, `src/widgets/map.c`, `src/map/`, CMake and the docs; the files renamed where their names say map (`tests/test_map.c` to `test_vector_map.c`, `map_benchmark.c`).
- [ ] `examples/vector_map`, `bindings/python/map_example.py` and compose-raven's cinterop use moved over; nothing of upstream's MapView touched.
- [ ] The C tests and `bindgen.py --check` passing; the bindings regenerated and the reference updated, in one commit on `compose-raven`.
- [ ] Spec section 11's change 17 marked made; [Propose the Map widget upstream](0144-propose-the-map-widget-upstream.md) checked against the new names.
