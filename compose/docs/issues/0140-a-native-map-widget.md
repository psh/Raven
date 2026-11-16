---
status: in-progress
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
Chicago). **Both optimised**: the Kotlin release test binary (`linkReleaseTestMacosArm64`,
`NativeComparisonBenchmark`) against C in a Release build (`raven_map_benchmark`), so the
comparison is about the code and not the build. Medians: 10 preparations, 30 frames, each frame
panned a hundredth of a unit.

| Measure | Kotlin, optimised | C widget |
|---|---|---|
| Dense 530 KB tile (14/4203/6089): decode | 12.4 ms | 0.9 ms |
| Dense tile: style and triangulate, without labels | 37.0 ms | 5.7 ms |
| Dense tile: style and triangulate, with labels | 57.0 ms | (step 3) |
| All 61 tiles, without labels: decode, then style and triangulate | 155 + 916 ms | 14 + 138 ms |
| 800 × 600 at zoom 14 (4 tiles, 43,903 triangles, 36,140 segments): fills | 0.52 ms | 0.49 ms |
| Same view: lines | 3.40 ms | 0.74 ms |
| 1200 × 800 at zoom 14 (12 tiles, 110,212 triangles, 75,614 segments): fills, lines | not run | 1.18, 1.46 ms |
| Labels a frame, placement reused / placed every frame | | (step 3) |
| Memory for 160 prepared tiles | | (step 2) |

Measured 2026-11-15, step 1. Findings:

- **The spike's earlier figures were from a debug build and a smaller view.** Its ~95 and ~210 ms
  for the dense tile were the unoptimised test binary; its "headless" view was the test
  harness's 800 × 600 window, not the 1200 × 800 asked for.
- **Both draw the same map**: the same triangles and segments at both sizes, and the C widget's
  window capture looks like the spike's (Liberty without its labels).
- **Fills cost the same**, as expected: both end in one `Rdraw_triangles` call per tile and layer.
- **Lines cost the Kotlin side its own bookkeeping, not Raven**: drawing a segment a call, it takes
  0.82 ms; batched, its `LineShapes` builds a string key per tile and layer and re-reads the
  style per tile each frame. The C widget keeps a fixed slot per tile and layer.
- **Preparing tiles is where the languages differ: 6 to 14 times faster in C.**

## Progress

- **Step 1, done (2026-11-15)**, on `spike/maps-native`: `src/map/` (JSON, tile decoding, earcut,
  style and expressions, dashes and gaps), `src/widgets/map.c`, `RAVEN_WITH_MAP`, the bindings
  regenerated, `tests/test_map.c` (96 checks), `tests/map_benchmark.c`, `examples/map`.
  Deviations from the sketch: a small JSON reader of its own instead of a vendored one (as the
  spike has); `Rmap_tile_loaded` prepares at once on the UI thread until step 2; gzipped tiles
  are not read yet (the offline set isn't compressed). `test_shell` and `test_themes` fail on this
  machine with or without the map.

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
