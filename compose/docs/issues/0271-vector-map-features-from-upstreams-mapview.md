---
status: open
---

# Vector map: features from upstream's MapView

Upstream's raster MapView (`Rmap_view_*`) has features the fork's vector map widget lacks. Compare
the two, and add to the vector map, in Raven's C and in compose-raven's vector map composable,
those that port over easily, so the two maps offer the same things where their tiles allow.
Found while deciding [Maps: raster tiles beside the vector map](archive/0250-maps-raster-tiles-beside-the-vector-map.md).

## The comparison to start from

| | Upstream's MapView (raster) | The fork's vector map |
|---|---|---|
| Camera | centre, zoom, zoom range, a view changed callback | the same, and a tile zoom range |
| Animated moves | `Rmap_view_animate_to` | none |
| Markers | added, moved, labelled, coloured, removed, cleared, clicked | none |
| Screen and map coordinates | `Rmap_view_latlon_to_screen`, `_screen_to_latlon` | none |
| Tiles | from the app (a callback, set data or failed, cancelled) or from files by a path template | from the app (a tile source, data or failed) |
| Cache | a size and its usage | a capacity, and frame stats |
| Tile states | per tile (`Rmap_view_get_tile_state`) | counts in the stats |
| Attribution | set and get | set |
| Styling, labels, icons | none (raster) | MapLibre style, labels, sprites |

The table is a first reading of the two headers, to be checked against them.

## Deliverables

- [ ] The comparison checked and completed against both headers, recorded here: each feature the vector map lacks, whether it ports easily, and why not where it doesn't (labels collide with markers, say).
- [ ] For each that ports easily (likely animated moves, markers with clicks, coordinate conversion, tiles from files): the C function in the vector map, its documentation, a C test, the bindings regenerated and the reference updated, in one commit each on `compose-raven`.
- [ ] compose-raven's vector map composable binding them, in the same shape the raster `MapView` composable takes for them.
- [ ] `api-coverage.md` updated.
