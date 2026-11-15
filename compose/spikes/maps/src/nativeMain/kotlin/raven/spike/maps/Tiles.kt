package raven.spike.maps

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import kotlin.time.TimeSource

data class TileId(val z: Int, val x: Int, val y: Int) {
    val parent: TileId get() = TileId(z - 1, x / 2, y / 2)
}

/**
 * What one style layer draws of one tile, in the tile's unit square (0 to 1 across): [fills] as
 * triangles, x, y for each corner; [lines] as polylines of x, y pairs: a line layer's lines, or a
 * fill layer's outlines; and a symbol layer's [labels].
 */
class Bucket(val fills: FloatArray, val lines: List<FloatArray>, val labels: List<Label> = emptyList()) {
    val triangleCount: Int get() = fills.size / 6
    val segmentCount: Int get() = lines.sumOf { it.size / 2 - 1 }

    /** [lines] in one array, a NaN point between each, for one `drawPolyline` call. */
    val packedLines: FloatArray = FloatArray(lines.sumOf { it.size + 2 }).also { packed ->
        var at = 0
        for (line in lines) {
            line.copyInto(packed, at)
            at += line.size
            packed[at++] = Float.NaN
            packed[at++] = Float.NaN
        }
    }
}

/** A tile decoded, filtered by the style at its zoom and triangulated: a [Bucket] per style layer. */
class PreparedTile(val id: TileId, val buckets: Map<String, Bucket>, val bytes: Int, val decodeMs: Double, val prepareMs: Double) {
    val triangleCount: Int get() = buckets.values.sumOf { it.triangleCount }
    val segmentCount: Int get() = buckets.values.sumOf { it.segmentCount }
}

/**
 * The tiles of [directory] (`z-x-y.pbf`), prepared for [style] on a background thread the first
 * time each is asked for: until then, [bestReady] gives an ancestor that is ready, and [version]
 * changes as each lands, so a map reading it draws again.
 */
class TileStore(private val directory: String, private val style: Style, val available: Set<TileId>) {
    private val prepared = HashMap<TileId, PreparedTile?>()
    private val pending = HashSet<TileId>()

    /** Where tiles are prepared from: the map's composition. */
    var scope: CoroutineScope? = null

    /** Bumped each time a tile is ready. */
    var version by mutableIntStateOf(0)
        private set

    val preparedTiles: Collection<PreparedTile> get() = prepared.values.filterNotNull()

    /** The tile if it is ready; otherwise asks for it and returns null. */
    fun ready(id: TileId): PreparedTile? {
        if (id in prepared) return prepared[id]
        if (id !in available) return null
        val scope = scope ?: return prepareNow(id)
        if (pending.add(id)) {
            scope.launch {
                val tile = withContext(Dispatchers.Default) { load(id) }
                prepared[id] = tile
                pending -= id
                version++
            }
        }
        return null
    }

    /** Prepares the tile on this thread, for tests and when no scope is given. */
    fun prepareNow(id: TileId): PreparedTile? = prepared.getOrPut(id) { if (id in available) load(id) else null }

    private fun load(id: TileId): PreparedTile? =
        readFile("$directory/${id.z}-${id.x}-${id.y}.pbf")?.let { prepareTile(id, it, style) }

    /** The tile if it is ready, else the nearest ancestor that is, down to zoom [minZoom]. */
    fun bestReady(id: TileId, minZoom: Int): PreparedTile? {
        var current = id
        while (current.z >= minZoom) {
            ready(current)?.let { return it }
            current = current.parent
        }
        return null
    }

    companion object {
        /** The tiles listed in [directory]/tiles.txt ("z x y" lines), found in [directory]/tiles. */
        fun open(directory: String, style: Style): TileStore {
            val list = readFile("$directory/tiles.txt")?.decodeToString().orEmpty()
            val ids = list.lines().mapNotNull { line ->
                line.trim().split(' ').takeIf { it.size == 3 }?.let { TileId(it[0].toInt(), it[1].toInt(), it[2].toInt()) }
            }.toSet()
            return TileStore("$directory/tiles", style, ids)
        }
    }
}

/** Decodes [bytes] and fills each style layer's bucket from the features its filter keeps, at the tile's zoom. */
fun prepareTile(id: TileId, bytes: ByteArray, style: Style): PreparedTile {
    val clock = TimeSource.Monotonic
    val start = clock.markNow()
    val layers = decodeTile(bytes).associateBy { it.name }
    val decoded = start.elapsedNow()
    val buckets = HashMap<String, Bucket>()
    val zoom = id.z.toDouble()
    for (styleLayer in style.symbols) {
        val layer = layers[styleLayer.sourceLayer] ?: continue
        if (zoom + 1 <= styleLayer.minZoom || zoom >= styleLayer.maxZoom) continue
        val labels = ArrayList<Label>()
        for (feature in layer.features) {
            if (feature.type != GeometryType.Point) continue
            val context = EvaluationContext(zoom, feature.properties, feature.type)
            if (!accepts(styleLayer.filter, context)) continue
            val text = labelText(styleLayer, context) ?: continue
            for (ring in feature.rings()) labels += Label(text, ring[0] / layer.extent, ring[1] / layer.extent)
        }
        if (labels.isNotEmpty()) buckets[styleLayer.id] = Bucket(FloatArray(0), emptyList(), labels)
    }
    for (styleLayer in style.layers) {
        val layer = layers[styleLayer.sourceLayer] ?: continue
        if (zoom + 1 <= styleLayer.minZoom || zoom >= styleLayer.maxZoom) continue
        val scale = 1f / layer.extent
        val fills = ArrayList<FloatArray>()
        val lines = ArrayList<FloatArray>()
        for (feature in layer.features) {
            if (feature.type == GeometryType.Point || feature.type == GeometryType.Unknown) continue
            if (!accepts(styleLayer.filter, EvaluationContext(zoom, feature.properties, feature.type))) continue
            val rings = feature.rings()
            if (styleLayer.isFill) {
                if (feature.type != GeometryType.Polygon) continue
                fills += triangulate(rings, scale)
                // The rings, closed, for the outline that smooths the mesh's aliased edges.
                if (styleLayer.antialias) for (ring in rings) lineOf(ring, scale, closed = true)?.let { lines += it }
            } else {
                for (ring in rings) lineOf(ring, scale, closed = feature.type == GeometryType.Polygon)?.let { lines += it }
            }
        }
        if (fills.isNotEmpty() || lines.isNotEmpty()) {
            val all = FloatArray(fills.sumOf { it.size })
            var at = 0
            for (f in fills) {
                f.copyInto(all, at)
                at += f.size
            }
            buckets[styleLayer.id] = Bucket(all, lines)
        }
    }
    return PreparedTile(id, buckets, bytes.size, decoded.inWholeMicroseconds / 1000.0, (start.elapsedNow() - decoded).inWholeMicroseconds / 1000.0)
}

/** [ring] scaled by [scale], its first point repeated at the end if [closed]; null if it has fewer than two points. */
private fun lineOf(ring: FloatArray, scale: Float, closed: Boolean): FloatArray? {
    val points = FloatArray(ring.size + if (closed) 2 else 0)
    for (i in ring.indices) points[i] = ring[i] * scale
    if (closed) {
        points[ring.size] = ring[0] * scale
        points[ring.size + 1] = ring[1] * scale
    }
    return points.takeIf { it.size >= 4 }
}

/**
 * The triangles of a polygon feature's rings, each exterior (positive area) with the holes that
 * follow it, as x, y corners scaled by [scale].
 */
fun triangulate(rings: List<FloatArray>, scale: Float): FloatArray {
    val out = ArrayList<Float>()
    var i = 0
    while (i < rings.size) {
        if (ringArea(rings[i]) <= 0) {
            i++
            continue
        }
        val polygon = mutableListOf(rings[i])
        i++
        while (i < rings.size && ringArea(rings[i]) < 0) polygon += rings[i++]
        val coords = FloatArray(polygon.sumOf { it.size })
        val holes = IntArray(polygon.size - 1)
        var at = 0
        for ((k, ring) in polygon.withIndex()) {
            if (k > 0) holes[k - 1] = at / 2
            ring.copyInto(coords, at)
            at += ring.size
        }
        for (v in earcut(coords, holes)) {
            out += coords[v * 2] * scale
            out += coords[v * 2 + 1] * scale
        }
    }
    return out.toFloatArray()
}

/** The bytes of the file at [path], or null if it can't be read. */
@OptIn(ExperimentalForeignApi::class)
fun readFile(path: String): ByteArray? {
    val file = fopen(path, "rb") ?: return null
    try {
        fseek(file, 0, SEEK_END)
        val size = ftell(file).toInt()
        fseek(file, 0, SEEK_SET)
        val bytes = ByteArray(size)
        if (size > 0) bytes.usePinned { fread(it.addressOf(0), 1u, size.convert(), file) }
        return bytes
    } finally {
        fclose(file)
    }
}
