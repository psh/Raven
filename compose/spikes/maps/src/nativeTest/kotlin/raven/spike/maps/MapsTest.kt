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
import kotlin.math.abs
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
        val store = TileStore.open(directory, style)
        val tile = store.prepareNow(TileId(14, 4203, 6089))!!
        println("${tile.bytes} bytes: decode ${tile.decodeMs} ms, prepare ${tile.prepareMs} ms, ${tile.triangleCount} triangles, ${tile.segmentCount} segments")
        for ((id, bucket) in tile.buckets.entries.sortedBy { it.key }) println("  $id: ${bucket.triangleCount} triangles, ${bucket.segmentCount} segments")
        // From zoom 14, Liberty's buildings are its 3D layer, drawn flat; this tile's parks are only label points.
        for (name in listOf("water", "building-3d", "road_minor", "landcover_grass")) assertTrue(name in tile.buckets, "$name drawn")
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
        val store = TileStore.open(directory, style)
        for (id in store.available) store.prepareNow(id)
        val camera = Camera.at(START_LATITUDE, START_LONGITUDE, 14.0)
        val stats = DrawStats()
        var batched by mutableStateOf(true)
        setContent {
            Canvas(Modifier.size(1200.dp, 800.dp)) {
                drawMap(style, store, camera, stats, 12, 14, batched)
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

private fun median(values: List<Double>): Double = values.sorted()[values.size / 2]

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
