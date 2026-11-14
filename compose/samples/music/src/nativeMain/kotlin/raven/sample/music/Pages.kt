package raven.sample.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import raven.compose.foundation.ColorPicker
import raven.compose.foundation.ComboBox
import raven.compose.foundation.HorizontalDivider
import raven.compose.foundation.Island
import raven.compose.foundation.OutlinedButton
import raven.compose.foundation.Slider
import raven.compose.foundation.SliderDefaults
import raven.compose.foundation.Surface
import raven.compose.foundation.Switch
import raven.compose.foundation.Text
import raven.compose.foundation.TextButton
import raven.compose.foundation.ButtonDefaults
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.FlowRow
import raven.compose.foundation.layout.Grid
import raven.compose.foundation.layout.GridTrack
import raven.compose.foundation.layout.HorizontalScroll
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.Spacer
import raven.compose.foundation.layout.VerticalScroll
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.height
import raven.compose.foundation.layout.padding
import raven.compose.foundation.layout.size
import raven.compose.foundation.layout.width
import raven.compose.foundation.layout.widthIn
import raven.compose.ui.Alignment
import raven.compose.ui.AppearTransition
import raven.compose.ui.Modifier
import raven.compose.ui.PaddingValues
import raven.compose.ui.alpha
import raven.compose.ui.appearTransition
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.Effect
import raven.compose.ui.graphics.EffectStyle
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.graphics.ShaderEffect
import raven.compose.ui.input.pointer.PointerIcon
import raven.compose.ui.input.pointer.pointerHoverIcon
import raven.compose.ui.material
import raven.compose.ui.semantics.semantics
import raven.compose.ui.testTag
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp
import kotlin.random.Random
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.posix.localtime
import platform.posix.time
import platform.posix.time_tVar

// The pages and the parts around them: the side bar, Home, an album or a playlist, Search, the
// library, the settings, the panel of the song playing, the player bar and the island.

private val AuroraEffect = ShaderEffect.builtin(Effect.Aurora)

/** "Not in this demo": what the player does not do. */
private fun MusicState.notHere() = say("Not in this demo")

@Composable
fun SideBar(state: MusicState, modifier: Modifier) {
    val page = state.page
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NavItem(Glyph.Home, "Home", page == Home, { state.navigate(Home) }, Modifier.fillMaxWidth())
                NavItem(Glyph.Search, "Search", page == SearchPage, state::openSearch, Modifier.fillMaxWidth())
                NavItem(Glyph.Settings, "Settings", page == SettingsPage, { state.navigate(SettingsPage) }, Modifier.fillMaxWidth())
            }
        }
        Panel(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().padding(start = 10.dp, top = 10.dp, end = 6.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    NavItem(Glyph.Library, "Your library", page == LibraryPage, { state.navigate(LibraryPage) }, Modifier.weight(1f))
                    IconButton(Glyph.Plus, "Create a playlist", state::notHere, size = 18.dp)
                }
                Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Playlists", "Albums").forEachIndexed { i, name ->
                        Chip(name, state.libraryFilter == i, { state.libraryFilter = i })
                    }
                }
                VerticalScroll(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val library = state.library
                        if (state.libraryFilter != 2) {
                            for (p in 0 until PLAYLISTS) {
                                val list = library.playlist(p)
                                LibraryRow(library.albums[list.cover].cover, list.title, "Playlist · ${list.tracks.size} songs", { state.navigate(PlaylistPage(p)) }, Modifier.testTag("sidebar.playlist.$p"))
                            }
                        }
                        if (state.libraryFilter != 1) {
                            for (album in library.albums) {
                                LibraryRow(album.cover, album.title, "Album · ${album.artist}", { state.navigate(AlbumPage(album.index)) }, Modifier.testTag("sidebar.album.${album.index}"))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A page's scrolling column, with the C example's padding. */
@Composable
private fun PageColumn(modifier: Modifier = Modifier, spacing: Int = 14, content: @Composable () -> Unit) {
    VerticalScroll(Modifier.fillMaxSize()) {
        Column(modifier.fillMaxWidth().padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(spacing.dp)) { content() }
    }
}

/** A row of cards that scrolls sideways. */
@Composable
private fun CardRow(content: @Composable () -> Unit) {
    HorizontalScroll(Modifier.fillMaxWidth().height(286.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

@Composable
private fun AlbumCardOf(state: MusicState, album: Album, round: Boolean = false) {
    if (round) AlbumCard(album.cover, album.artist, "Artist", { state.navigate(AlbumPage(album.index)) }, round = true)
    else AlbumCard(album.cover, album.title, "${album.year} · ${album.artist}", { state.navigate(AlbumPage(album.index)) })
}

@OptIn(ExperimentalForeignApi::class)
private fun greeting(): String {
    val hour = memScoped {
        val now = alloc<time_tVar>()
        now.value = time(null)
        localtime(now.ptr)?.pointed?.tm_hour ?: 20
    }
    return when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
fun HomePage(state: MusicState) {
    val library = state.library
    val player = state.player
    PageColumn {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Music", "Podcasts", "Audiobooks").forEachIndexed { i, name -> Chip(name, i == 0, { state.say("Only music here") }) }
        }
        Heading(remember { greeting() }, 30f, Modifier.padding(top = 8.dp).appearTransition(AppearTransition.FlyUp))
        // Six tiles: the liked songs, a playlist and albums.
        Grid(List(3) { GridTrack.Fill() }, emptyList(), Modifier.fillMaxWidth().appearTransition(AppearTransition.Fade), spacing = 10.dp) {
            for (i in 0 until 6) {
                val cell = Modifier.cell(i % 3, i / 3)
                if (i < 2) {
                    val p = if (i == 0) 0 else 2
                    val list = library.playlist(p)
                    QuickCard(library.albums[list.cover].cover, list.title, false, player.playing, { state.navigate(PlaylistPage(p)) }, cell)
                } else {
                    val album = library.albums[(i * 5 + 3) % ALBUMS]
                    QuickCard(album.cover, album.title, player.currentAlbum == album, player.playing, { state.navigate(AlbumPage(album.index)) }, cell)
                }
            }
        }

        SectionHeader("Made for you") { state.navigate(LibraryPage) }
        CardRow {
            for (p in 1 until PLAYLISTS) {
                val list = library.playlist(p)
                AlbumCard(library.albums[list.cover].cover, list.title, list.description, { state.navigate(PlaylistPage(p)) })
            }
        }
        SectionHeader("Recently played") { state.navigate(LibraryPage) }
        CardRow { for (i in 0 until 10) AlbumCardOf(state, library.albums[(i * 7 + 2) % ALBUMS]) }
        SectionHeader("Your favorite artists")
        CardRow { for (i in 0 until 10) AlbumCardOf(state, library.albums[(i * 3 + 1) % ALBUMS], round = true) }
        SectionHeader("New releases") { state.navigate(LibraryPage) }
        // The newest first; a stable sort keeps the C example's order among a year's.
        CardRow { for (album in library.albums.sortedByDescending { it.year }.take(10)) AlbumCardOf(state, album) }
    }
}

@Composable
fun AlbumCollection(state: MusicState, index: Int) {
    val library = state.library
    val album = library.albums[index]
    val seconds = library.albumSeconds(index)
    val liked = index in state.player.likedAlbums
    Collection(
        state, index, "ALBUM", album.title, "", album.artist,
        "·  ${album.year}  ·  ${album.trackCount} songs, ${seconds / 60} min ${seconds % 60} s",
        library.albumTracks(index), showAlbum = false,
        footer = "${album.year}. ${album.genre}. Released by Raven Records under a license that does not exist.",
        liked = liked,
        onLike = {
            if (liked) state.player.likedAlbums.remove(index) else state.player.likedAlbums.add(index)
            state.say(if (liked) "Removed from your library" else "Saved to your library")
        },
    )
}

@Composable
fun PlaylistCollection(state: MusicState, index: Int) {
    val library = state.library
    // The liked songs are made again as hearts change.
    val list = library.playlist(index)
    val seconds = list.tracks.sumOf { library.tracks[it].seconds }
    Collection(
        state, list.cover, "PLAYLIST", list.title, list.description, "Raven Music",
        "·  ${list.tracks.size} songs, about ${seconds / 3600} h ${((seconds / 60) % 60).toString().padStart(2, '0')} min",
        list.tracks, showAlbum = true, footer = "", liked = false,
        onLike = { state.say("It is your playlist already") },
    )
}

/** The page of an album or a playlist: its hero in the colours of the cover, then its songs. */
@Composable
private fun Collection(
    state: MusicState,
    cover: Int,
    kind: String,
    title: String,
    description: String,
    artist: String,
    meta: String,
    tracks: List<Int>,
    showAlbum: Boolean,
    footer: String,
    liked: Boolean,
    onLike: () -> Unit,
) {
    val library = state.library
    val player = state.player
    val album = library.albums[cover]
    val accent = RavenTheme.colors.accent
    VerticalScroll(Modifier.fillMaxSize()) {
        // The top of the page in dark shades of the cover's colours: its texts are white, whatever the theme.
        Surface(
            Modifier.fillMaxWidth(),
            effect = AuroraEffect,
            effectStyle = EffectStyle(album.color.toColor(0.55f), album.color2.toColor(0.22f), animate = state.animate, pointer = false),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 28.dp, top = 40.dp, end = 28.dp, bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.Bottom) {
                Surface(shape = RoundedCornerShape(8.dp), shadowElevation = 14.dp) {
                    Cover(album.cover, 232.dp, 8.dp, Modifier.appearTransition(AppearTransition.Zoom))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eyebrow(kind, color = Color.White)
                    // A long title in smaller letters.
                    val size = if (title.length <= 12) 60f else if (title.length <= 18) 46f else 38f
                    Text(
                        title, Modifier.fillMaxWidth().appearTransition(AppearTransition.FlyUp).testTag("collection.title"),
                        color = Color.White, fontSize = size.sp, fontWeight = raven.compose.ui.text.font.FontWeight.Bold, softWrap = true,
                    )
                    if (description.isNotEmpty()) Text(description, Modifier.fillMaxWidth().alpha(0.8f), color = Color.White, softWrap = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(album.cover, 24.dp, 12.dp)
                        Heading(artist, 14f, color = Color.White)
                        Text(meta, Modifier.alpha(0.75f), color = Color.White)
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                val here = player.isPlaying(tracks)
                PlayButton(here, {
                    // The button of a page plays that page: a pause only while that page plays.
                    if (player.queue == tracks && tracks.isNotEmpty() && !player.shuffle) player.playing = !player.playing
                    else player.playList(tracks, 0, title)
                }, Modifier.testTag("collection.play"), 56.dp, 24.dp)
                IconButton(Glyph.Shuffle, "Play in random order", {
                    val keep = player.shuffle
                    player.shuffle = true
                    if (tracks.isNotEmpty()) player.playList(tracks, Random.nextInt(tracks.size), title)
                    player.shuffle = keep
                }, size = 26.dp)
                IconButton(if (liked) Glyph.HeartFilled else Glyph.Heart, "Save to your library", onLike, size = 26.dp, tint = if (liked) accent else Color.Unspecified)
                IconButton(Glyph.Download, "Download", state::notHere, size = 24.dp)
                IconButton(Glyph.More, "More options", state::notHere, size = 24.dp)
                Spacer(Modifier.weight(1f))
                Muted("List")
                Icon(Glyph.Queue, 18.dp, Modifier.alpha(0.7f))
            }
            // The heads of the columns, as the rows are laid out.
            Grid(
                listOf(GridTrack.Auto, GridTrack.Fill(4f), GridTrack.Fill(3f), GridTrack.Auto, GridTrack.Auto), emptyList(),
                Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 12.dp), spacing = 14.dp,
            ) {
                Muted("#", Modifier.cell(0, 0).width(26.dp))
                Muted("Title", Modifier.cell(1, 0))
                Muted(if (showAlbum) "Album" else "", Modifier.cell(2, 0))
                Spacer(Modifier.cell(3, 0).width(32.dp))
                Row(Modifier.cell(4, 0).width(44.dp)) {
                    Spacer(Modifier.weight(1f))
                    Icon(Glyph.Clock, 16.dp, Modifier.alpha(0.7f))
                }
            }
            HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 6.dp))
            Column(Modifier.fillMaxWidth()) {
                tracks.forEachIndexed { i, t ->
                    val track = library.tracks[t]
                    TrackRow(
                        library, track, i + 1, showAlbum,
                        highlight = if (player.track == t) accent else Color.Unspecified, accent = accent,
                        onPlay = { player.playList(tracks, i, title) },
                        onLike = { state.likeTrack(track) },
                    )
                }
                if (tracks.isEmpty()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Heading("Songs you like will appear here", 20f)
                        Muted("Save songs by tapping the heart icon.")
                    }
                }
            }
            if (footer.isNotEmpty()) Muted(footer, Modifier.fillMaxWidth().padding(start = 12.dp, top = 18.dp, end = 12.dp), 12f, wrap = true)
        }
    }
}

private fun String.has(part: String) = contains(part, ignoreCase = true)

/** Search: the genres while nothing is typed, then what matches. */
@Composable
fun SearchResults(state: MusicState) {
    val library = state.library
    val query = state.query
    PageColumn {
        if (query.isEmpty()) {
            Heading("Browse all", 26f)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Genres.forEachIndexed { i, genre ->
                    val cover = library.albums.firstOrNull { it.genre == genre } ?: library.albums[i % ALBUMS]
                    GenreTile(genre, genreColor(i), cover.cover, { state.query = genre })
                }
            }
        } else {
            val albums = library.albums.filter { it.title.has(query) || it.artist.has(query) || it.genre.has(query) }
            val songs = library.tracks.filter { it.title.has(query) || library.albums[it.album].artist.has(query) }.take(5)
            val top = albums.firstOrNull() ?: songs.firstOrNull()?.let { library.albums[it.album] }
            val accent = RavenTheme.colors.accent
            val style = LocalMusicStyle.current
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Heading("Top result", 22f)
                    if (top != null) {
                        val b = style.top
                        OutlinedButton(
                            { state.navigate(AlbumPage(top.index)) },
                            Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand).testTag("search.top"),
                            shape = RoundedCornerShape(style.cardRadius.dp),
                            colors = ButtonDefaults.buttonColors(b.container, b.content, hoveredContainerColor = b.hovered, pressedContainerColor = b.pressed),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Cover(top.cover, 96.dp, 8.dp)
                                Text(top.title, Modifier.fillMaxWidth(), fontSize = 26.sp, fontWeight = raven.compose.ui.text.font.FontWeight.Bold, softWrap = true)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Muted("Album")
                                    Muted("·")
                                    Text(top.artist)
                                }
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Heading("Songs", 22f, Modifier.padding(bottom = 6.dp))
                    songs.forEachIndexed { i, track ->
                        TrackRow(
                            library, track, i + 1, false,
                            highlight = if (state.player.track == track.index) accent else Color.Unspecified, accent = accent,
                            onPlay = { state.playTrack(track.index, null, null) },
                            onLike = { state.likeTrack(track) },
                        )
                    }
                }
            }
            SectionHeader("Albums")
            CardRow { for (album in albums.take(10)) AlbumCardOf(state, album) }
            if (top == null) Muted("Nothing matches \"${query.take(60)}\". Try another word: a genre, a city, a color...", Modifier.testTag("search.none"), 16f)
        }
    }
}

/** Everything in your library, as cards. */
@Composable
fun LibraryContents(state: MusicState) {
    val library = state.library
    val liked = library.playlist(0).tracks.size
    PageColumn {
        Heading("Your library", 30f)
        Muted("$PLAYLISTS playlists, $ALBUMS albums (${state.player.likedAlbums.size} saved), $liked liked songs.")
        SectionHeader("Playlists")
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (p in 0 until PLAYLISTS) {
                val list = library.playlist(p)
                AlbumCard(library.albums[list.cover].cover, list.title, "${list.tracks.size} songs", { state.navigate(PlaylistPage(p)) })
            }
        }
        SectionHeader("Albums")
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (album in library.albums) AlbumCardOf(state, album)
        }
    }
}

/** The settings: the look, the accent, the background, the panel; playback, which does nothing. */
@Composable
fun SettingsContents(state: MusicState) {
    VerticalScroll(Modifier.fillMaxSize()) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(start = 32.dp, top = 24.dp, end = 32.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Heading("Settings", 30f)
            Eyebrow("APPEARANCE", Modifier.padding(top = 8.dp))
            SettingRow("Look", "A theme of the player, or a look of Raven: Windows, macOS, GNOME, glass, high contrast.") {
                ComboBox(MusicLooks.map { it.name }, state.lookIndex, { state.lookIndex = it }, Modifier.width(260.dp).testTag("settings.look"))
            }
            SettingRow("Accent from the cover", "The color of the controls follows the cover of what plays.") {
                Switch(state.followCover, { state.followCover = it }, Modifier.testTag("settings.follow"))
            }
            SettingRow("Accent", "The color of the controls, when it does not follow the cover.") {
                ColorPicker(state.picked, {
                    state.picked = it
                    state.followCover = false
                }, Modifier.testTag("settings.accent"), popup = true, alpha = false)
            }
            SettingRow("Background", "What moves behind the panels, in the colors of the cover.") {
                ComboBox(Backgrounds, state.background, { state.background = it }, Modifier.width(160.dp))
            }
            SettingRow("Animate the background", "Off, the background stays still and the window draws only when something changes.") {
                Switch(state.animate, { state.animate = it })
            }
            SettingRow("Now playing panel", "The panel on the right, with the cover and what comes next.") {
                Switch(state.panel, { state.panel = it }, Modifier.testTag("settings.panel"))
            }

            HorizontalDivider()
            Eyebrow("PLAYBACK")
            SettingRow("Crossfade", "Songs fade into each other.") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Slider(state.crossfade, { state.crossfade = it }, Modifier.width(180.dp), valueRange = 0f..12f, steps = 11)
                    Text("${(state.crossfade + 0.5f).toInt()} s", Modifier.width(36.dp))
                }
            }
            SettingRow("Normalize the volume", "The same loudness for every song.") {
                Switch(state.normalize, { state.normalize = it })
            }
            SettingRow("Streaming quality", "Nothing is streamed: the songs of this player are made up.") {
                ComboBox(listOf("Low", "Normal", "High", "Very high"), state.quality, { state.quality = it }, Modifier.width(160.dp))
            }

            HorizontalDivider()
            Eyebrow("ABOUT")
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Heading("Raven Music", 15f)
                    Text("is a showcase of Raven: no server, no sound, only the interface.", Modifier.fillMaxWidth(), softWrap = true)
                    for (line in listOf(
                        "the pages are a NavDisplay of Navigation 3",
                        "the cards, the rows and the tiles are the app's own composables",
                        "Icon, Cover, Visualizer and Heading are made by the application",
                        "the themes are the files of examples/music/themes; the accent follows the cover playing",
                        "the island at the top lives in the notch of a MacBook",
                    )) Text("•  $line", Modifier.fillMaxWidth(), softWrap = true)
                }
            }
        }
    }
}

/** The panel of the song playing, on the right. */
@Composable
fun NowPlaying(state: MusicState, modifier: Modifier) {
    val library = state.library
    val player = state.player
    val track = player.currentTrack
    val album = player.currentAlbum ?: library.albums[0]
    Panel(modifier.testTag("nowplaying")) {
        VerticalScroll(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Heading(player.context, 15f, Modifier.weight(1f))
                    IconButton(Glyph.More, "More options", state::notHere, size = 18.dp)
                    IconButton(Glyph.Close, "Hide the panel", { state.panel = false }, size = 18.dp)
                }
                Surface(shape = RoundedCornerShape(10.dp), shadowElevation = 10.dp) { Cover(album.cover, 288.dp, 10.dp) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Heading(track?.title ?: "", 22f, Modifier.testTag("np.title"))
                        Muted(album.artist, size = 15f)
                    }
                    IconButton(Glyph.Share, "Share", state::notHere)
                    LikeButton(state, 22.dp, "Save to Liked songs (Ctrl+L)")
                }
                Visualizer(player.playing, 36, 3f, Modifier.fillMaxWidth().height(56.dp))

                // The artist, over the colours of the album.
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    effect = AuroraEffect,
                    effectStyle = EffectStyle(album.color.toColor(0.7f), album.color2.toColor(0.3f), animate = state.animate, pointer = false),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Eyebrow("About the artist", color = Color.White)
                        Heading(album.artist, 20f, color = Color.White)
                        Text("${1 + (album.index * 37) % 90},${((album.index * 211) % 1000).toString().padStart(3, '0')} monthly listeners", Modifier.alpha(0.8f), color = Color.White)
                        Text(
                            "${album.artist} makes ${album.genre} from ${if (album.index % 2 == 1) "a small room" else "a big city"}, ${album.year}. Their last album, ${album.title}, came out this year.",
                            Modifier.fillMaxWidth().alpha(0.85f), color = Color.White, fontSize = 13.sp, softWrap = true,
                        )
                    }
                }

                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = LocalMusicStyle.current.cardFlat) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Heading("Next in queue", 15f, Modifier.weight(1f))
                            TextButton(state::notHere, colors = ButtonDefaults.buttonColors(contentColor = LocalMusicStyle.current.link)) { Text("Open queue") }
                        }
                        val next = (player.position + 1 until player.queue.size).take(4)
                        for (i in next) {
                            val t = library.tracks[player.queue[i]]
                            val a = library.albums[t.album]
                            LibraryRow(a.cover, t.title, a.artist, { player.playPosition(i) })
                        }
                        if (next.isEmpty()) Muted("Nothing after this song.", Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}

/** The heart of the song playing: on, it wears the accent. */
@Composable
private fun LikeButton(state: MusicState, size: raven.compose.ui.unit.Dp, tip: String, modifier: Modifier = Modifier) {
    val liked = state.player.currentTrack?.liked == true
    IconButton(
        if (liked) Glyph.HeartFilled else Glyph.Heart, tip, state::like, modifier, size,
        tint = if (liked) RavenTheme.colors.accent else Color.Unspecified,
    )
}

/** Where the song is, and a slider that moves it. */
@Composable
private fun Seek(state: MusicState, labelSize: Float, modifier: Modifier = Modifier, labelWidth: Boolean = true) {
    val player = state.player
    val length = player.currentTrack?.seconds ?: 0
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Muted(formatTime(player.seconds.toInt()), if (labelWidth) Modifier.width(40.dp) else Modifier, labelSize)
        Slider(
            if (length > 0) (player.seconds / length).toFloat().coerceIn(0f, 1f) else 0f,
            { if (length > 0) player.seconds = it.toDouble() * length },
            Modifier.weight(1f),
            colors = SliderDefaults.colors(inactiveTrackColor = LocalMusicStyle.current.seekTrack),
        )
        Muted(formatTime(length), if (labelWidth) Modifier.width(40.dp) else Modifier, labelSize)
    }
}

/** The bar of the player, at the bottom: the song, the transport and its progress, the volume. */
@Composable
fun PlayerBar(state: MusicState) {
    val style = LocalMusicStyle.current
    val player = state.player
    val track = player.currentTrack
    val album = player.currentAlbum
    val accent = RavenTheme.colors.accent
    Surface(
        Modifier.fillMaxWidth().height(88.dp).let { m -> style.barMaterial?.let { m.material(it) } ?: m },
        color = style.bar,
    ) {
        Grid(listOf(GridTrack.Fill(1f), GridTrack.Fill(1.3f), GridTrack.Fill(1f)), emptyList(), Modifier.fillMaxSize().padding(horizontal = 16.dp), spacing = 16.dp) {
            Row(Modifier.cell(0, 0).align(Alignment.CenterVertically), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (album != null) Cover(album.cover, 58.dp, 6.dp)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(track?.title ?: "", Modifier.testTag("player.title"), fontSize = 15.sp)
                    Muted(album?.artist ?: "", size = 13f)
                }
                LikeButton(state, 18.dp, "Save to Liked songs (Ctrl+L)")
            }

            Column(Modifier.cell(1, 0).align(Alignment.CenterVertically), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(Glyph.Shuffle, "Shuffle", state::toggleShuffle, size = 18.dp, tint = if (player.shuffle) accent else Color.Unspecified)
                    IconButton(Glyph.Previous, "Previous (Ctrl+Left)", player::previous)
                    PlayButton(player.playing, state::toggle, Modifier.testTag("player.play"), 40.dp, 18.dp, white = true)
                    IconButton(Glyph.Next, "Next (Ctrl+Right)", player::next)
                    IconButton(
                        if (player.repeat == Repeat.One) Glyph.RepeatOne else Glyph.Repeat, "Repeat", state::cycleRepeat, size = 18.dp,
                        tint = if (player.repeat != Repeat.Off) accent else Color.Unspecified,
                    )
                }
                Seek(state, 12f, Modifier.fillMaxWidth())
            }

            Row(Modifier.cell(2, 0).align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Visualizer(player.playing, 5, 2f, Modifier.padding(end = 8.dp).size(22.dp, 16.dp))
                IconButton(Glyph.Microphone, "Lyrics", state::notHere, size = 18.dp)
                IconButton(Glyph.Panel, "Now playing panel", { state.panel = !state.panel }, size = 18.dp)
                IconButton(Glyph.Queue, "Queue", state::notHere, size = 18.dp)
                IconButton(if (player.muted) Glyph.Muted else Glyph.Volume, "Mute", state::mute, size = 18.dp)
                Slider(if (player.muted) 0f else player.volume, state::setVolume, Modifier.width(120.dp))
            }
        }
    }
}

/** The island at the top of the window: what plays, folded; a small player, unfolded. */
@Composable
fun MusicIsland(state: MusicState) {
    val library = state.library
    val player = state.player
    val track = player.currentTrack
    val album = player.currentAlbum
    Island(
        state.islandExpanded, { state.islandExpanded = it },
        attached = true,
        notch = true,
        folded = {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (album != null) Cover(album.cover, 20.dp, 5.dp)
                Text(state.notice ?: track?.title ?: "Raven Music", Modifier.testTag("island.title"), fontSize = 12.sp)
                Visualizer(player.playing, 4, 2f, Modifier.size(16.dp, 12.dp))
            }
        },
        unfolded = {
            Column(Modifier.width(380.dp).padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover((album ?: library.albums[0]).cover, 60.dp, 10.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Heading(track?.title ?: "", 16f)
                        Muted(album?.artist ?: "", size = 13f)
                    }
                    Visualizer(player.playing, 6, 2f, Modifier.size(30.dp, 24.dp))
                }
                Seek(state, 11f, Modifier.fillMaxWidth(), labelWidth = false)
                Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    LikeButton(state, 18.dp, "Save to Liked songs")
                    IconButton(Glyph.Previous, "Previous", player::previous, size = 22.dp)
                    PlayButton(player.playing, state::toggle, size = 44.dp, iconSize = 20.dp, white = true)
                    IconButton(Glyph.Next, "Next", player::next, size = 22.dp)
                    IconButton(Glyph.Panel, "Now playing panel", { state.panel = !state.panel }, size = 18.dp)
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Glyph.Moon, 16.dp, Modifier.alpha(0.8f))
                    Muted("Look")
                    ComboBox(MusicLooks.map { it.name }, state.lookIndex, { state.lookIndex = it }, Modifier.weight(1f))
                }
            }
        },
    )
}
