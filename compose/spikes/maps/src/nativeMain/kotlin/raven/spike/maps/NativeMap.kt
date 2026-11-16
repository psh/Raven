package raven.spike.maps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import raven.compose.foundation.Surface
import raven.compose.foundation.Text
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.padding
import raven.compose.map.MapStyle
import raven.compose.map.MapView
import raven.compose.map.MapViewState
import raven.compose.map.rememberMapViewState
import raven.compose.ui.Alignment
import raven.compose.ui.Modifier
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.platform.LocalWindowInfo
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp
import raven.compose.map.TileSource as MapTileSource

/** The spike's tile source, as compose-raven-map asks for tiles. */
private class NativeTiles(private val source: TileSource) : MapTileSource {
    override val minZoom: Int get() = source.minZoom
    override val maxZoom: Int get() = source.maxZoom

    // An empty tile (no tile there) is handed on as such.
    override suspend fun tile(z: Int, x: Int, y: Int): ByteArray? = source.fetch(TileId(z, x, y))
}

/**
 * The map drawn by Raven's native Map widget, through compose-raven-map's MapView: the style's
 * [json] and [sprite] handed to it, the tiles fetched from [source] as it asks for them, with what
 * each frame costs over it, as [MapScreen] shows for the Kotlin drawing.
 */
@Composable
fun NativeMapScreen(json: String, source: TileSource, sprite: Sprite?, latitude: Double, longitude: Double, zoom: Double) {
    val style = remember(json, sprite) { MapStyle(json, spriteIndex = sprite?.json, spritePng = sprite?.png) }
    val tiles = remember(source) { NativeTiles(source) }
    val state = rememberMapViewState(latitude, longitude, zoom)
    Box(Modifier.fillMaxSize()) {
        MapView(
            style,
            tiles,
            Modifier.fillMaxSize().testTag("map"),
            state,
            attribution = "© OpenFreeMap © OpenMapTiles Data from OpenStreetMap",
        )
        NativeStats(state, source, Modifier.align(Alignment.TopStart).padding(12.dp))
    }
}

/** What the native map's last frame drew and cost, read twice a second. */
@Composable
private fun NativeStats(state: MapViewState, source: TileSource, modifier: Modifier) {
    val info = LocalWindowInfo.current
    var lines by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) {
        while (true) {
            val frame = info.frameStats()
            val s = state.stats()
            lines = if (s == null) emptyList() else listOf(
                "zoom ${fmt(state.zoom)}, ${s.tiles} tiles (native)",
                "${s.triangles} triangles, ${s.segments} line segments",
                "C draw calls: ${fmt(s.drawMs)} ms (labels ${fmt(s.labelsMs)})",
                "${s.labelsPlaced} labels placed of ${s.labelCandidates}, ${s.icons} icons, in ${fmt(s.placeMs)} ms" + if (s.labelsReused) " (reused)" else "",
                "Raven: paint ${fmt(frame.paintMs)} ms, render ${fmt(frame.renderMs)} ms, ${frame.primitiveCount} primitives, ${frame.drawCallCount} GPU draws",
                "${s.preparedTiles} tiles prepared (${fmt(s.memoryBytes / 1048576.0)} MB): decode ${fmt(s.decodeMs)} ms, style + triangulate ${fmt(s.prepareMs)} ms",
                "${s.requestedTiles} asked for, ${s.pendingTiles} loading, ${s.failedTiles} failed" +
                    ((source as? HttpTileSource)?.let { ", ${it.fetched} fetched, ${it.cached} from the cache" } ?: ""),
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

private fun fmt(value: Double): String = ((value * 10).toInt() / 10.0).toString()
