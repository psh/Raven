package raven.spike.maps

import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv
import raven.compose.ui.Window
import raven.compose.ui.application
import raven.compose.ui.rememberWindowState
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp

/** Central Chicago, the Loop, where fetch-data's tiles are. */
const val START_LATITUDE = 41.882
const val START_LONGITUDE = -87.630

@OptIn(ExperimentalForeignApi::class)
fun main() {
    val directory = getenv("MAPS_DATA")?.toKString() ?: "data"
    val zoom = getenv("MAPS_ZOOM")?.toKString()?.toDoubleOrNull() ?: 14.0
    val style = Style.parse(readFile("$directory/liberty.json")?.decodeToString() ?: error("no $directory/liberty.json: run fetch-data"))
    val store = TileStore.open(directory, style)
    check(store.available.isNotEmpty()) { "no tiles listed in $directory/tiles.txt: run fetch-data" }
    application {
        val camera = remember { Camera.at(START_LATITUDE, START_LONGITUDE, zoom) }
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(size = DpSize(1200.dp, 800.dp)),
            title = "Raven maps spike",
        ) { MapScreen(style, store, camera) }
    }
}
