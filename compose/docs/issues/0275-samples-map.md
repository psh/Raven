---
status: open
---

# samples/map

Port Raven's `examples/map`. Spec: [10: samples/map](../spec.md#samplesmap-phase-5). Planned in [Maps: raster tiles beside the vector map](archive/0250-maps-raster-tiles-beside-the-vector-map.md).

**Depends on:** [Maps: MapView over upstream's raster map](0274-maps-mapview-over-upstreams-raster-map.md).

## Deliverables

- [ ] `samples/map`: tiles drawn offline by a Kotlin `TileSource` (or read from files), the cities' markers, World, Europe, Americas and Asia, zoom in and out, a marker's click flying to it, the place and cache usage in a label; `RAVEN_MAP_DEMO`'s tour left out.
- [ ] A headless test checking each control.
- [ ] No port gaps; the roadmap's sample table and `api-coverage.md` updated.
