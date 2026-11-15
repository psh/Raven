package raven.spike.maps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import raven.compose.foundation.Canvas
import raven.compose.foundation.Surface
import raven.compose.foundation.Text
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.padding
import raven.compose.ui.Alignment
import raven.compose.ui.Modifier
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.graphics.decodeToImageBitmap
import raven.compose.ui.geometry.Offset
import raven.compose.ui.graphics.drawscope.DrawScope
import raven.compose.ui.input.pointer.PointerEventType
import raven.compose.ui.input.pointer.onPointerEvent
import raven.compose.ui.platform.LocalWindowInfo
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.tan
import kotlin.time.TimeSource

/** The tile size MapLibre's styles are written for: line widths are pixels on a 512-pixel tile. */
internal const val TILE_SIZE = 512.0

/**
 * Where the map looks: its centre in Web Mercator's unit square (0 to 1 across, from the
 * antimeridian and the north edge), and the zoom, fractional.
 */
class Camera(centerX: Double, centerY: Double, zoom: Double) {
    var centerX by mutableDoubleStateOf(centerX)
    var centerY by mutableDoubleStateOf(centerY)
    var zoom by mutableDoubleStateOf(zoom)

    val worldSize: Double get() = TILE_SIZE * 2.0.pow(zoom)

    /** Zooms by [delta] keeping the point under ([x], [y]) of a [width] by [height] view where it is. */
    fun zoomAt(delta: Double, x: Float, y: Float, width: Float, height: Float, minZoom: Double, maxZoom: Double) {
        val before = worldSize
        val wx = centerX + (x - width / 2) / before
        val wy = centerY + (y - height / 2) / before
        zoom = (zoom + delta).coerceIn(minZoom, maxZoom)
        centerX = wx - (x - width / 2) / worldSize
        centerY = wy - (y - height / 2) / worldSize
    }

    companion object {
        fun at(latitude: Double, longitude: Double, zoom: Double): Camera = Camera(
            (longitude + 180) / 360,
            (1 - asinh(tan(latitude * PI / 180)) / PI) / 2,
            zoom,
        )
    }
}

/** What the last frame drew, and how long the drawing calls took. */
class DrawStats {
    var drawMs = 0.0
    var fillMs = 0.0
    var lineMs = 0.0
    var triangles = 0
    var segments = 0
    var tiles = 0
}

/** The tiles at the zoom the map draws from that cover a [width] by [height] view of [camera]. */
fun visibleTiles(camera: Camera, width: Float, height: Float, tileZoom: Int): List<TileId> {
    val n = 1 shl tileZoom
    val half = camera.worldSize
    val left = camera.centerX - width / 2 / half
    val right = camera.centerX + width / 2 / half
    val top = camera.centerY - height / 2 / half
    val bottom = camera.centerY + height / 2 / half
    val tiles = ArrayList<TileId>()
    for (x in floor(left * n).toInt()..floor(right * n).toInt()) {
        for (y in floor(top * n).toInt()..floor(bottom * n).toInt()) {
            if (x in 0 until n && y in 0 until n) tiles += TileId(tileZoom, x, y)
        }
    }
    return tiles
}

/**
 * Draws [store]'s tiles under [camera], layer by layer of [style], and records what it drew in
 * [stats]: each tile's fills in one `drawTriangles` call and its lines in one `drawPolyline` call,
 * or, without [batched], one `drawTriangle` call a triangle and one `drawLine` call a segment, to
 * compare. With [labels], the point labels go over them, measured by it.
 */
fun DrawScope.drawMap(
    style: Style,
    store: TileStore,
    camera: Camera,
    stats: DrawStats,
    batched: Boolean = true,
    labels: TextMeasure? = null,
    labelStats: LabelStats = LabelStats(),
    labelCache: LabelCache = LabelCache(),
    settled: Boolean = false,
    sprites: SpriteSheet? = null,
    lineShapes: LineShapes = LineShapes(),
) {
    val start = TimeSource.Monotonic.markNow()
    store.version // a tile prepared draws the map again
    drawRect(style.background)
    val zoom = camera.zoom
    val tileZoom = floor(zoom).toInt().coerceIn(store.minZoom, store.maxZoom)
    val wanted = visibleTiles(camera, size.width, size.height, tileZoom)
    val tiles = wanted.mapNotNull { store.bestReady(it) }.distinctBy { it.id }
    // Tiles panned or zoomed past stop being fetched.
    store.retain(wanted)
    lineShapes.retain(tiles.map { it.id })
    var triangles = 0
    var segments = 0
    var fillTime = kotlin.time.Duration.ZERO
    var lineTime = kotlin.time.Duration.ZERO
    val world = camera.worldSize
    for (layer in style.layers) {
        if (zoom < layer.minZoom || zoom >= layer.maxZoom) continue
        val fill = layer.isFill
        val color = layer.drawColor(zoom) ?: continue
        if (color.alpha <= 0f) continue
        val width = if (fill) 0f else layer.number("line-width", zoom, 1.0).toFloat()
        for (tile in tiles) {
            val bucket = tile.buckets[layer.id] ?: continue
            val n = (1 shl tile.id.z).toDouble()
            val scale = (world / n).toFloat()
            val ox = ((tile.id.x / n - camera.centerX) * world + size.width / 2).toFloat()
            val oy = ((tile.id.y / n - camera.centerY) * world + size.height / 2).toFloat()
            // Tiles carry a buffer past their edges: clipped to the tile, a translucent fill isn't drawn twice there.
            clipRect(ox, oy, ox + scale, oy + scale) {
                val part = TimeSource.Monotonic.markNow()
                if (fill) {
                    if (batched) {
                        drawTriangles(color, bucket.fills, Offset(ox, oy), scale)
                        // One device pixel (Raven's thinnest line) over the mesh's aliased edges.
                        if (layer.antialias) layer.outlineColor(zoom)?.let { drawPolyline(it, bucket.packedLines, Offset(ox, oy), scale, 0f) }
                    } else {
                        val f = bucket.fills
                        for (i in f.indices step 6) {
                            drawTriangle(
                                color,
                                Offset(ox + f[i] * scale, oy + f[i + 1] * scale),
                                Offset(ox + f[i + 2] * scale, oy + f[i + 3] * scale),
                                Offset(ox + f[i + 4] * scale, oy + f[i + 5] * scale),
                            )
                        }
                    }
                    triangles += bucket.triangleCount
                    fillTime += part.elapsedNow()
                } else {
                    if (batched) {
                        // Dashed, or drawn either side of a gap: cut for the width drawn, kept while it holds.
                        val pattern = layer.dashes(zoom)
                        val gap = layer.number("line-gap-width", zoom, 0.0).toFloat()
                        val cap = layer.lineCap(zoom)
                        if (gap > 0f) {
                            // Each side's middle is half the gap and half its own width from the line's.
                            val side = gap / 2 / width.coerceAtLeast(1f) + 0.5f
                            for (s in listOf(side, -side)) {
                                drawPolyline(color, lineShapes.shape(tile.id, layer.id, bucket, pattern, s, width, scale), Offset(ox, oy), scale, width, cap)
                            }
                        } else {
                            drawPolyline(color, lineShapes.shape(tile.id, layer.id, bucket, pattern, 0f, width, scale), Offset(ox, oy), scale, width, cap)
                        }
                    } else {
                        for (line in bucket.lines) {
                            var px = ox + line[0] * scale
                            var py = oy + line[1] * scale
                            var i = 2
                            while (i < line.size) {
                                val x = ox + line[i] * scale
                                val y = oy + line[i + 1] * scale
                                drawLine(color, Offset(px, py), Offset(x, y), width)
                                px = x
                                py = y
                                i += 2
                            }
                        }
                    }
                    segments += bucket.segmentCount
                    lineTime += part.elapsedNow()
                }
            }
        }
    }
    if (labels != null) drawLabels(style, tiles, camera, labels, labelStats, labelCache, store.version, settled, sprites)
    stats.drawMs = start.elapsedNow().inWholeMicroseconds / 1000.0
    stats.fillMs = fillTime.inWholeMicroseconds / 1000.0
    stats.lineMs = lineTime.inWholeMicroseconds / 1000.0
    stats.triangles = triangles
    stats.segments = segments
    stats.tiles = tiles.size
}

/** The map, dragged to pan and scrolled to zoom, with what each frame costs over it. */
@Composable
fun MapScreen(style: Style, store: TileStore, camera: Camera, sprite: Sprite? = null, minZoom: Double = 2.0, maxZoom: Double = 18.0) {
    val stats = remember { DrawStats() }
    val labelStats = remember { LabelStats() }
    val measure = remember { TextMeasure() }
    val labelCache = remember { LabelCache() }
    val lineShapes = remember { LineShapes() }
    // The sprite sheet's image, decoded once Raven runs.
    val sprites = remember(sprite) { sprite?.let { SpriteSheet(it, it.png.decodeToImageBitmap()) } }
    // The camera at rest for a moment: the labels are placed again where it stopped.
    var settled by remember { mutableStateOf(true) }
    LaunchedEffect(camera.centerX, camera.centerY, camera.zoom) {
        settled = false
        delay(200)
        settled = true
    }
    var dragging by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(Offset(0f, 0f)) }
    val scope = rememberCoroutineScope()
    remember(store) { store.scope = scope }
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize().testTag("map")
                .onPointerEvent(PointerEventType.Press) { dragging = it.position }
                .onPointerEvent(PointerEventType.Release) { dragging = null }
                .onPointerEvent(PointerEventType.Move) { event ->
                    val from = dragging ?: return@onPointerEvent
                    camera.centerX -= (event.position.x - from.x) / camera.worldSize
                    camera.centerY -= (event.position.y - from.y) / camera.worldSize
                    dragging = event.position
                }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    camera.zoomAt(-event.scrollDelta.y * 0.25, event.position.x, event.position.y, size.x, size.y, minZoom, maxZoom)
                },
        ) {
            size = Offset(this.size.width, this.size.height)
            drawMap(style, store, camera, stats, labels = measure, labelStats = labelStats, labelCache = labelCache, settled = settled, sprites = sprites, lineShapes = lineShapes)
        }
        Stats(stats, labelStats, camera, store, Modifier.align(Alignment.TopStart).padding(12.dp))
        Surface(Modifier.align(Alignment.BottomEnd).padding(6.dp), shape = RoundedCornerShape(4.dp), color = Color(0xCCFFFFFF)) {
            Text("© OpenFreeMap © OpenMapTiles Data from OpenStreetMap", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color(0xFF333333), fontSize = 11.sp)
        }
    }
}

/** What the last frame drew and cost, read twice a second. */
@Composable
private fun Stats(stats: DrawStats, labelStats: LabelStats, camera: Camera, store: TileStore, modifier: Modifier) {
    val info = LocalWindowInfo.current
    var lines by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) {
        while (true) {
            val frame = info.frameStats()
            val prepared = store.preparedTiles
            lines = listOf(
                "zoom ${(camera.zoom * 100).toInt() / 100.0}, ${stats.tiles} tiles",
                "${stats.triangles} triangles, ${stats.segments} line segments",
                "Kotlin draw calls: ${fmt(stats.drawMs)} ms (fills ${fmt(stats.fillMs)}, lines ${fmt(stats.lineMs)}, labels ${fmt(labelStats.ms)})",
                "${labelStats.placed} labels placed of ${labelStats.candidates}, ${labelStats.icons} icons, in ${fmt(labelStats.placeMs)} ms" + if (labelStats.reused) " (reused)" else "",
                "Raven: paint ${fmt(frame.paintMs)} ms, render ${fmt(frame.renderMs)} ms, ${frame.primitiveCount} primitives, ${frame.drawCallCount} GPU draws",
                "${prepared.size} tiles prepared: decode ${fmt(prepared.sumOf { it.decodeMs })} ms, style + triangulate ${fmt(prepared.sumOf { it.prepareMs })} ms",
                "${store.pendingCount} loading, ${store.failures} failed" + ((store.source as? HttpTileSource)?.let { ", ${it.fetched} fetched, ${it.cached} from the cache" } ?: ""),
            )
            delay(500)
        }
    }
    Surface(modifier, shape = RoundedCornerShape(6.dp), color = Color(0xDD222222)) {
        Column(Modifier.padding(8.dp)) {
            for (line in lines) Text(line, color = Color.White, fontSize = 12.sp)
        }
    }
}

private fun fmt(ms: Double): String = ((ms * 10).toInt() / 10.0).toString()

