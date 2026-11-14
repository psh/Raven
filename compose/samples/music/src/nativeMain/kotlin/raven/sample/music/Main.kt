package raven.sample.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import platform.posix.getenv
import raven.compose.ui.graphics.BorderStroke
import raven.compose.foundation.HeaderBar
import raven.compose.foundation.Surface
import raven.compose.foundation.TextField
import raven.compose.foundation.TextFieldDefaults
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.Spacer
import raven.compose.foundation.layout.fillMaxHeight
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.height
import raven.compose.foundation.layout.padding
import raven.compose.foundation.layout.size
import raven.compose.foundation.layout.width
import raven.compose.navigation3.NavDisplay
import raven.compose.ui.Alignment
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.alpha
import raven.compose.ui.application
import raven.compose.ui.clip
import raven.compose.ui.focus.FocusRequester
import raven.compose.ui.focus.focusRequester
import raven.compose.ui.graphics.CircleShape
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.Effect
import raven.compose.ui.graphics.EffectStyle
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.graphics.ShaderEffect
import raven.compose.ui.graphics.isSpecified
import raven.compose.ui.input.key.Key
import raven.compose.ui.input.key.KeyEvent
import raven.compose.ui.input.key.KeyEventType
import raven.compose.ui.material
import raven.compose.ui.rememberWindowState
import raven.compose.ui.testTag
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.theme.loadTheme
import raven.compose.ui.tooltip
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp
import kotlin.random.Random

// A port of Raven's examples/music: a music player, only the interface, no server and no sound
// (the songs, made up, "play" by the clock).
//
//   NavDisplay                  the five pages, a page visited again brought to the front, back
//                               with Alt+Left, Cmd+[ or the mouse's Back button (Window(backKeys))
//   RavenTheme(theme, accent)   the player's themes, or a look of Raven; the accent follows the
//                               cover playing
//   MusicStyle                  what the C example's theme files say of its classes, read by the
//                               app's own composables (Widgets.kt): IconButton, Cover, Visualizer...
//   Surface(effect)             the colours of the cover moving behind the panels, and the heroes
//   Island                      what plays, unfolded into a small player; in a MacBook's notch
//
//   Space        play / pause         Ctrl+Left / Right   previous / next
//   Ctrl+L       like the song        Ctrl+F              search
//   Ctrl+P       settings             Alt+Left            back

@Serializable
data object Home : NavKey

@Serializable
data class AlbumPage(val album: Int) : NavKey

@Serializable
data class PlaylistPage(val playlist: Int) : NavKey

@Serializable
data object SearchPage : NavKey

@Serializable
data object LibraryPage : NavKey

@Serializable
data object SettingsPage : NavKey

/** Kotlin/Native has no reflection, so `NavKey`'s open polymorphism is declared by hand. */
private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Home::class)
            subclass(AlbumPage::class)
            subclass(PlaylistPage::class)
            subclass(SearchPage::class)
            subclass(LibraryPage::class)
            subclass(SettingsPage::class)
        }
    }
}

private const val NOTICE_NANOS = 2_500_000_000L

/** The backgrounds Settings offers, in its order; the last is none. */
val Backgrounds = listOf("Mesh", "Aurora", "Plasma", "Spotlight", "Shimmer", "None")
private val BackgroundEffects = listOf(Effect.Mesh, Effect.Aurora, Effect.Plasma, Effect.Spotlight, Effect.Shimmer)

/** The theme files of the example, loaded: the three themes by their names, and the one laid over a look. */
class MusicThemes(directory: String) {
    init {
        for (file in listOf("nocturne", "aurora", "daylight")) loadTheme("$directory/themes/$file.xml")
    }

    val overTheLook: String = loadTheme("$directory/themes/styles.xml")
}

/** Everything the player shows: the player, the pages, the look and the settings. */
class MusicState(val library: Library, random: Random = Random.Default) {
    val player = Player(library, random)

    var lookIndex by mutableIntStateOf(0)
    val look: MusicLook get() = MusicLooks[lookIndex]

    /** The accent follows the cover playing, or is [picked]. */
    var followCover by mutableStateOf(true)
    var picked by mutableStateOf(Color(0.72f, 0.61f, 1f))
    var background by mutableIntStateOf(0)
    var animate by mutableStateOf(true)
    var panel by mutableStateOf(true)
    var crossfade by mutableFloatStateOf(5f)
    var normalize by mutableStateOf(true)
    var quality by mutableIntStateOf(2)

    var query by mutableStateOf("")
    var libraryFilter by mutableIntStateOf(0)
    var islandExpanded by mutableStateOf(false)

    /** A short word in the folded island, for a moment: [notices] counts them, each starting its own moment. */
    var notice by mutableStateOf<String?>(null)
        private set
    var notices by mutableIntStateOf(0)
        private set

    /** Asked to focus the search field: Ctrl+F, Search in the side bar. */
    val searchFocus = FocusRequester()

    lateinit var backStack: NavBackStack<NavKey>

    val page: NavKey get() = backStack.last()

    /** Shows [key]; a page visited again moves to the top instead of repeating. */
    fun navigate(key: NavKey) {
        if (backStack.lastOrNull() == key) return
        backStack.remove(key)
        backStack.add(key)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeLast()
    }

    fun say(text: String) {
        notice = text
        notices++
    }

    fun endNotice() {
        notice = null
    }

    /** The accent: the cover's colour, darker on a light window, or the one picked. */
    fun accent(): Color {
        val album = player.currentAlbum
        return if (followCover && album != null) album.color.toColor(if (look.dark) 1f else 0.62f) else picked
    }

    fun toggle() {
        if (player.currentTrack == null) {
            navigate(AlbumPage(0))
            player.playList(library.albumTracks(0), 0, library.albums[0].title)
        } else {
            player.playing = !player.playing
        }
    }

    fun like() {
        val track = player.currentTrack ?: return
        likeTrack(track)
    }

    fun likeTrack(track: Track) {
        track.liked = !track.liked
        say(if (track.liked) "Added to Liked songs" else "Removed from Liked songs")
    }

    fun toggleShuffle() {
        player.shuffle = !player.shuffle
        say(if (player.shuffle) "Shuffle on" else "Shuffle off")
    }

    fun cycleRepeat() {
        player.repeat = Repeat.entries[(player.repeat.ordinal + 1) % Repeat.entries.size]
        say(
            when (player.repeat) {
                Repeat.Off -> "Repeat off"
                Repeat.All -> "Repeat all"
                Repeat.One -> "Repeat this song"
            },
        )
    }

    fun mute() {
        player.muted = !player.muted
        if (!player.muted && player.volume <= 0.001f) player.volume = 0.5f
    }

    fun setVolume(value: Float) {
        player.volume = value
        player.muted = value <= 0.001f
    }

    /** The search field changed: Search shows what matches. */
    fun search(text: String) {
        query = text
        if (page != SearchPage) navigate(SearchPage)
    }

    fun openSearch() {
        navigate(SearchPage)
        searchFocus.requestFocus()
    }

    /** Plays [track] from the list it is in, else from its album. */
    fun playTrack(track: Int, list: List<Int>?, context: String?) {
        val start = list?.indexOf(track) ?: -1
        if (list != null && context != null && start >= 0) {
            player.playList(list, start, context)
        } else {
            val album = library.albums[library.tracks[track].album]
            player.playList(library.albumTracks(album.index), track - album.firstTrack, album.title)
        }
    }

    /** The keys nothing in the window took. */
    fun onKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val command = event.isCtrlPressed || event.isMetaPressed
        when {
            event.key == Key.Spacebar && !command -> toggle()
            command && event.key == Key.DirectionRight -> player.next()
            command && event.key == Key.DirectionLeft -> player.previous()
            command && event.key == Key.L -> like()
            command && event.key == Key.F -> openSearch()
            command && event.key == Key.P -> navigate(SettingsPage)
            else -> return false
        }
        return true
    }

    companion object {
        /** As the C example opens: album 4's third song, 47 seconds in. */
        fun opening(library: Library, playing: Boolean = true, random: Random = Random.Default): MusicState = MusicState(library, random).apply {
            player.playList(library.albumTracks(3), 2, library.albums[3].title)
            player.seconds = 47.0
            player.playing = playing
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun env(name: String): String? = getenv(name)?.toKString()

fun main() = application {
    val themes = remember { MusicThemes(EXAMPLE_DIR) }
    val state = remember {
        MusicState.opening(Library.create(EXAMPLE_DIR), playing = env("RAVEN_MUSIC_PAUSED") == null).apply {
            env("RAVEN_MUSIC_LOOK")?.let { name -> MusicLooks.indexOfFirst { it.name == name }.takeIf { it >= 0 }?.let { lookIndex = it } }
            islandExpanded = env("RAVEN_MUSIC_ISLAND") != null
        }
    }
    val icons = remember { Icons() }
    MusicApp(state, themes, icons, onQuit = ::exitApplication) {
        // What the C example opens on, to show a page (RAVEN_MUSIC_PAGE).
        when (env("RAVEN_MUSIC_PAGE")) {
            "album" -> state.navigate(AlbumPage(7))
            "playlist" -> state.navigate(PlaylistPage(1))
            "search" -> state.search("the")
            "genres" -> state.navigate(SearchPage)
            "library" -> state.navigate(LibraryPage)
            "settings" -> state.navigate(SettingsPage)
        }
    }
}

/** The player's window, in its look. [onStart] runs once the back stack is there. */
@Composable
fun MusicApp(state: MusicState, themes: MusicThemes, icons: Icons, onQuit: () -> Unit, onStart: () -> Unit = {}) {
    val look = state.look
    RavenTheme(look = look.look, dark = look.dark, theme = look.theme ?: themes.overTheLook, accent = state.accent()) {
        CompositionLocalProvider(LocalMusicStyle provides look.style, LocalIcons provides icons) {
            val track = state.player.currentTrack
            Window(
                onCloseRequest = onQuit,
                state = rememberWindowState(size = DpSize(1360.dp, 860.dp)),
                title = track?.let { "${it.title} · ${state.library.albums[it.album].artist}" } ?: "Raven Music",
                customHeader = true,
                minimumSize = DpSize(1040.dp, 660.dp),
                island = { MusicIsland(state) },
                onKeyEvent = state::onKey,
            ) {
                state.backStack = rememberNavBackStack(navConfig, Home)
                LaunchedEffect(Unit) { onStart() }
                MusicScreen(state)
            }
        }
    }
}

@Composable
private fun MusicScreen(state: MusicState) {
    val player = state.player
    // The songs "play" by the clock, frames asked for only while one plays.
    LaunchedEffect(player.playing) {
        if (!player.playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            player.advance((now - last) / 1e9)
            last = now
        }
    }
    // A notice lasts a moment, by the frame clock.
    LaunchedEffect(state.notices) {
        if (state.notice == null) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (withFrameNanos { it } - start < NOTICE_NANOS) continue
        state.endNotice()
    }
    Box(Modifier.fillMaxSize()) {
        Backdrop(state, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            Header(state)
            Row(Modifier.weight(1f).fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SideBar(state, Modifier.width(300.dp).fillMaxHeight())
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    NavDisplay(
                        state.backStack,
                        Modifier.fillMaxSize().testTag("pages"),
                        onBack = state::back,
                        entryProvider = entryProvider {
                            entry<Home> { HomePage(state) }
                            entry<AlbumPage> { AlbumCollection(state, it.album) }
                            entry<PlaylistPage> { PlaylistCollection(state, it.playlist) }
                            entry<SearchPage> { SearchResults(state) }
                            entry<LibraryPage> { LibraryContents(state) }
                            entry<SettingsPage> { SettingsContents(state) }
                        },
                    )
                }
                if (state.panel) NowPlaying(state, Modifier.width(320.dp).fillMaxHeight())
            }
            PlayerBar(state)
        }
    }
}

/** The colours of the cover, moving slowly behind everything. */
@Composable
private fun Backdrop(state: MusicState, modifier: Modifier) {
    val effect = BackgroundEffects.getOrNull(state.background) ?: return
    val album = state.player.currentAlbum ?: state.library.albums[0]
    val dark = state.look.dark
    Surface(
        modifier.alpha(if (dark) 0.9f else 0.45f),
        effect = remember(effect) { ShaderEffect.builtin(effect) },
        effectStyle = EffectStyle(album.color.toColor(if (dark) 0.55f else 1f), album.color2.toColor(if (dark) 0.35f else 0.9f), animate = state.animate, pointer = false),
    ) { Box(Modifier.fillMaxSize()) {} }
}

/** One of the panels in front of the backdrop, as the look's "surface". */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val style = LocalMusicStyle.current
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier.clip(shape).let { m -> style.surfaceMaterial?.let { m.material(it) } ?: m },
        shape = shape,
        color = style.surface,
        border = if (style.surfaceBorder.isSpecified) BorderStroke(1.dp, style.surfaceBorder) else null,
        content = content,
    )
}

/** The window's title bar: dragged to move the window, with back, home, the search and settings. */
@Composable
private fun Header(state: MusicState) {
    val style = LocalMusicStyle.current
    HeaderBar(Modifier.fillMaxWidth().height(56.dp), title = "", background = false) {
        Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(30.dp), shape = RoundedCornerShape(9.dp), color = Color.White) {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { Icon(Glyph.Note, 18.dp, tint = Color(0xFF060609)) }
            }
            Heading("Raven Music", 16f)
        }
        Spacer(Modifier.width(36.dp))
        IconButton(Glyph.Back, "Back (Alt+Left)", state::back, size = 18.dp, enabled = state.backStack.size > 1)
        IconButton(Glyph.Home, "Home", { state.navigate(Home) })
        Surface(
            Modifier.width(280.dp).height(40.dp).let { m -> style.searchMaterial?.let { m.material(it) } ?: m },
            shape = CircleShape,
            color = style.search,
            border = if (style.searchBorder.isSpecified) BorderStroke(1.dp, style.searchBorder) else null,
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Glyph.Search, 18.dp, Modifier.alpha(0.7f))
                TextField(
                    state.query, state::search,
                    Modifier.weight(1f).focusRequester(state.searchFocus).testTag("search"),
                    placeholder = "What do you want to play?",
                    colors = TextFieldDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }
        }
        // Packed from the right.
        Surface(
            Modifier.align(Alignment.End).padding(end = 8.dp).size(30.dp).tooltip("Mira Solen"),
            shape = CircleShape,
            color = Color(0xFFE8A93B),
        ) { Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { Heading("M", 14f, color = Color(0xFF060609)) } }
        IconButton(Glyph.Settings, "Settings (Ctrl+P)", { state.navigate(SettingsPage) }, Modifier.align(Alignment.End))
    }
}
