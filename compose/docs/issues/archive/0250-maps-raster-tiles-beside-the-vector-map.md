---
status: closed
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: claude
---

# Maps: raster tiles beside the vector map

## Question

Upstream's MapView (`Rmap_view_*`: raster slippy-map tiles the app gives or reads from files,
markers, animated moves) now sits beside the fork's vector map widget, which
`compose-raven-map`'s `MapView` composable already binds. Decide whether raster tiles become a
tile source of the same `MapView`, a second composable, or stay unbound, and how markers fit. The
answer is the spec section and its implementation tickets, with the `map` port, or an
out-of-scope ruling.

## Resolution

Resolved on 2026-11-27, with the user, in three rounds: designed into
[spec section 8: Maps](../../spec.md#maps-phase-5), section 10's
[samples/map](../../spec.md#samplesmap-phase-5), section 11's change 17 and section 12.

- **A second composable** over upstream's raster widget, beside the vector one; they share a
  vocabulary (a camera, a `TileSource`), not a widget.
- **Upstream landed first, so it keeps the name**: the raster map is `MapView`, and the fork's
  becomes `VectorMapView` in Kotlin and `Rvector_map_*` in C (change 17).
- **One `TileSource`** for both, each map reading its own format, and `TileSource.files(template)`.
- **`MapView`**: markers as a list keyed by the app, `onMarkerClick`, and a `MapViewState` with
  `animateTo`, coordinate conversion and cache usage.
- The `map` port without port gaps; its demo tour, the C example showing itself, isn't ported.
- What the vector map lacks beside upstream's goes to
  [Vector map: features from upstream's MapView](../0271-vector-map-features-from-upstreams-mapview.md),
  outside this map.

Filed as [Raven: the vector map's own names](../0272-raven-the-vector-maps-own-names.md),
[Maps: VectorMapView](../0273-maps-vectormapview.md),
[Maps: MapView over upstream's raster map](../0274-maps-mapview-over-upstreams-raster-map.md) and
[samples/map](../0275-samples-map.md).
