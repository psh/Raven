package raven.spike.maps

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

data class TileId(val z: Int, val x: Int, val y: Int) {
    val parent: TileId get() = TileId(z - 1, x / 2, y / 2)
}

/**
 * What one style layer draws of one tile, in the tile's unit square (0 to 1 across): [fills] as
 * triangles, x, y for each corner; [lines] as polylines of x, y pairs: a line layer's lines, or a
 * fill layer's outlines; and a symbol layer's [labels], at points or [lineLabels] along lines.
 */
class Bucket(val fills: FloatArray, val lines: List<FloatArray>, val labels: List<Label> = emptyList(), val lineLabels: List<LineLabel> = emptyList()) {
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
 * The tiles of [source], prepared for [style] on worker threads the first time each is asked for:
 * until then, [bestReady] gives an ancestor that is ready, and [version] changes as each lands, so
 * a map reading it draws again. A tile no longer wanted ([retain]) stops being fetched and
 * prepared; past [capacity] prepared tiles, those used least recently go.
 */
class TileStore(val source: TileSource, private val style: Style, private val capacity: Int = 160) {
    private val prepared = HashMap<TileId, PreparedTile>()
    private val pending = HashMap<TileId, Job>()
    private val lastUsed = HashMap<TileId, Long>()

    // Tiles that failed (a network error), and when: asked for again after a while.
    private val failed = HashMap<TileId, TimeMark>()
    private var clock = 0L

    val minZoom: Int get() = source.minZoom
    val maxZoom: Int get() = source.maxZoom

    /** Where tiles are prepared from: the map's composition. */
    var scope: CoroutineScope? = null

    /** Bumped each time a tile is ready. */
    var version by mutableIntStateOf(0)
        private set

    /** Tiles that failed, so far. */
    var failures = 0
        private set

    val preparedTiles: Collection<PreparedTile> get() = prepared.values
    val pendingCount: Int get() = pending.size

    /** The tile if it is ready; otherwise asks for it and returns null. */
    fun ready(id: TileId): PreparedTile? {
        lastUsed[id] = clock
        prepared[id]?.let { return it }
        if (id.z !in source.minZoom..source.maxZoom || id in pending) return null
        failed[id]?.let { if (it.elapsedNow() < RETRY_AFTER) return null }
        val scope = scope ?: return null
        pending[id] = scope.launch {
            try {
                val tile = withContext(Dispatchers.Default) { source.fetch(id)?.let { prepareTile(id, it, style, deepest = id.z >= source.maxZoom) } }
                // No tile there: an empty one, so it isn't asked for again.
                prepared[id] = tile ?: PreparedTile(id, emptyMap(), 0, 0.0, 0.0)
                failed -= id
                version++
                evict()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed[id] = TimeSource.Monotonic.markNow()
                failures++
            } finally {
                pending -= id
            }
        }
        return null
    }

    /** Fetches and prepares the tile on this thread, for tests. */
    fun prepareNow(id: TileId): PreparedTile? = prepared[id] ?: runBlocking { source.fetch(id) }?.let { prepareTile(id, it, style, deepest = id.z >= source.maxZoom) }?.also { prepared[id] = it }

    /** The tile if it is ready, else the nearest ancestor that is, down to the source's [minZoom]. */
    fun bestReady(id: TileId): PreparedTile? {
        var current = id
        while (current.z >= source.minZoom) {
            ready(current)?.let { return it }
            if (current.z == 0) break
            current = current.parent
        }
        return null
    }

    /**
     * Keeps fetching and preparing only [wanted] and their ancestors; the rest stop. A frame calls
     * it with the tiles it needs, then the next starts a new round of use for eviction.
     */
    fun retain(wanted: Collection<TileId>) {
        val keep = HashSet<TileId>()
        for (id in wanted) {
            var current = id
            while (true) {
                keep += current
                if (current.z == 0) break
                current = current.parent
            }
        }
        for ((id, job) in pending.entries.toList()) if (id !in keep) job.cancel()
        clock++
    }

    private fun evict() {
        if (prepared.size <= capacity) return
        val oldest = prepared.keys.sortedBy { lastUsed[it] ?: 0L }.take(prepared.size - capacity)
        for (id in oldest) {
            // The tiles of the last frame stay: it drew them, and the next will again.
            if ((lastUsed[id] ?: 0L) >= clock - 1) continue
            prepared -= id
            lastUsed -= id
        }
    }
}

/** How long a tile that failed waits before it is asked for again. */
private val RETRY_AFTER = 10.seconds

/**
 * Decodes [bytes] and fills each style layer's bucket from the features its filter keeps, at the
 * tile's zoom: the layers drawn at that zoom or the next, or, for a tile of the source's [deepest]
 * zoom, which draws every zoom past it too, every layer drawn from it on.
 */
fun prepareTile(id: TileId, bytes: ByteArray, style: Style, deepest: Boolean = false): PreparedTile {
    val clock = TimeSource.Monotonic
    val start = clock.markNow()
    val layers = decodeTile(bytes).associateBy { it.name }
    val decoded = start.elapsedNow()
    val buckets = HashMap<String, Bucket>()
    val zoom = id.z.toDouble()
    for (styleLayer in style.symbols) {
        val layer = layers[styleLayer.sourceLayer] ?: continue
        if ((zoom + 1 <= styleLayer.minZoom && !deepest) || zoom >= styleLayer.maxZoom) continue
        val labels = ArrayList<Label>()
        val lineLabels = ArrayList<LineLabel>()
        val alongLines = styleLayer.placementAt(zoom) == "line"
        for (feature in layer.features) {
            // Along lines, a line's symbols; at points, a point's, or a line's at its middle (a shield at low zoom).
            if (alongLines && feature.type != GeometryType.LineString) continue
            if (!alongLines && feature.type != GeometryType.Point && feature.type != GeometryType.LineString) continue
            val context = EvaluationContext(zoom, feature.properties, feature.type)
            if (!accepts(styleLayer.filter, context)) continue
            val text = labelText(styleLayer, context)
            val icon = iconName(styleLayer, context)
            if (text == null && icon == null) continue
            for (ring in feature.rings()) {
                when {
                    alongLines -> if (ring.size >= 4) lineLabels += LineLabel(text, FloatArray(ring.size) { ring[it] / layer.extent }, icon)
                    feature.type == GeometryType.Point -> labels += Label(text, ring[0] / layer.extent, ring[1] / layer.extent, icon)
                    else -> {
                        val middle = (ring.size / 4) * 2
                        labels += Label(text, ring[middle] / layer.extent, ring[middle + 1] / layer.extent, icon)
                    }
                }
            }
        }
        if (labels.isNotEmpty() || lineLabels.isNotEmpty()) buckets[styleLayer.id] = Bucket(FloatArray(0), emptyList(), labels, lineLabels)
    }
    for (styleLayer in style.layers) {
        val layer = layers[styleLayer.sourceLayer] ?: continue
        if ((zoom + 1 <= styleLayer.minZoom && !deepest) || zoom >= styleLayer.maxZoom) continue
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
