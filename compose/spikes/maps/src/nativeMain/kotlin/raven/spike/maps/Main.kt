package raven.spike.maps

import androidx.compose.runtime.remember
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import platform.posix.getenv
import raven.compose.ui.Window
import raven.compose.ui.application
import raven.compose.ui.rememberWindowState
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform

/** Central Chicago, the Loop, where fetch-data's tiles are. */
const val START_LATITUDE = 41.882
const val START_LONGITUDE = -87.630

/** OpenFreeMap's Liberty style, whose vector source is OpenFreeMap's planet. */
const val LIBERTY_STYLE = "https://tiles.openfreemap.org/styles/liberty"

@OptIn(ExperimentalForeignApi::class)
private fun env(name: String): String? = getenv(name)?.toKString()

/**
 * Online by default: the style from [LIBERTY_STYLE] (or `MAPS_STYLE`), its tiles fetched as the
 * map needs them and kept in a cache (`MAPS_CACHE`, else the user's cache folder). `MAPS_OFFLINE`
 * draws fetch-data's tiles instead (`MAPS_DATA`, else `data`). `MAPS_LAT`, `MAPS_LON` and
 * `MAPS_ZOOM` say where the map opens.
 */
fun main() {
    val latitude = env("MAPS_LAT")?.toDoubleOrNull() ?: START_LATITUDE
    val longitude = env("MAPS_LON")?.toDoubleOrNull() ?: START_LONGITUDE
    val zoom = env("MAPS_ZOOM")?.toDoubleOrNull() ?: 14.0
    val (style, source, sprite) = if (env("MAPS_OFFLINE") != null) offline() else online()
    val store = TileStore(source, style)
    application {
        val camera = remember { Camera.at(latitude, longitude, zoom) }
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(size = DpSize(1200.dp, 800.dp)),
            title = "Raven maps spike",
        ) { MapScreen(style, store, camera, sprite) }
    }
}

/** What the map draws: its style, where its tiles come from, and its sprite sheet, if it has one. */
private data class MapData(val style: Style, val source: TileSource, val sprite: Sprite?)

private fun offline(): MapData {
    val directory = env("MAPS_DATA") ?: "data"
    val style = Style.parse(readFile("$directory/liberty.json")?.decodeToString() ?: error("no $directory/liberty.json: run fetch-data"))
    val source = FileTileSource(directory)
    check(!source.isEmpty) { "no tiles listed in $directory/tiles.txt: run fetch-data" }
    val json = readFile("$directory/sprite@2x.json")?.decodeToString()
    val png = readFile("$directory/sprite@2x.png")
    return MapData(style, source, if (json != null && png != null) Sprite.parse(json, png) else null)
}

private fun online(): MapData = runBlocking {
    val client = HttpTileSource.client()
    val json = client.get(env("MAPS_STYLE") ?: LIBERTY_STYLE).bodyAsText()
    val style = Style.parse(json)
    @Suppress("UNCHECKED_CAST")
    val root = parseJson(json) as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val sources = root["sources"] as Map<String, Map<String, Any?>>
    val tileJson = sources.getValue("openmaptiles")["url"] as String
    // The sheet at twice the pixels, sharp on a Retina display.
    val sprite = (root["sprite"] as? String)?.let { url ->
        Sprite.parse(client.get("$url@2x.json").bodyAsText(), client.get("$url@2x.png").bodyAsBytes())
    }
    MapData(style, HttpTileSource.fromTileJson(client, tileJson, env("MAPS_CACHE") ?: cacheDirectory()), sprite)
}

/** The user's cache folder for the spike's tiles: Library/Caches on macOS, else XDG's. */
@OptIn(ExperimentalNativeApi::class)
private fun cacheDirectory(): String? {
    val home = env("HOME") ?: return null
    val base = if (Platform.osFamily == OsFamily.MACOSX) "$home/Library/Caches" else env("XDG_CACHE_HOME") ?: "$home/.cache"
    return "$base/compose-raven-maps-spike"
}
