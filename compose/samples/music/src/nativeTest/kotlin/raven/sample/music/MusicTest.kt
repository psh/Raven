@file:OptIn(InternalComposeRavenApi::class)

package raven.sample.music

import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import raven.cinterop.R_COLOR_ACCENT
import raven.cinterop.R_COLOR_WINDOW
import raven.cinterop.Rlabel_get_text
import raven.cinterop.Rtheme_get_color
import raven.compose.foundation.RavenTestScope
import raven.compose.foundation.runRavenTest
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.ravenColor
import raven.compose.ui.input.key.Key
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The music sample, headless: its window as the C example opens, the song paused. */
class MusicTest {
    private lateinit var state: MusicState

    private val player get() = state.player

    private fun RavenTestScope.show() {
        // The player's clock and the visualizers move every frame while a song plays: waiting for
        // idle holds them.
        mainClock.autoAdvance = false
        host.start {
            // The themes, the covers and the icons need Raven running.
            val themes = remember { MusicThemes(EXAMPLE_DIR) }
            val icons = remember { Icons() }
            state = remember { MusicState.opening(Library.create(EXAMPLE_DIR), playing = false, random = Random(7)) }
            MusicApp(state, themes, icons, onQuit = {})
        }
        waitForIdle()
    }

    private fun title(position: Int) = state.library.tracks[player.queue[position]].title

    /** The text of the label tagged [tag]. */
    @OptIn(ExperimentalForeignApi::class)
    private fun RavenTestScope.text(tag: String): String = Rlabel_get_text(onNodeWithTag(tag).fetchWidget())?.toKString().orEmpty()

    private fun color(role: UInt) = ravenColor { r, g, b, a -> Rtheme_get_color(role, r, g, b, a) }

    @Test
    fun itPlaysPausesAndGoesOn() = runRavenTest {
        show()
        assertEquals(2, player.position, "the third song of the fourth album")
        assertEquals(title(2), text("player.title"))
        // Space, while nothing has the focus.
        pressKey(Key.Spacebar)
        waitForIdle()
        assertTrue(player.playing)
        mainClock.advanceTimeBy(2000)
        assertTrue(player.seconds >= 48.9, "playing: ${player.seconds}")

        onNodeWithContentDescription("Next (Ctrl+Right)").performClick()
        waitForIdle()
        assertEquals(3, player.position)
        assertEquals(0.0, player.seconds)
        assertEquals(title(3), text("player.title"))
        assertEquals(title(3), text("np.title"))

        // Back to the song before (at its start, so not to its start), then pause.
        pressKey(Key.DirectionLeft, ctrl = true)
        waitForIdle()
        assertEquals(2, player.position)
        onNodeWithTag("player.play").performClick()
        waitForIdle()
        assertEquals(false, player.playing)
    }

    @Test
    fun aPageVisitedAgainComesToTheFrontAndBackGoesBack() = runRavenTest {
        show()
        onNodeWithContentDescription("Back (Alt+Left)").fetchNode()
        assertEquals(listOf<Any>(Home), state.backStack.toList(), "nowhere to go back to")

        onNodeWithContentDescription("Your library").performClick()
        waitForIdle()
        onNodeWithText("$PLAYLISTS playlists", substring = true).assertExists()
        onNodeWithTag("sidebar.album.7").performScrollTo().performClick()
        waitForIdle()
        assertEquals(state.library.albums[7].title, text("collection.title"))

        // The library again: brought to the front, not repeated.
        onNodeWithContentDescription("Your library").performClick()
        waitForIdle()
        assertEquals(listOf(Home, AlbumPage(7), LibraryPage), state.backStack.toList())

        // Back, with the keys, then the button: the album, then Home.
        pressKey(Key.DirectionLeft, alt = true)
        waitForIdle()
        assertEquals(AlbumPage(7), state.page)
        assertEquals(state.library.albums[7].title, text("collection.title"))
        onNodeWithContentDescription("Back (Alt+Left)").performClick()
        waitForIdle()
        assertEquals(listOf<Any>(Home), state.backStack.toList())

        // A playlist's page plays the playlist.
        onNodeWithTag("sidebar.playlist.1").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("collection.play").performClick()
        waitForIdle()
        assertTrue(player.playing)
        assertEquals(state.library.playlist(1).tracks, player.queue)
        assertEquals(state.library.playlist(1).title, player.context)
    }

    @Test
    fun searchFindsAlbumsAndSongs() = runRavenTest {
        show()
        pressKey(Key.F, ctrl = true)
        waitForIdle()
        assertEquals(SearchPage, state.page)
        onNodeWithTag("search").assertIsFocused()
        onNodeWithText("Browse all").assertExists()

        onNodeWithTag("search").performTextInput("zzqx")
        waitForIdle()
        onNodeWithTag("search.none").assertExists()

        // A genre's tile searches for it; the top result is the first album of it.
        state.query = ""
        waitForIdle()
        val genre = state.library.albums[0].genre
        onNodeWithContentDescription(genre).performClick()
        waitForIdle()
        assertEquals(genre, state.query)
        onNodeWithTag("search.none").assertDoesNotExist()
        onNodeWithTag("search.top").performClick()
        waitForIdle()
        assertEquals(AlbumPage(state.library.albums.first { it.genre == genre }.index), state.page)
    }

    @Test
    fun theLookAndTheAccentChange() = runRavenTest {
        show()
        val accent = color(R_COLOR_ACCENT)
        val nocturne = color(R_COLOR_WINDOW)
        pressKey(Key.P, ctrl = true)
        waitForIdle()
        assertEquals(SettingsPage, state.page)

        // Daylight: a light window, and the accent a darker shade of the cover.
        onNodeWithTag("settings.look").performSelection(2)
        waitForIdle()
        assertEquals("Daylight", state.look.name)
        val daylight = color(R_COLOR_WINDOW)
        assertNotEquals(nocturne, daylight)
        assertTrue(daylight.red > 0.8f, "light: $daylight")
        assertNotEquals(accent, color(R_COLOR_ACCENT))

        // A look of Raven, the player's classes over it.
        onNodeWithTag("settings.look").performSelection(MusicLooks.indexOfFirst { it.name == "GNOME dark" })
        waitForIdle()
        assertNotEquals(daylight, color(R_COLOR_WINDOW))

        // An accent picked stops following the cover.
        onNodeWithTag("settings.accent").performColorChange(Color(0xFF22AA44))
        waitForIdle()
        assertEquals(false, state.followCover)
        assertEquals(Color(0xFF22AA44), color(R_COLOR_ACCENT))
    }

    @Test
    fun likingASongPutsItInLikedSongsAndTheIslandSaysSo() = runRavenTest {
        show()
        val track = player.currentTrack!!
        val wasLiked = track.liked
        val liked = state.library.playlist(0).tracks.size
        pressKey(Key.L, ctrl = true)
        waitForIdle()
        assertEquals(!wasLiked, track.liked)
        assertEquals(liked + if (track.liked) 1 else -1, state.library.playlist(0).tracks.size)
        assertEquals(if (track.liked) "Added to Liked songs" else "Removed from Liked songs", text("island.title"))

        // The notice lasts a moment, then the island shows the song again.
        mainClock.advanceTimeBy(3000)
        assertEquals(track.title, text("island.title"))
    }
}
