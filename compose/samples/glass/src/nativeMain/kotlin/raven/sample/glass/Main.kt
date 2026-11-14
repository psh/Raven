package raven.sample.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import raven.compose.foundation.Button
import raven.compose.foundation.CheckboxItem
import raven.compose.foundation.Checkbox
import raven.compose.foundation.ComboBox
import raven.compose.foundation.Item
import raven.compose.foundation.DropdownMenu
import raven.compose.foundation.HorizontalDivider
import raven.compose.foundation.Image
import raven.compose.foundation.LinearProgressIndicator
import raven.compose.foundation.OutlinedButton
import raven.compose.foundation.RadioButton
import raven.compose.foundation.Separator
import raven.compose.foundation.Slider
import raven.compose.foundation.SpinBox
import raven.compose.foundation.Surface
import raven.compose.foundation.Switch
import raven.compose.foundation.Text
import raven.compose.foundation.TextButton
import raven.compose.foundation.TextField
import raven.compose.foundation.TreeView
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.FlowRow
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.Spacer
import raven.compose.foundation.layout.VerticalScroll
import raven.compose.foundation.layout.fillMaxHeight
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.padding
import raven.compose.foundation.layout.width
import raven.compose.foundation.layout.widthIn
import raven.compose.foundation.lazy.grid.GridCells
import raven.compose.foundation.lazy.grid.LazyVerticalGrid
import raven.compose.foundation.lazy.grid.itemsIndexed
import raven.compose.foundation.rememberDropdownMenuState
import raven.compose.ui.Alignment
import raven.compose.ui.AppearTransition
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.alpha
import raven.compose.ui.appearTransition
import raven.compose.ui.application
import raven.compose.ui.clip
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.ui.graphics.Material
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.input.key.Key
import raven.compose.ui.input.key.KeyShortcut
import raven.compose.ui.layout.ContentScale
import raven.compose.ui.material
import raven.compose.ui.rememberWindowState
import raven.compose.ui.testTag
import raven.compose.ui.theme.Look
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

// A port of Raven's examples/glass: the glass look, its translucent controls that blur and bend
// what is behind them, and panels of glass floating over a page that scrolls under them.
//
//   RavenTheme(look = Look.Glass, dark)   every control becomes glass, where the C example applies
//                                         R_THEME_GLASS_DARK or _LIGHT
//   Modifier.material(...)                a panel of glass: clear (Glass), frosted or tinted, the
//                                         C example's Material="..."
//   withFrameNanos                        the player's time, while it plays: the C example's frame
//                                         callback and continuous redraw
//
// Glass shows what is behind it, so it needs something to show: the look gives the window a soft
// coloured wallpaper, and a page of coloured cards scrolls under the floating bars.

fun main(args: Array<String>) = application {
    // "glass light" starts with the light glass.
    var dark by remember { mutableStateOf(args.firstOrNull() != "light") }
    RavenTheme(look = Look.Glass, dark = dark) {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(size = DpSize(1100.dp, 720.dp)),
            title = "Raven - glass",
            minimumSize = DpSize(820.dp, 560.dp),
        ) { GlassScreen(dark, { dark = it }, onQuit = ::exitApplication) }
    }
}

/** A song lasts four minutes. */
internal const val SONG_SECONDS = 240f

/** The player bar's state: where in the song it is, and whether it plays. */
internal class Player {
    var playing by mutableStateOf(true)
    var seconds by mutableFloatStateOf(84f)

    val time: String get() = seconds.toInt().let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }
}

@Composable
internal fun GlassScreen(dark: Boolean, onDarkChange: (Boolean) -> Unit, onQuit: () -> Unit, player: Player = remember { Player() }) {
    // The player advances while it plays: frames are asked for only then.
    LaunchedEffect(player.playing) {
        if (!player.playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            player.seconds = (player.seconds + (now - last) / 1e9f).let { if (it > SONG_SECONDS) 0f else it }
            last = now
        }
    }
    Box(Modifier.fillMaxSize()) {
        Page(player)
        SideBar(dark, onDarkChange, Modifier.align(Alignment.CenterStart))
        ToolBar(onQuit, Modifier.align(Alignment.TopCenter))
        PlayerBar(player, Modifier.align(Alignment.BottomCenter))
    }
}

private val CardTitles = listOf("Sunrise", "Deep water", "Citrus", "Orchid", "Glacier", "Ember", "Meadow", "Dusk", "Coral")

/** Each card's two colours, melted together. */
private val CardColors = listOf(
    floatArrayOf(1.00f, 0.60f, 0.20f, 0.95f, 0.20f, 0.45f), floatArrayOf(0.05f, 0.30f, 0.75f, 0.10f, 0.80f, 0.85f),
    floatArrayOf(0.98f, 0.85f, 0.15f, 0.40f, 0.85f, 0.25f), floatArrayOf(0.65f, 0.25f, 0.90f, 0.98f, 0.45f, 0.75f),
    floatArrayOf(0.55f, 0.85f, 1.00f, 0.20f, 0.35f, 0.80f), floatArrayOf(0.90f, 0.20f, 0.10f, 1.00f, 0.70f, 0.15f),
    floatArrayOf(0.20f, 0.70f, 0.35f, 0.75f, 0.95f, 0.45f), floatArrayOf(0.25f, 0.15f, 0.55f, 0.95f, 0.45f, 0.35f),
    floatArrayOf(1.00f, 0.45f, 0.40f, 1.00f, 0.80f, 0.60f),
)

private const val CARD_SIZE = 96

/** A small picture for a card: two colours melted together with a bright spot. */
internal fun cardPixels(index: Int): ByteArray {
    val c = CardColors[index]
    val pixels = ByteArray(CARD_SIZE * CARD_SIZE * 4)
    for (y in 0 until CARD_SIZE) {
        for (x in 0 until CARD_SIZE) {
            val u = x.toFloat() / (CARD_SIZE - 1)
            val v = y.toFloat() / (CARD_SIZE - 1)
            // A diagonal blend, bent by a wave so that no two cards look alike.
            val t = (0.5f * (u + v) + 0.18f * sin(u * 5f + index) * cos(v * 4f - index * 0.7f)).coerceIn(0f, 1f)
            val du = u - 0.72f
            val dv = v - 0.28f
            val spot = exp(-(du * du + dv * dv) * 9f) * 0.45f
            val out = (y * CARD_SIZE + x) * 4
            for (k in 0 until 3) pixels[out + k] = (255f * (c[k] + (c[k + 3] - c[k]) * t + spot).coerceAtMost(1f)).toInt().toByte()
            pixels[out + 3] = -1
        }
    }
    return pixels
}

@Composable
private fun Page(player: Player) {
    VerticalScroll(Modifier.fillMaxSize().testTag("page")) {
        Column(Modifier.padding(start = 304.dp, top = 92.dp, end = 28.dp, bottom = 120.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Good evening", Modifier.appearTransition(AppearTransition.FlyUp, 100.milliseconds), fontSize = 36.sp)
            Text(
                "Everything that floats on this page is glass: it blurs and bends what scrolls behind it, picks up its colors " +
                    "and catches the light on its rim. Scroll, or drag a slider and watch its knob turn into a lens.",
                Modifier.alpha(0.75f).appearTransition(AppearTransition.FlyUp, 180.milliseconds),
                softWrap = true,
            )
            Cards()
            Text("Controls", Modifier.padding(top = 10.dp), fontSize = 22.sp)
            Controls()
            LinearProgressIndicator(player.seconds / SONG_SECONDS, Modifier.width(420.dp).testTag("progress"))
        }
    }
}

@Composable
private fun Cards() {
    val images = remember { CardTitles.indices.map { ImageBitmap(CARD_SIZE, CARD_SIZE, cardPixels(it)) } }
    LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxWidth().testTag("cards"), minCellSize = DpSize(200.dp, 150.dp), spacing = 16.dp) {
        itemsIndexed(CardTitles) { index, title ->
            // The cards pop in one after the other: a picture with rounded corners and its title on a chip of glass.
            Box(Modifier.appearTransition(AppearTransition.Pop, (300 + 60 * index).milliseconds)) {
                Image(images[index], contentDescription = title, Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
                Surface(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp).material(Material.Glass), shape = RoundedCornerShape(100.dp)) {
                    Text(title, Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun Controls() {
    var quality by remember { mutableIntStateOf(1) }
    var text by remember { mutableStateOf("") }
    var number by remember { mutableIntStateOf(4) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = {}) { Text("Glass") }
        Button(onClick = {}) { Text("Tinted") }
        OutlinedButton(onClick = {}, enabled = false) { Text("Disabled") }
        ComboBox(listOf("Lossless", "High quality", "Data saver"), quality, { quality = it })
        TextField(text, { text = it }, Modifier.width(180.dp), placeholder = "Type here")
        SpinBox(number, { number = it }, Modifier.width(90.dp), valueRange = 0..10)
    }
    var check by remember { mutableStateOf(true) }
    var radio by remember { mutableStateOf(true) }
    var on by remember { mutableStateOf(true) }
    var off by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
        Labelled("Check") { Checkbox(check, { check = it }) }
        Labelled("Radio") { RadioButton(radio, { radio = !radio }) }
        Labelled("Switch") { Switch(on, { on = it }) }
        Labelled("Off") { Switch(off, { off = it }) }
    }
}

@Composable
private fun Labelled(label: String, control: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        control()
        Text(label)
    }
}

/** An entry of the library. */
private data class LibraryItem(val name: String, val children: List<LibraryItem> = emptyList())

private val Library = listOf(
    LibraryItem("Listen now"),
    LibraryItem("Browse"),
    LibraryItem("Playlists", listOf(LibraryItem("Favourites"), LibraryItem("Focus"), LibraryItem("Late night"))),
    LibraryItem("Artists"),
    LibraryItem("Albums"),
)

@Composable
private fun SideBar(dark: Boolean, onDarkChange: (Boolean) -> Unit, modifier: Modifier) {
    var search by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf<Set<Any>>(emptySet()) }
    var crossfade by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.62f) }
    Surface(
        modifier.padding(start = 18.dp, top = 18.dp, bottom = 18.dp).width(264.dp).fillMaxHeight()
            .material(Material.FrostedGlass).appearTransition(AppearTransition.FlyRight),
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 28.dp,
    ) {
        Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Library", fontSize = 22.sp)
            TextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = "Search")
            TreeView(
                Library, { it.children }, { it.name }, { it.name }, selection, { selection = it },
                initiallyExpanded = { it.name == "Playlists" },
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("library"),
            )
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Dark glass", Modifier.weight(1f))
                Switch(dark, onDarkChange, Modifier.testTag("dark"))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Crossfade", Modifier.weight(1f))
                Switch(crossfade, { crossfade = it })
            }
            Text("Volume", Modifier.alpha(0.7f))
            Slider(volume, { volume = it }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ToolBar(onQuit: () -> Unit, modifier: Modifier) {
    val menu = rememberDropdownMenuState()
    var lossless by remember { mutableStateOf(true) }
    Surface(
        modifier.padding(start = 286.dp, top = 18.dp).material(Material.TintedGlass).appearTransition(AppearTransition.Drop, 250.milliseconds),
        shape = RoundedCornerShape(100.dp),
        shadowElevation = 24.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = {}) { Text("Today") }
            TextButton(onClick = {}) { Text("Library") }
            TextButton(onClick = {}) { Text("Radio") }
            // A menu is a popup of glass: it opens under the button.
            Box {
                TextButton(onClick = menu::open, Modifier.testTag("more")) { Text("More") }
                DropdownMenu(menu, offset = raven.compose.ui.unit.DpOffset(0.dp, 8.dp)) {
                    Item("Add to library", onClick = {})
                    Item("Add to a playlist", onClick = {})
                    CheckboxItem("Lossless audio", lossless, { lossless = it })
                    Separator()
                    Item("Quit", onClick = onQuit, shortcut = KeyShortcut(Key.Q, ctrl = true))
                }
            }
            Button(onClick = {}) { Text("Share") }
        }
    }
}

@Composable
private fun PlayerBar(player: Player, modifier: Modifier) {
    Surface(
        modifier.padding(start = 286.dp, bottom = 22.dp).material(Material.Glass).appearTransition(AppearTransition.FlyUp, 350.milliseconds),
        shape = RoundedCornerShape(100.dp),
        shadowElevation = 30.dp,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {}) { Text("Previous") }
            Button(onClick = { player.playing = !player.playing }, Modifier.widthIn(min = 76.dp).testTag("play")) { Text(if (player.playing) "Pause" else "Play") }
            TextButton(onClick = {}) { Text("Next") }
            // Dragged, the time goes where it was left.
            Slider(player.seconds / SONG_SECONDS, { player.seconds = it * SONG_SECONDS }, Modifier.width(240.dp).testTag("position"))
            Text(player.time, Modifier.widthIn(min = 40.dp).alpha(0.7f).testTag("time"))
        }
    }
}
