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

/* Tiles come from the app: the map asks, the app answers whenever it can, from any thread.
   (Built as sketched in step 2, with Rmap_set_tile_capacity beside it.) */
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
| Dense tile: style and triangulate, with labels | 56.3 ms | 9.2 ms |
| All 61 tiles, without labels: decode, then style and triangulate | 155 + 916 ms | 14 + 138 ms |
| All 61 tiles prepared together, on worker threads: until all are ready | 340 ms (`Dispatchers.Default`) | 25 ms (8 workers) |
| 800 × 600 at zoom 14 (4 tiles, 43,903 triangles, 36,140 segments): fills | 0.52 ms | 0.49 ms |
| Same view: lines | 3.40 ms | 0.74 ms |
| 1200 × 800 at zoom 14 (12 tiles, 110,212 triangles, 75,614 segments): fills, lines | not run | 1.18, 1.46 ms |
| Labels, 800 × 600 at zoom 14: a frame reusing the placement (panning; zooming) | 0.22 ms; 0.22 ms | 0.10 ms; 0.10 ms |
| Same: a placement (zooming, 84 of 1,480 placed, 36 icons; resting, 92, 47 icons) | 2.52 ms; 2.68 ms | 0.45 ms; 0.95 ms |
| Same: a frame placing again each time | 2.88 ms | 1.17 ms |
| Memory held by prepared tiles | not measured (garbage-collected) | 24.1 MB for the 61, about 0.4 MB a tile: about 60 MB at the 160 kept |

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
- **Labels place the same in both** (step 3): the same counts of labels and icons placed of the
  same candidates, in a third to a sixth of the time in C; a frame reusing the placement costs
  half.
- **Prepared together, the gap widens to 13 times** (step 2, 2026-11-15): C's 8 workers finish
  6.6 times faster than its summed preparation time, Kotlin's `Dispatchers.Default` about 3 times.

## Progress

- **Step 1, done (2026-11-15)**, on `spike/maps-native`: `src/map/` (JSON, tile decoding, earcut,
  style and expressions, dashes and gaps), `src/widgets/map.c`, `RAVEN_WITH_MAP`, the bindings
  regenerated, `tests/test_map.c` (96 checks), `tests/map_benchmark.c`, `examples/map`.
  Deviations from the sketch: a small JSON reader of its own instead of a vendored one (as the
  spike has); `Rmap_tile_loaded` prepares at once on the UI thread until step 2; gzipped tiles
  are not read yet (the offline set isn't compressed). `test_shell` and `test_themes` fail on this
  machine with or without the map.
- **Step 2, done (2026-11-15)**: `Rmap_set_tile_source` (request and cancel callbacks, told outside
  painting), `Rmap_tile_loaded` and `Rmap_tile_failed` thread-safe (bytes posted to the interface,
  prepared on a worker with a reference to the style, posted back; a map or style gone meanwhile
  frees what lands), `Rmap_set_tile_capacity` (160), stand-in ancestors, cancellation, retry after
  10 s, memory in `RMapStats`. `test_map` ports the spike's `TileStoreTest` (139 checks), clean
  under ThreadSanitizer and over 30 runs; `examples/map` reads tiles as the map asks. One change
  from the spike: below the source's lowest zoom the map draws and asks for nothing, as MapLibre
  does; listing tiles there cost millions of lookups a frame far out (the spike has the same flaw).
  A tile handed in unasked is kept, and never cancelled. The disk cache stays with the
  application, as the HTTP client does.
- **Step 3, done (2026-11-15)**: `src/map/labels.c` and `Rmap_set_sprite`: symbols prepared with
  their tiles on the workers, then placed as the spike places them (points with icons, wrapping,
  anchors and offsets; glyphs along lines; road shields; one-way arrows; faces from `text-font`;
  halos; collision; the placement kept through small pans and zooms and placed again 200 ms after
  the camera rests). `test_map`: 166 checks, the spike's label-text and downloaded-tile checks
  among them (the latter with `MAPS_DATA`), clean under ThreadSanitizer. One deviation:
  `text-transform` changes case in ASCII, Latin-1, Latin Extended-A, Greek and Cyrillic only;
  Raven has no full Unicode case mapping, where Kotlin has.

## Deliverables

- [x] `R_WIDGET_MAP` and `Rmap_*` in `raven_controls.h`, behind a build option (`RAVEN_WITH_MAP`)
      so builds that don't want it leave it out (its JSON reader is its own, not vendored).
- [x] `src/widgets/map.c` and its parts in `src/map/` (tile decoding, earcut, style, lines,
      labels, the tile store in the widget).
- [x] Tests in `tests/test_map.c`, ported from `MapsTest.kt`: decoding, triangulation, filters and
      expressions, dashes, labels, placement, and the tile store against hand-made tiles.
- [x] A benchmark that measures both implementations (`tests/map_benchmark.c` and the spike's
      `NativeComparisonBenchmark` and `NativeLabelsBenchmark`), with results filled into the table.
- [x] Step 4: compose-raven's `MapView` over the widget (`compose-raven-map`), and Python showing
      a map (`bindings/python/map_example.py`, tested as `binding_python_map`).
- [x] A verdict: keep the map in C, keep it in Kotlin, or split it, and which Raven changes to
      propose upstream. See [Verdict](#verdict).

## Step 4 (2026-11-15)

- **`compose-raven-map`**, an opt-in module: `MapView(style, tiles, modifier, state,
  attribution, zoomRange, capacity)` emits `R_WIDGET_MAP`. `MapStyle` carries the style JSON and
  its sprite sheet; `TileSource` is the app's own fetching (`suspend fun tile(z, x, y)`), run on
  `Dispatchers.Default` as the widget asks and cancelled when it no longer needs the tile;
  `MapViewState` holds the camera, written by the app and by the user (a new binding event,
  `MapCameraChanged`, in `compose-raven-ui`), and reads the widget's stats. Named `MapView`, not
  `Map`, so as not to shadow `kotlin.collections.Map`. 5 tests (requests, camera both ways,
  cancellation, failure, leaving the composition), stable over 10 runs.
- **The spike** draws through `MapView` by default, its Ktor source and disk cache feeding it
  (checked online over Paris and offline over Chicago); `MAPS_KOTLIN=1` keeps the Kotlin drawing as
  the reference. The window places the same labels as the C example.
- **Python**: `map_example.py` shows Chicago from the spike's files in about 120 lines with no map
  logic of its own, the same labels and icons; `--test` runs headless on a tile it writes.
- compose-raven-ui (136), foundation (270), map (5) and the spike (19) tests pass.

## Verdict

**Decided 2026-11-15: the map stays in C, as Raven's Map widget.** compose-raven keeps only what is
Kotlin's to do (the `MapView` composable, its state, and fetching tiles), and every other binding
gets the same map through `Rmap_*`. The reasons are those of the recommendation below: the same
map as the spike, in every language, and faster wherever the two can differ.

**The style's JSON is parsed in C too**, by the map's own reader (`src/map/json.c`), not by each
client and not by a vendored library. The style's expressions are evaluated per feature on Raven's
workers, so the style has to live in C whoever reads it; reading it in each client would mean a
value-building API in Raven and a tree walker in every binding, to save about 300 lines. Raven
already reads XML with its own parser (`src/xml/xml_parser.c`) and keeps text formats out of
`third_party/`; the JSON reader follows it. If apps need to change a style at run time, targeted
calls (a layer's visibility, a paint property) are the way, not client-side parsing.

## Recommendation for the verdict

**Keep the map in C, as the widget.** The port draws and labels the same map as the spike, every
binding gets it, and it is faster everywhere it can differ: tiles 6 to 14 times (13 prepared
together), label placement 3 to 6 times, reused frames twice; drawing ties, as it should.
compose-raven keeps only what is Kotlin's to do: the composable, state, and fetching.

**To propose upstream**, in order, each standing alone:

1. The seven drawing additions from the spike (`Rdraw_triangles`, `Rdraw_polyline` with caps,
   `R_INSTANCE_ALIASED`, `Rdraw_text_rotated`, `Rdraw_image_region`, `Rdraw_image_rotated`):
   useful to any custom drawing, map or not.
2. The Map widget behind `RAVEN_WITH_MAP`, off by default upstream if its size is a concern.

**Left to do before either**: gzipped tiles (servers that send them compressed), Windows and
Linux builds of the widget (only macOS was run), and the spike's own Kotlin drawing retired or
kept as a test reference.
