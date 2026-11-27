---
status: open
---

# Maps: MapView over upstream's raster map

Bind upstream's raster MapView as `MapView(tiles, state, markers, onMarkerClick, ...)` in
`compose-raven-map`. Spec: [8: Maps](../spec.md#maps-phase-5) and section 12. Planned in [Maps: raster tiles beside the vector map](archive/0250-maps-raster-tiles-beside-the-vector-map.md).

**Depends on:** [Maps: VectorMapView](0273-maps-vectormapview.md).

## Deliverables

- [ ] `MapView(tiles, modifier, state, markers, onMarkerClick, attribution, zoomRange, tileSize, cacheSize)` over `Rmap_view_*`, tiles asked for through `TileSource` (set, failed, cancelled), on `Dispatchers.Default`.
- [ ] `TileSource.files(pathTemplate)` over `Rmap_view_set_tile_path`.
- [ ] `MapViewState` and `rememberMapViewState`: latitude, longitude and zoom following the view callback; `animateTo`, `latLonToScreen`, `screenToLatLon`, `cacheUsage()`.
- [ ] `MapMarker` and the markers' list diffed by key; `onMarkerClick`.
- [ ] Headless tests: tiles asked for and cancelled, a failed tile, markers added, moved and removed, a click, the view followed, `animateTo` returning.
- [ ] The XML translator's `<MapView>` and `<Marker>`.
- [ ] The spec's sections checked against what was built; `api-coverage.md` updated (the MapView row and the numbers).
