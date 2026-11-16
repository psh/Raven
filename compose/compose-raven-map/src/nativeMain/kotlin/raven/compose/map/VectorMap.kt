package raven.compose.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import raven.cinterop.RMapStats
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_MAP
import raven.cinterop.Rid
import raven.cinterop.Rmap_get_center
import raven.cinterop.Rmap_get_stats
import raven.cinterop.Rmap_get_zoom
import raven.cinterop.Rmap_set_attribution
import raven.cinterop.Rmap_set_center
import raven.cinterop.Rmap_set_sprite
import raven.cinterop.Rmap_set_style_json
import raven.cinterop.Rmap_set_tile_capacity
import raven.cinterop.Rmap_set_tile_source
import raven.cinterop.Rmap_set_zoom
import raven.cinterop.Rmap_set_zoom_range
import raven.cinterop.Rmap_tile_failed
import raven.cinterop.Rmap_tile_loaded
import raven.cinterop.Rwidget_is_valid
import raven.compose.ui.MapCameraChanged
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import kotlin.math.abs

/**
 * A MapLibre style: its JSON, the vector [source] it draws (null: its first), and its sprite sheet,
 * the JSON index and the PNG it names (null: no icons), such as the style's "sprite" URL with
 * "@2x.json" and "@2x.png".
 */
public class MapStyle(
    public val json: String,
    public val source: String? = null,
    public val spriteIndex: String? = null,
    public val spritePng: ByteArray? = null,
)

/** Where a map's tiles come from: the app's own fetching, over HTTP, from a cache or from files. */
public interface TileSource {
    /** The zooms the source has tiles for; past [maxZoom], the map draws the deepest tiles larger. */
    public val minZoom: Int get() = 0
    public val maxZoom: Int get() = 14

    /**
     * The bytes of tile [z]/[x]/[y] (a Mapbox Vector Tile, not compressed), or null where there is
     * none. Throws when it can't tell, such as on a network error: the map asks again later. Called
     * on `Dispatchers.Default`, and cancelled when the map no longer needs the tile.
     */
    public suspend fun tile(z: Int, x: Int, y: Int): ByteArray?
}

/** What a map's last frame drew, and what its tiles and labels cost (Raven's `RMapStats`). */
public data class MapStats(
    val tiles: Int,
    val triangles: Int,
    val segments: Int,
    val drawMs: Double,
    val preparedTiles: Int,
    val pendingTiles: Int,
    val failedTiles: Int,
    val requestedTiles: Int,
    val memoryBytes: Long,
    val decodeMs: Double,
    val prepareMs: Double,
    val labelsPlaced: Int,
    val labelCandidates: Int,
    val icons: Int,
    val labelsMs: Double,
    val placeMs: Double,
    val labelsReused: Boolean,
)

/**
 * Where a [VectorMap] looks: its centre in degrees and its zoom, fractional. The app moves the map
 * by writing them; the user's drags and scrolls write them too.
 */
public class VectorMapState(latitude: Double, longitude: Double, zoom: Double) {
    public var latitude: Double by mutableDoubleStateOf(latitude)
    public var longitude: Double by mutableDoubleStateOf(longitude)
    public var zoom: Double by mutableDoubleStateOf(zoom)

    internal var widget: Rid = R_INVALID_ID

    /** What the map's last frame drew and cost, or null before it is shown. */
    @OptIn(ExperimentalForeignApi::class)
    public fun stats(): MapStats? {
        if (widget == R_INVALID_ID || Rwidget_is_valid(widget) != R_TRUE) return null
        return memScoped {
            val s = alloc<RMapStats>()
            Rmap_get_stats(widget, s.ptr)
            MapStats(
                s.tiles, s.triangles, s.segments, s.draw_ms, s.prepared_tiles, s.pending_tiles, s.failed_tiles, s.requested_tiles,
                s.memory_bytes.toLong(), s.decode_ms, s.prepare_ms, s.labels_placed, s.label_candidates, s.icons, s.labels_ms, s.place_ms,
                s.labels_reused != 0,
            )
        }
    }
}

/** A [VectorMapState] remembered, opening at [latitude], [longitude] and [zoom]. */
@Composable
public fun rememberVectorMapState(latitude: Double, longitude: Double, zoom: Double): VectorMapState =
    remember { VectorMapState(latitude, longitude, zoom) }

private val MapWidget = WidgetDescriptor("VectorMap", R_WIDGET_MAP, events = listOf(MapCameraChanged))

private val StyleProperty = Property<MapStyle> { widget, style ->
    Rmap_set_style_json(widget, style.json, -1, style.source)
    val index = style.spriteIndex
    val png = style.spritePng
    if (index != null && png != null && png.isNotEmpty()) {
        png.usePinned { Rmap_set_sprite(widget, index, -1, it.addressOf(0), png.size.convert()) }
    } else {
        Rmap_set_sprite(widget, null, 0, null, 0u)
    }
}

private val AttributionProperty = Property<String?> { widget, text -> Rmap_set_attribution(widget, text) }

private val ZoomRangeProperty = Property<ClosedFloatingPointRange<Double>> { widget, range ->
    Rmap_set_zoom_range(widget, range.start, range.endInclusive)
}

private val CapacityProperty = Property<Int> { widget, capacity -> Rmap_set_tile_capacity(widget, capacity) }

/** Moves the widget to where the state says, unless it is there already (the user's own move, read back). */
@OptIn(ExperimentalForeignApi::class)
private fun moveWidget(widget: Rid, latitude: Double, longitude: Double, zoom: Double) {
    memScoped {
        val lat = alloc<DoubleVar>()
        val lon = alloc<DoubleVar>()
        Rmap_get_center(widget, lat.ptr, lon.ptr)
        if (abs(lat.value - latitude) > 1e-9 || abs(lon.value - longitude) > 1e-9) Rmap_set_center(widget, latitude, longitude)
    }
    if (abs(Rmap_get_zoom(widget) - zoom) > 1e-9) Rmap_set_zoom(widget, zoom)
}

/**
 * The tiles the map asks for, fetched from the [source] on `Dispatchers.Default` in [scope], and
 * handed to the widget as they come (Raven prepares them on its workers); a tile the map no longer
 * needs is cancelled. Held by a [StableRef] the widget's callbacks find it by.
 */
private class TileLoader(val scope: CoroutineScope) {
    var widget: Rid = R_INVALID_ID
    var source: TileSource? = null
    private val jobs = HashMap<Long, Job>()
    private var ref: StableRef<TileLoader>? = null

    private fun key(z: Int, x: Int, y: Int): Long = (z.toLong() shl 56) or (x.toLong() shl 28) or y.toLong()

    @OptIn(ExperimentalForeignApi::class)
    fun attach(widget: Rid, source: TileSource) {
        this.widget = widget
        this.source = source
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        val ref = ref ?: StableRef.create(this).also { ref = it }
        Rmap_set_tile_source(widget, source.minZoom, source.maxZoom, onRequest, onCancel, ref.asCPointer())
    }

    fun request(z: Int, x: Int, y: Int) {
        val source = source ?: return
        val widget = widget
        val key = key(z, x, y)
        jobs[key]?.cancel()
        jobs[key] = scope.launch {
            try {
                val bytes = try {
                    withContext(Dispatchers.Default) { source.tile(z, x, y) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Rmap_tile_failed(widget, z, x, y)
                    return@launch
                }
                loaded(widget, z, x, y, bytes)
            } finally {
                if (jobs[key] === coroutineContext[Job]) jobs.remove(key)
            }
        }
    }

    fun cancel(z: Int, x: Int, y: Int) {
        jobs.remove(key(z, x, y))?.cancel()
    }

    @OptIn(ExperimentalForeignApi::class)
    fun dispose() {
        if (widget != R_INVALID_ID && Rwidget_is_valid(widget) == R_TRUE) Rmap_set_tile_source(widget, 0, 14, null, null, null)
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        ref?.dispose()
        ref = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun loaded(widget: Rid, z: Int, x: Int, y: Int, bytes: ByteArray?) {
    if (bytes == null || bytes.isEmpty()) {
        Rmap_tile_loaded(widget, z, x, y, null, 0u)
    } else {
        bytes.usePinned { Rmap_tile_loaded(widget, z, x, y, it.addressOf(0), bytes.size.convert()) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun loader(userData: COpaquePointer?): TileLoader? = userData?.asStableRef<TileLoader>()?.get()

@OptIn(ExperimentalForeignApi::class)
private val onRequest = staticCFunction { _: Rid, z: Int, x: Int, y: Int, userData: COpaquePointer? ->
    // nothing thrown crosses into C
    try {
        loader(userData)?.request(z, x, y)
    } catch (_: Throwable) {
    }
    Unit
}

@OptIn(ExperimentalForeignApi::class)
private val onCancel = staticCFunction { _: Rid, z: Int, x: Int, y: Int, userData: COpaquePointer? ->
    try {
        loader(userData)?.cancel(z, x, y)
    } catch (_: Throwable) {
    }
    Unit
}

/**
 * A vector map: [tiles] drawn as [style] says, by Raven's Map widget, which decodes, styles and
 * labels them in C on its worker threads. It looks where [state] says; the user drags it to pan and
 * scrolls to zoom, within [zoomRange], and [state] follows. [attribution] is shown in its corner,
 * as the tiles' licence usually asks; past [capacity] prepared tiles, those used least recently go.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
public fun VectorMap(
    style: MapStyle,
    tiles: TileSource,
    modifier: Modifier = Modifier,
    state: VectorMapState = rememberVectorMapState(0.0, 0.0, 2.0),
    attribution: String? = null,
    zoomRange: ClosedFloatingPointRange<Double> = 2.0..18.0,
    capacity: Int = 160,
) {
    val scope = rememberCoroutineScope()
    val loader = remember { TileLoader(scope) }
    DisposableEffect(loader) { onDispose { loader.dispose() } }
    val latitude = state.latitude
    val longitude = state.longitude
    val zoom = state.zoom
    // The user moved the map: the state follows, and composing it back finds the widget there already.
    val onCameraChanged = remember(state) {
        {
            memScoped {
                val lat = alloc<DoubleVar>()
                val lon = alloc<DoubleVar>()
                Rmap_get_center(state.widget, lat.ptr, lon.ptr)
                state.latitude = lat.value
                state.longitude = lon.value
            }
            state.zoom = Rmap_get_zoom(state.widget)
        }
    }
    RavenWidget(MapWidget, modifier, {
        set(state) { state.widget = widget }
        bind(style, StyleProperty)
        set(tiles) { loader.attach(widget, it) }
        bind(attribution, AttributionProperty)
        bind(zoomRange, ZoomRangeProperty)
        bind(capacity, CapacityProperty)
        set(Triple(latitude, longitude, zoom)) { moveWidget(widget, it.first, it.second, it.third) }
        bind(onCameraChanged, MapCameraChanged)
    })
}
