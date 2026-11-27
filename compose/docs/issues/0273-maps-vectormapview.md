---
status: open
---

# Maps: VectorMapView

Rename `compose-raven-map`'s vector `MapView` to `VectorMapView`, with `VectorMapViewState` and
`rememberVectorMapViewState`, freeing `MapView` for upstream's raster map. Spec:
[8: Maps](../spec.md#maps-phase-5). Planned in [Maps: raster tiles beside the vector map](archive/0250-maps-raster-tiles-beside-the-vector-map.md).

**Depends on:** [Raven: the vector map's own names](0272-raven-the-vector-maps-own-names.md).

## Deliverables

- [ ] `VectorMapView`, `VectorMapViewState` and `rememberVectorMapViewState`, over the renamed C API; `MapStyle`, `TileSource` and `MapStats` keep their names.
- [ ] `MapViewTest` renamed and passing; `spikes/maps` and anything else using the vector map moved over.
- [ ] No `MapView` left in `compose-raven-map` until the raster one is built.
