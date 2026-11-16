---
status: open
---

# A native Map widget in Raven

Port the maps spike from Kotlin into Raven's C code, as a `Map` widget (`R_WIDGET_MAP`, `Rmap_*`)
in the same shape as `Plot`. Every binding (C++, C#, Go, Java, Python, Rust, Zig, Node, Bun,
LuaJIT, Odin, C3) could then show a vector map, and compose-raven's map shrinks to a thin
composable over the widget. The Kotlin spike stays as the reference, and the two are measured
against each other: this is a spike first, and the comparison is part of what it answers.

**Branch:** `spike/maps-native`, from `spike/maps`, so it has the seven drawing additions and the
Kotlin spike to compare against.

**Reference:** the maps row of [`spikes/README.md`](../../spikes/README.md) on `spike/maps`, and
`compose/spikes/maps/` there (about 2,550 lines of Kotlin, tests in `MapsTest.kt`).

## What moves into C, and what doesn't

| Part | Kotlin (spike) | In C |
|---|---|---|
| Mapbox Vector Tile decoding | `VectorTile.kt`, 217 lines | Plain protobuf parsing by hand, as the spike does |
| Polygon triangulation | `Earcut.kt`, 356 | A port of earcut (it started as JS, and a C++ port exists) |
| Dashes and line gaps | `LineStyle.kt`, 171 | As is |
| Style JSON and its expressions | `Style.kt` and `Json.kt`, about 400 | Needs a JSON parser: vendor a small one (cJSON or similar) into `third_party/`, beside stb and nanosvg. The expression subset the spike evaluates (`get`, `match`, `case`, `coalesce`, `interpolate`/`step` on zoom, comparisons) is the fiddliest part |
| Label and icon placement, collision, keeping placement while you pan | `Labels.kt`, 596 | Geometry over Raven's own text measuring |
| Tile store: wanted set, LRU of 160, retry after 10 s, nearest ready ancestor | `Tiles.kt`, 293 | Tiles prepared with `Rtask_run_async`, results handed back with `Rtask_post` |
| Camera, dragging, wheel zoom | `MapView.kt`, 286 | The widget's own input, as `Plot` handles its own |
| Fetching tiles, style and sprites | `TileSources.kt`, 148 (Ktor) | **Stays with the app**: Raven has no HTTP client and shouldn't gain one for this |

Gzipped tiles can be inflated with stb's zlib decoder (`stbi_zlib_decode_buffer`), so that adds no
new dependency. The sprite sheet's PNG loads through Raven's existing image loading.

## C API sketch

To be settled by the work, not fixed here:

```c
Rid map = Rwidget_create(R_WIDGET_MAP);

/* Style: MapLibre style JSON, and the sprite sheet it names (JSON index + PNG bytes). */
Rbool Rmap_set_style_json(Rid map, const char* json, Rsize length);
Rbool Rmap_set_sprite(Rid map, const char* index_json, Rsize index_length,
                      const void* png, Rsize png_length);

/* Camera. */
void   Rmap_set_center(Rid map, double latitude, double longitude);
void   Rmap_get_center(Rid map, double* latitude, double* longitude);
void   Rmap_set_zoom(Rid map, double zoom);
double Rmap_get_zoom(Rid map);

/* Tiles come from the app: the map asks, the app answers whenever it can, from any thread. */
typedef void (*RMapTileRequestCallback)(Rid map, int z, int x, int y, void* user_data);
typedef void (*RMapTileCancelCallback)(Rid map, int z, int x, int y, void* user_data);
void Rmap_set_tile_source(Rid map, int min_zoom, int max_zoom,
                          RMapTileRequestCallback request, RMapTileCancelCallback cancel,
                          void* user_data);
void Rmap_tile_loaded(Rid map, int z, int x, int y, const void* bytes, Rsize length); /* Thread-safe; copies */
void Rmap_tile_failed(Rid map, int z, int x, int y);                                 /* Thread-safe */

void Rmap_set_attribution(Rid map, const char* text);

typedef void (*RMapCameraChangedCallback)(Rid map, void* user_data);
void Rmap_set_camera_changed_callback(Rid map, RMapCameraChangedCallback callback, void* user_data);
```

Each binding answers `request` with its own HTTP client and disk cache. compose-raven's version is
the spike's Ktor fetcher feeding `Rmap_tile_loaded`.

## Steps

Each step ends with the C widget drawing Chicago alongside the Kotlin spike, measured the same way.

1. **Static map**: decoding, triangulation, style (fill, fill-extrusion drawn flat, line), dashes
   and gaps, drawn from tiles handed in. The `MAPS_OFFLINE` tiles of central Chicago are enough.
2. **Tile store**: the tile-source callbacks, the wanted set, LRU, retry, stand-in ancestors,
   preparation on worker threads.
3. **Labels and icons**: point labels, labels along lines, kept placement, sprites, road shields,
   one-way arrows, the style's faces.
4. **compose-raven over it**: a `Map()` composable over `R_WIDGET_MAP`, the spike's `MapView`
   replaced by it, and one other binding (Python, say) showing the same map to prove the point.

## Performance comparison

Both sides measured on the same machine, same view and same tiles (the `MAPS_OFFLINE` set,
Chicago). **Both optimised**: the Kotlin release binary (not the test build, which the spike's
headless numbers came from) against C at `-O2`, so the comparison is about the code and not the
build. Medians of 30 frames, as in the spike.

| Measure | Kotlin spike (2026-11-14) | C widget |
|---|---|---|
| Preparing the dense 530 KB tile: decode | ~95 ms (test build) | |
| Preparing the dense tile: style and triangulate | ~210 ms (test build) | |
| Headless view (43,903 triangles, 36,140 segments): fills | 0.3 ms | |
| Same view: lines | 2.2 ms | |
| Labels a frame, placement reused, while panning or zooming | 2.0–2.4 ms (test build) | |
| Labels a frame, placing every frame | 16.3–17.3 ms (test build) | |
| In the window, the Loop at zoom 14: frame | 6.2 ms | |
| In the window: one label placement | 4.2 ms | |
| Memory for 160 prepared tiles | not measured | |

Re-measure the Kotlin column optimised before comparing; the test-build figures are only a
starting point. The drawing figures should come out close, since both end in the same
`Rdraw_triangles`/`Rdraw_polyline` calls. Tile preparation and label placement are where the
languages differ.

## Deliverables

- [ ] `R_WIDGET_MAP` and `Rmap_*` in `raven_controls.h`, behind a build option (`RAVEN_WITH_MAP`)
      so builds that don't want it, and the vendored JSON parser, leave it out.
- [ ] `src/widgets/map.c` and its parts (tile decoding, earcut, style, labels, tile store).
- [ ] Tests in `tests/`, ported from `MapsTest.kt`: decoding, triangulation, filters and
      expressions, dashes, placement, and the tile store against hand-made tiles.
- [ ] A benchmark that measures both implementations as above, with results filled into the table.
- [ ] Step 4: compose-raven's `Map()` over the widget, and a second binding showing a map.
- [ ] A verdict: keep the map in C, keep it in Kotlin, or split it, and which Raven changes to
      propose upstream.
