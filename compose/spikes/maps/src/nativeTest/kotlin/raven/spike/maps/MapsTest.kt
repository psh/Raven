package raven.spike.maps

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.compose.foundation.Canvas
import raven.compose.foundation.layout.size
import raven.compose.ui.Modifier
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.runtime.remember
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapsTest {
    @Test
    fun jsonReadsAStyleShapedDocument() {
        val json = parseJson("""{"a": [1, -2.5e1, "x\"y", true, null], "b": {"c": "é"}}""") as Map<*, *>
        assertEquals(listOf(1.0, -25.0, "x\"y", true, null), json["a"])
        assertEquals(mapOf("c" to "é"), json["b"])
    }

    @Test
    fun aTileDecodesWithItsLayersFeaturesAndGeometry() {
        // One layer "water", extent 4096, a polygon feature tagged class=lake: a square from (2,2) to (6,6).
        val geometry = intArrayOf(command(1, 1), zz(2), zz(2), command(2, 3), zz(4), zz(0), zz(0), zz(4), zz(-4), zz(0), command(7, 1))
        val feature = message(2, packed(intArrayOf(0, 0))) + varintField(3, 3) + message(4, packed(geometry))
        val value = message(1, "lake".encodeToByteArray())
        val layer = message(1, "water".encodeToByteArray()) + message(2, feature) + message(3, "class".encodeToByteArray()) +
            message(4, value) + varintField(5, 4096) + varintField(15, 2)
        val tile = decodeTile(message(3, layer))
        assertEquals(listOf("water"), tile.map { it.name })
        val decoded = tile[0].features.single()
        assertEquals(GeometryType.Polygon, decoded.type)
        assertEquals(mapOf<String, Any?>("class" to "lake"), decoded.properties)
        assertEquals(listOf(2f, 2f, 6f, 2f, 6f, 6f, 2f, 6f), decoded.rings().single().toList())
        assertTrue(ringArea(decoded.rings().single()) > 0, "an exterior ring has a positive area, y down")
    }

    @Test
    fun earcutCoversAPolygonWithAHoleExactly() {
        // A 10 by 10 square with a 4 by 4 hole: 84 square units, in triangles that cover it once.
        val square = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f, 3f, 3f, 3f, 7f, 7f, 7f, 7f, 3f)
        val triangles = earcut(square, intArrayOf(4))
        assertEquals(84.0, trianglesArea(square, triangles), 1e-6)
        // A concave L: 3 by 3 less a 2 by 2 corner.
        val l = floatArrayOf(0f, 0f, 3f, 0f, 3f, 1f, 1f, 1f, 1f, 3f, 0f, 3f)
        assertEquals(5.0, trianglesArea(l, earcut(l)), 1e-6)
    }

    @Test
    fun expressionsFilterAndInterpolateAsTheStyleSpecSays() {
        val road = EvaluationContext(14.0, mapOf("class" to "minor", "brunnel" to "bridge", "admin_level" to 2.0), GeometryType.LineString)
        val minor = listOf("all", listOf("match", listOf("geometry-type"), listOf("LineString", "MultiLineString"), true, false),
            listOf("match", listOf("get", "brunnel"), listOf("bridge", "tunnel"), false, true))
        assertEquals(false, evaluate(minor, road), "a bridge is not a plain road")
        assertEquals(true, evaluate(listOf("==", listOf("get", "admin_level"), 2.0), road))
        assertEquals(true, evaluate(listOf("!", listOf("has", "claimed_by")), road))
        val width = listOf("interpolate", listOf("exponential", 1.2), listOf("zoom"), 13.5, 0.0, 14.0, 2.5, 20.0, 18.0)
        assertEquals(2.5, evaluate(width, road) as Double, 1e-9)
        assertEquals(1.25, evaluate(width, EvaluationContext(13.75)) as Double, 0.1)
        val color = listOf("interpolate", listOf("linear"), listOf("zoom"), 9.0, "#000000", 13.0, "#ffffff")
        assertEquals("#808080ff", parseColor(evaluate(color, EvaluationContext(11.0)))!!.hex())
    }

    @Test
    fun aLabelsTextFollowsTheStylesExpressionAndTransform() {
        val field = listOf("case", listOf("has", "name:nonlatin"), listOf("concat", listOf("get", "name:latin"), "\n", listOf("get", "name:nonlatin")),
            listOf("coalesce", listOf("get", "name_en"), listOf("get", "name")))
        fun layer(layout: Map<String, Any?>) = StyleLayer("l", "symbol", "place", 0.0, 24.0, null, emptyMap(), layout)
        val plain = layer(mapOf("text-field" to field))
        assertEquals("Chicago", labelText(plain, EvaluationContext(14.0, mapOf("name" to "Chicago"))))
        assertEquals("Chicago", labelText(plain, EvaluationContext(14.0, mapOf("name" to "Chicago (city)", "name_en" to "Chicago"))))
        assertEquals("Tokyo\n東京", labelText(plain, EvaluationContext(14.0, mapOf("name:latin" to "Tokyo", "name:nonlatin" to "東京"))))
        assertEquals("THE LOOP", labelText(layer(mapOf("text-field" to field, "text-transform" to "uppercase")), EvaluationContext(14.0, mapOf("name" to "The Loop"))))
        assertEquals("Route 66", labelText(layer(mapOf("text-field" to "Route {ref}")), EvaluationContext(14.0, mapOf("ref" to 66.0))))
        assertEquals(null, labelText(plain, EvaluationContext(14.0)), "no name, no label")
    }

    @Test
    fun dashesFollowThePatternAlongLinesAndAcrossTurns() {
        // A line 10 long, then a second one 4 long: 3 on, 2 off, starting again on each line.
        val lines = floatArrayOf(0f, 0f, 10f, 0f, Float.NaN, Float.NaN, 0f, 5f, 0f, 9f, Float.NaN, Float.NaN)
        val dashes = pieces(dash(lines, listOf(3f, 2f)))
        assertEquals(listOf(listOf(0f, 0f, 3f, 0f), listOf(5f, 0f, 8f, 0f), listOf(0f, 5f, 0f, 8f)), dashes)
        // Across a turn, a dash keeps the turn's point: 3 on from (0, 0) along (2, 0) then down.
        assertEquals(listOf(0f, 0f, 2f, 0f, 2f, 1f), pieces(dash(floatArrayOf(0f, 0f, 2f, 0f, 2f, 9f, Float.NaN, Float.NaN), listOf(3f, 100f))).first())
        // No gap at all draws the line whole.
        assertEquals(null, styleLayer(listOf(1.0, 0.0)).dashes(14.0))
        assertEquals(listOf(1f, 1.5f), styleLayer(listOf(1.0, 1.5)).dashes(14.0))
    }

    @Test
    fun anOffsetLineKeepsItsDistanceAtCorners() {
        // Along x, its left (y down) is up: offset by 2, it moves to y = -2.
        assertEquals(listOf(0f, -2f, 10f, -2f), pieces(offset(floatArrayOf(0f, 0f, 10f, 0f, Float.NaN, Float.NaN), 2f)).single())
        // Right then down: the corner's mitre keeps it 2 from both legs, at (12, -2).
        val corner = pieces(offset(floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, Float.NaN, Float.NaN), 2f)).single()
        assertEquals(listOf(0f, -2f, 12f, -2f, 12f, 10f), corner.map { (it * 1000).roundToInt() / 1000f })
    }

    @Test
    fun coloursParseInTheStylesForms() {
        assertEquals("#f8f4f0ff", parseColor("#f8f4f0")!!.hex())
        assertEquals("#ffddeeff", parseColor("#fde")!!.hex())
        assertEquals("#9ebdffff", parseColor("rgb(158,189,255)")!!.hex())
        assertEquals("#5fd06480", parseColor("rgba(95, 208, 100, 0.5)")!!.hex())
        assertEquals("#dcd9d6ff", parseColor("hsl(35,8%,85%)")!!.hex())
    }

    @Test
    fun theVisibleTilesCoverTheView() {
        val camera = Camera.at(START_LATITUDE, START_LONGITUDE, 14.0)
        // 1200 by 800 at zoom 14 on 512-pixel tiles: at most 4 by 3 tiles.
        val tiles = visibleTiles(camera, 1200f, 800f, 14)
        assertTrue(tiles.size in 6..12, "${tiles.size} tiles")
        assertTrue(tiles.all { it.z == 14 })
    }

    /** Against fetch-data's tiles when they are there: what each style layer gets from the Loop's tile. */
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun theDownloadedTilesPrepareWithEveryKindOfFeature() {
        val directory = getenv("MAPS_DATA")?.toKString() ?: return
        val style = Style.parse(readFile("$directory/liberty.json")!!.decodeToString())
        val store = TileStore(FileTileSource(directory), style)
        val tile = store.prepareNow(TileId(14, 4203, 6089))!!
        println("${tile.bytes} bytes: decode ${tile.decodeMs} ms, prepare ${tile.prepareMs} ms, ${tile.triangleCount} triangles, ${tile.segmentCount} segments")
        for ((id, bucket) in tile.buckets.entries.sortedBy { it.key }) println("  $id: ${bucket.triangleCount} triangles, ${bucket.segmentCount} segments")
        // From zoom 14, Liberty's buildings are its 3D layer, drawn flat; this tile's parks are only label points.
        for (name in listOf("water", "building-3d", "road_minor", "landcover_grass")) assertTrue(name in tile.buckets, "$name drawn")
        // The deepest tiles draw every zoom past theirs: points of interest (from zoom 15) and minor
        // street names (from 15) are prepared from them too.
        assertTrue(tile.buckets["poi_r1"]?.labels.orEmpty().isNotEmpty(), "points of interest from zoom 15")
        assertTrue(tile.buckets["highway-name-minor"]?.lineLabels.orEmpty().isNotEmpty(), "minor street names from zoom 15")
        // One-way arrows: icons alone along their roads (from zoom 16).
        assertTrue(tile.buckets["road_one_way_arrow"]?.lineLabels.orEmpty().any { it.icon == "arrow" && it.text == null }, "one-way arrows along roads")
        // Street names run along their lines; place names stand at points.
        assertTrue(tile.buckets["highway-name-major"]?.lineLabels.orEmpty().isNotEmpty(), "street names along lines")
        assertTrue(tile.buckets.values.any { it.labels.isNotEmpty() }, "labels at points")
    }
}

/**
 * Draws the Loop at zoom 14 from fetch-data's tiles, headless, 30 frames with each way of drawing
 * the fills, and prints what a frame costs: the measure behind `Rdraw_triangles`.
 */
class MapsBenchmark {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun batchedTrianglesAgainstOneCallEach() = runRavenTest {
        val directory = getenv("MAPS_DATA")?.toKString() ?: return@runRavenTest
        val style = Style.parse(readFile("$directory/liberty.json")!!.decodeToString())
        val source = FileTileSource(directory)
        val store = TileStore(source, style)
        for (id in source.tiles) store.prepareNow(id)
        val camera = Camera.at(START_LATITUDE, START_LONGITUDE, 14.0)
        val stats = DrawStats()
        var batched by mutableStateOf(true)
        setContent {
            Canvas(Modifier.size(1200.dp, 800.dp)) {
                drawMap(style, store, camera, stats, batched)
            }
        }
        for (mode in listOf(true, false, true, false)) {
            batched = mode
            val fills = ArrayList<Double>()
            val lines = ArrayList<Double>()
            repeat(30) {
                // A pan of a hundredth of a pixel: the same picture, drawn again.
                camera.centerX += 1e-2 / camera.worldSize
                waitForIdle()
                fills += stats.fillMs
                lines += stats.lineMs
            }
            println("${if (mode) "batched     " else "one call each"}: ${stats.triangles} triangles, fills ${median(fills)} ms, ${stats.segments} segments, lines ${median(lines)} ms (medians of 30 frames)")
        }
    }
}

/**
 * What the native Map widget's benchmark (tests/map_benchmark.c, 0140) measures, measured the same
 * way here: the densest tile prepared ten times, every tile once, then the Loop at zoom 14 drawn 30
 * times. Tiles are prepared with the style's labels and without them: the native widget has none yet.
 * Run optimised, from build/bin/macosArm64/releaseTest/test.kexe.
 */
class NativeComparisonBenchmark {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun asTheNativeBenchmarkMeasures() = runRavenTest {
        val directory = getenv("MAPS_DATA")?.toKString() ?: return@runRavenTest
        val full = Style.parse(readFile("$directory/liberty.json")!!.decodeToString())
        val noLabels = Style(full.background, full.layers)
        val source = FileTileSource(directory)
        val dense = TileId(14, 4203, 6089)
        val bytes = readFile("$directory/tiles/${dense.z}-${dense.x}-${dense.y}.pbf")!!
        for ((name, style) in listOf("with labels" to full, "without labels" to noLabels)) {
            val decode = ArrayList<Double>()
            val prepare = ArrayList<Double>()
            repeat(10) {
                val tile = prepareTile(dense, bytes, style, deepest = true)
                decode += tile.decodeMs
                prepare += tile.prepareMs
            }
            println("dense tile ${dense.z}/${dense.x}/${dense.y} $name: decode ${median(decode)} ms, style + triangulate ${median(prepare)} ms (medians of 10)")
        }
        val store = TileStore(source, noLabels)
        val tiles = source.tiles.mapNotNull { store.prepareNow(it) }
        println("${tiles.size} tiles without labels: decode ${tiles.sumOf { it.decodeMs }} ms, style + triangulate ${tiles.sumOf { it.prepareMs }} ms in all")
        // The same tiles through a store of their own, prepared together on Dispatchers.Default.
        val parallel = TileStore(source, noLabels)
        val wall = kotlin.time.measureTime {
            runBlocking {
                parallel.scope = this
                for (id in source.tiles) parallel.ready(id)
            }
        }
        println("${parallel.preparedTiles.size} tiles without labels, prepared together: all ready in ${wall.inWholeMicroseconds / 1000.0} ms")
        val camera = Camera.at(START_LATITUDE, START_LONGITUDE, 14.0)
        val stats = DrawStats()
        var drawn = ""
        setContent {
            Canvas(Modifier.size(1200.dp, 800.dp)) {
                drawn = "${size.width} x ${size.height}"
                drawMap(noLabels, store, camera, stats)
            }
        }
        waitForIdle()
        println("canvas $drawn")
        val fills = ArrayList<Double>()
        val lines = ArrayList<Double>()
        val draws = ArrayList<Double>()
        repeat(30) {
            camera.centerX += 1e-2 / camera.worldSize
            waitForIdle()
            fills += stats.fillMs
            lines += stats.lineMs
            draws += stats.drawMs
        }
        println("zoom 14, 1200 x 800: ${stats.tiles} tiles, ${stats.triangles} triangles, fills ${median(fills)} ms, ${stats.segments} segments, lines ${median(lines)} ms, map ${median(draws)} ms (medians of 30 frames)")
    }
}

/**
 * The labels, as raven_map_benchmark measures them (0140): the Loop at zoom 14 in the harness's
 * 800 x 600 window, with the sprite sheet; 30 frames panned two units each, 30 zoomed a hundredth
 * of a level each, and 30 each placed again (settled).
 */
class NativeLabelsBenchmark {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun labelsAsTheNativeBenchmarkMeasures() = runRavenTest {
        val directory = getenv("MAPS_DATA")?.toKString() ?: return@runRavenTest
        val style = Style.parse(readFile("$directory/liberty.json")!!.decodeToString())
        val sprite = Sprite.parse(readFile("$directory/sprite@2x.json")!!.decodeToString(), readFile("$directory/sprite@2x.png")!!)
        val source = FileTileSource(directory)
        val store = TileStore(source, style)
        for (id in source.tiles) store.prepareNow(id)
        val camera = Camera.at(START_LATITUDE, START_LONGITUDE, 14.0)
        val stats = DrawStats()
        val labelStats = LabelStats()
        val measure = TextMeasure()
        val cache = LabelCache()
        var settled by mutableStateOf(false)
        setContent {
            val sheet = remember { SpriteSheet(sprite, sprite.png.decodeToImageBitmap()) }
            Canvas(Modifier.size(1200.dp, 800.dp)) {
                drawMap(style, store, camera, stats, labels = measure, labelStats = labelStats, labelCache = cache, settled = settled, sprites = sheet)
            }
        }
        waitForIdle()
        for ((mode, name) in listOf("panning", "zooming", "placed every frame").withIndex()) {
            camera.centerX = Camera.at(START_LATITUDE, START_LONGITUDE, 14.0).centerX
            camera.zoom = 14.0
            settled = mode == 2
            waitForIdle()
            val ms = ArrayList<Double>()
            val place = ArrayList<Double>()
            var reused = 0
            repeat(30) { i ->
                if (mode == 1) camera.zoom = 14.0 + 0.01 * (i + 1) else camera.centerX += 2.0 / camera.worldSize
                waitForIdle()
                ms += labelStats.ms
                if (labelStats.reused) reused++ else place += labelStats.placeMs
            }
            println("labels $name: ${median(ms)} ms a frame, $reused of 30 frames reusing the placement" +
                if (place.isEmpty()) "" else ", a placement ${median(place)} ms; ${labelStats.placed} placed of ${labelStats.candidates}, ${labelStats.icons} icons")
        }
    }
}

/**
 * The labels of the Loop at zoom 14, headless, 30 frames panned a little and 30 zoomed in a
 * hundredth of a level at a time (the placement moved), and 30 at rest each frame somewhere new
 * (placed again each frame): what labels cost a frame each way.
 */
class LabelsBenchmark {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun keptPlacementAgainstPlacingEveryFrame() = runRavenTest {
        val directory = getenv("MAPS_DATA")?.toKString() ?: return@runRavenTest
        val style = Style.parse(readFile("$directory/liberty.json")!!.decodeToString())
        val source = FileTileSource(directory)
        val store = TileStore(source, style)
        for (id in source.tiles) store.prepareNow(id)
        val camera = Camera.at(START_LATITUDE, START_LONGITUDE, 14.0)
        val stats = DrawStats()
        val labelStats = LabelStats()
        val measure = TextMeasure()
        val cache = LabelCache()
        var settled by mutableStateOf(false)
        setContent {
            Canvas(Modifier.size(1200.dp, 800.dp)) {
                drawMap(style, store, camera, stats, labels = measure, labelStats = labelStats, labelCache = cache, settled = settled)
            }
        }
        // Panning, zooming in steps of a hundredth of a level, and at rest every frame (each placed again).
        for (mode in listOf("panned", "zoomed", "settled", "panned", "zoomed", "settled")) {
            val costs = ArrayList<Double>()
            var reused = 0
            settled = mode == "settled"
            camera.zoom = 14.0
            waitForIdle()
            repeat(30) {
                when (mode) {
                    "panned" -> camera.centerX += 1.0 / camera.worldSize
                    "zoomed" -> camera.zoom += 0.01
                    else -> camera.centerX += 1.0 / camera.worldSize
                }
                waitForIdle()
                costs += labelStats.ms
                if (labelStats.reused) reused++
            }
            println("${mode.padEnd(7)}: ${labelStats.placed} labels of ${labelStats.candidates}, ${median(costs)} ms a frame, $reused of 30 frames reused (medians of 30 frames)")
        }
    }
}

/** The tile store against a source of hand-made tiles: loading, cancelling, eviction, failures. */
class TileStoreTest {
    private val style = Style(Color.White, emptyList())

    /** A source of one small tile anywhere, that waits for [gate] where it holds one, and fails where [failing]. */
    private class FakeSource(val gate: Map<TileId, CompletableDeferred<Unit>> = emptyMap(), val failing: Set<TileId> = emptySet()) : TileSource {
        override val minZoom = 0
        override val maxZoom = 14
        var fetches = 0

        override suspend fun fetch(id: TileId): ByteArray? {
            fetches++
            gate[id]?.await()
            if (id in failing) error("no network")
            return waterTile()
        }
    }

    /** Runs [block] until [done], or fails after two seconds. */
    private suspend fun waitFor(done: () -> Boolean) {
        withTimeout(2000) { while (!done()) delay(5) }
    }

    @Test
    fun aTileIsAskedForOnceAndLandsReady() = runBlocking {
        val source = FakeSource()
        val store = TileStore(source, style).also { it.scope = this }
        val id = TileId(14, 1, 2)
        assertNull(store.ready(id), "not ready at first")
        assertNull(store.ready(id), "asked for again while it loads")
        waitFor { store.version > 0 }
        assertNotNull(store.ready(id))
        assertEquals(1, source.fetches, "fetched once")
        assertEquals(store.ready(id), store.bestReady(TileId(14, 1, 2)))
    }

    @Test
    fun aTileNoLongerWantedStopsLoading() = runBlocking {
        val wanted = TileId(14, 1, 1)
        val gone = TileId(14, 9, 9)
        val source = FakeSource(gate = mapOf(gone to CompletableDeferred()))
        val store = TileStore(source, style).also { it.scope = this }
        store.ready(gone)
        store.ready(wanted)
        waitFor { store.ready(wanted) != null }
        assertEquals(1, store.pendingCount)
        // Panned away: the tile still loading, and its ancestors, are no longer wanted.
        store.retain(listOf(wanted))
        waitFor { store.pendingCount == 0 }
        assertNull(store.ready(gone).also { store.retain(emptyList()) }, "asked for again from the start")
        assertEquals(0, store.failures, "a cancelled tile is no failure")
        coroutineContext.cancelChildren()
    }

    @Test
    fun theTilesUsedLeastRecentlyGoPastTheCapacity() = runBlocking {
        val store = TileStore(FakeSource(), style, capacity = 2).also { it.scope = this }
        val tiles = List(4) { TileId(14, it, 0) }
        for (id in tiles) {
            // A frame wanting this tile alone, until it is ready.
            store.ready(id)
            store.retain(listOf(id))
            waitFor { store.preparedTiles.any { it.id == id } }
        }
        assertEquals(listOf(2, 3), store.preparedTiles.map { it.id.x }.sorted())
    }

    @Test
    fun aTileThatFailedWaitsBeforeItIsAskedForAgain() = runBlocking {
        val id = TileId(14, 5, 5)
        val source = FakeSource(failing = setOf(id))
        val store = TileStore(source, style).also { it.scope = this }
        store.ready(id)
        waitFor { store.failures == 1 && store.pendingCount == 0 }
        assertNull(store.ready(id))
        assertEquals(0, store.pendingCount, "not asked for again at once")
        assertEquals(1, source.fetches)
    }

    @Test
    fun theDiskCacheKeepsTilesEmptyOnesIncluded() = runBlocking {
        val directory = "${getenv("TMPDIR")?.toKString()?.trimEnd('/') ?: "/tmp"}/compose-raven-maps-test-${Random.nextInt(1_000_000)}"
        val cache = DiskCache(directory)
        assertNull(cache.read("14/1/2.pbf"))
        cache.write("14/1/2.pbf", waterTile())
        cache.write("14/1/3.pbf", ByteArray(0))
        assertEquals(waterTile().toList(), cache.read("14/1/2.pbf")?.toList())
        assertEquals(0, cache.read("14/1/3.pbf")?.size, "an empty tile is kept, as a tile with nothing in it")
        // Found in the cache, a tile is not fetched: the client is never used.
        val source = HttpTileSource(HttpTileSource.client(), "http://localhost:1/{z}/{x}/{y}.pbf", 0, 14, cache)
        assertEquals(waterTile().toList(), source.fetch(TileId(14, 1, 2))?.toList())
        assertEquals(1, source.cached)
        assertEquals(0, source.fetched)
    }
}

/** A tile with one layer, "water", holding a square. */
private fun waterTile(): ByteArray {
    val geometry = intArrayOf(command(1, 1), zz(2), zz(2), command(2, 3), zz(4), zz(0), zz(0), zz(4), zz(-4), zz(0), command(7, 1))
    val feature = message(2, packed(intArrayOf(0, 0))) + varintField(3, 3) + message(4, packed(geometry))
    val layer = message(1, "water".encodeToByteArray()) + message(2, feature) + message(3, "class".encodeToByteArray()) +
        message(4, message(1, "lake".encodeToByteArray())) + varintField(5, 4096) + varintField(15, 2)
    return message(3, layer)
}

private fun median(values: List<Double>): Double = values.sorted()[values.size / 2]

/** Packed lines (a NaN pair between them) as lists of their coordinates. */
private fun pieces(packed: FloatArray): List<List<Float>> {
    val out = ArrayList<List<Float>>()
    var current = ArrayList<Float>()
    for (i in packed.indices step 2) {
        if (packed[i].isNaN()) {
            if (current.isNotEmpty()) out += current
            current = ArrayList()
        } else {
            current += packed[i]
            current += packed[i + 1]
        }
    }
    if (current.isNotEmpty()) out += current
    return out
}

private fun styleLayer(dasharray: List<Double>) = StyleLayer("l", "line", "x", 0.0, 24.0, null, mapOf("line-dasharray" to dasharray), emptyMap())

private fun trianglesArea(coords: FloatArray, triangles: IntArray): Double {
    var sum = 0.0
    for (t in triangles.indices step 3) {
        val (a, b, c) = Triple(triangles[t] * 2, triangles[t + 1] * 2, triangles[t + 2] * 2)
        sum += abs((coords[b] - coords[a]) * (coords[c + 1] - coords[a + 1]) - (coords[c] - coords[a]) * (coords[b + 1] - coords[a + 1])) / 2.0
    }
    return sum
}

// A protobuf writer, just enough to build a tile by hand.
private fun varint(v: Long): ByteArray {
    val out = ArrayList<Byte>()
    var n = v
    do {
        var b = (n and 0x7F).toInt()
        n = n ushr 7
        if (n != 0L) b = b or 0x80
        out += b.toByte()
    } while (n != 0L)
    return out.toByteArray()
}

private fun message(field: Int, body: ByteArray) = varint(((field shl 3) or 2).toLong()) + varint(body.size.toLong()) + body

private fun varintField(field: Int, value: Int) = varint((field shl 3).toLong()) + varint(value.toLong())

private fun packed(values: IntArray): ByteArray = values.fold(ByteArray(0)) { acc, v -> acc + varint(v.toLong() and 0xFFFFFFFFL) }

private fun command(id: Int, count: Int) = (id and 7) or (count shl 3)

private fun zz(n: Int) = (n shl 1) xor (n shr 31)
