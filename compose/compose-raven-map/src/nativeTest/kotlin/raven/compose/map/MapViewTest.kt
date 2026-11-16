package raven.compose.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CompletableDeferred
import platform.posix.usleep
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Rmap_get_center
import raven.cinterop.Rmap_get_zoom
import raven.cinterop.Rwidget_get_rect
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.compose.foundation.layout.size
import raven.compose.ui.Modifier
import raven.compose.ui.test.RavenTestScope
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sinh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalForeignApi::class)
class MapViewTest {
    /** A source of the water tile anywhere, counting what it is asked; [gate] holds a tile until completed, [failing] throws. */
    private class FakeSource(
        val gate: CompletableDeferred<Unit>? = null,
        val failing: Boolean = false,
    ) : TileSource {
        override val minZoom = 14
        override val maxZoom = 14
        var asked = 0
        var cancelled = 0

        override suspend fun tile(z: Int, x: Int, y: Int): ByteArray? {
            asked++
            try {
                gate?.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled++
                throw e
            }
            if (failing) error("no network")
            return waterTile()
        }
    }

    /** Draws frames until [done], or fails after two seconds: tiles land from other threads. */
    private fun RavenTestScope.waitUntil(what: String, done: () -> Boolean) {
        repeat(400) {
            if (done()) return
            frame()
            usleep(5_000u)
        }
        fail("timed out waiting for $what")
    }

    private fun tileCenter(x: Int, y: Int): Pair<Double, Double> {
        val n = 16384.0
        return atan(sinh(PI * (1 - 2 * (y + 0.5) / n))) * 180 / PI to (x + 0.5) / n * 360 - 180
    }

    @Test
    fun aMapAsksForItsTilesAndDrawsThem() = runRavenTest {
        val source = FakeSource()
        val (lat, lon) = tileCenter(4203, 6089)
        val state = MapViewState(lat, lon, 14.0)
        setContent {
            MapView(MapStyle(STYLE), source, Modifier.size(500.dp, 400.dp).testTag("map"), state, attribution = "© test")
        }
        waitUntil("the tile") { state.stats()?.preparedTiles == 1 }
        frame()
        val stats = assertNotNull(state.stats())
        assertEquals(1, source.asked, "asked for once")
        assertEquals(1, stats.tiles)
        assertEquals(2, stats.triangles)
    }

    @Test
    fun theStateMovesTheMapAndTheUserMovesTheState() = runRavenTest {
        val (lat, lon) = tileCenter(4203, 6089)
        val state = MapViewState(lat, lon, 14.0)
        setContent { MapView(MapStyle(STYLE), FakeSource(), Modifier.size(500.dp, 400.dp).testTag("map"), state) }
        waitForIdle()
        val widget = onNodeWithTag("map").fetchWidget()
        // written by the app
        state.zoom = 15.0
        waitForIdle()
        assertEquals(15.0, Rmap_get_zoom(widget), 1e-9)
        // dragged by the user: the state follows, and the widget isn't moved back
        val (x, y) = memScoped {
            val rx = alloc<kotlinx.cinterop.FloatVar>()
            val ry = alloc<kotlinx.cinterop.FloatVar>()
            Rwidget_get_rect(widget, rx.ptr, ry.ptr, null, null)
            rx.value to ry.value
        }
        val window = Rwidget_get_window(widget)
        Rwindow_inject_mouse_move(window, x + 200f, y + 200f)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
        Rwindow_inject_mouse_move(window, x + 300f, y + 200f)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
        waitForIdle()
        assertTrue(state.longitude < lon, "dragged right, the map looks further west")
        val widgetLongitude = memScoped {
            val a = alloc<DoubleVar>()
            val b = alloc<DoubleVar>()
            Rmap_get_center(widget, a.ptr, b.ptr)
            b.value
        }
        assertEquals(state.longitude, widgetLongitude, 1e-9)
        assertEquals(100.0 / (512.0 * 32768.0) * 360.0, lon - state.longitude, 1e-9)
    }

    @Test
    fun aTileNoLongerWantedIsCancelled() = runRavenTest {
        val gate = CompletableDeferred<Unit>()
        val source = FakeSource(gate = gate)
        val (lat, lon) = tileCenter(4203, 6089)
        val state = MapViewState(lat, lon, 14.0)
        setContent { MapView(MapStyle(STYLE), source, Modifier.size(500.dp, 400.dp), state) }
        waitUntil("the request") { source.asked == 1 }
        // panned far away: the tile still loading is cancelled
        val (lat2, lon2) = tileCenter(4300, 6089)
        state.latitude = lat2
        state.longitude = lon2
        waitUntil("the cancellation") { source.cancelled == 1 }
        assertEquals(2, source.asked)
        gate.complete(Unit)
    }

    @Test
    fun aTileThatCannotBeFetchedCountsAsFailed() = runRavenTest {
        val (lat, lon) = tileCenter(4203, 6089)
        val state = MapViewState(lat, lon, 14.0)
        setContent { MapView(MapStyle(STYLE), FakeSource(failing = true), Modifier.size(500.dp, 400.dp), state) }
        waitUntil("the failure") { state.stats()?.failedTiles == 1 }
        assertEquals(0, state.stats()?.preparedTiles)
        assertTrue(abs(Rmap_get_zoom(state.widget) - 14.0) < 1e-9)
    }

    @Test
    fun aMapLeavingTheCompositionLeavesNothingBehind() = runRavenTest {
        val gate = CompletableDeferred<Unit>()
        val source = FakeSource(gate = gate)
        val (lat, lon) = tileCenter(4203, 6089)
        var shown by mutableStateOf(true)
        setContent {
            if (shown) MapView(MapStyle(STYLE), source, Modifier.size(500.dp, 400.dp), rememberMapViewState(lat, lon, 14.0))
        }
        waitUntil("the request") { source.asked == 1 }
        shown = false
        // the fetch on its way is cancelled, on the thread it runs on
        waitUntil("the cancellation") { source.cancelled == 1 }
        gate.complete(Unit)
        waitForIdle()
    }

    private companion object {
        const val STYLE = """{"version": 8, "sources": {"openmaptiles": {"type": "vector"}}, "layers": [
            {"id": "background", "type": "background", "paint": {"background-color": "#f8f4f0"}},
            {"id": "water", "type": "fill", "source": "openmaptiles", "source-layer": "water", "paint": {"fill-color": "#9ebdff"}}
        ]}"""
    }
}

/** A tile with one layer, "water", holding a square. */
private fun waterTile(): ByteArray {
    val geometry = intArrayOf(command(1, 1), zz(2), zz(2), command(2, 3), zz(4), zz(0), zz(0), zz(4), zz(-4), zz(0), command(7, 1))
    val feature = message(2, packed(intArrayOf(0, 0))) + varintField(3, 3) + message(4, packed(geometry))
    val layer = message(1, "water".encodeToByteArray()) + message(2, feature) + message(3, "class".encodeToByteArray()) +
        message(4, message(1, "lake".encodeToByteArray())) + varintField(5, 16) + varintField(15, 2)
    return message(3, layer)
}

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
