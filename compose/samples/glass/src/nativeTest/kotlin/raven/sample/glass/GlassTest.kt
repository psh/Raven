@file:OptIn(InternalComposeRavenApi::class)

package raven.sample.glass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.R_COLOR_WINDOW
import raven.cinterop.Rtheme_get_color
import raven.cinterop.Rwidget_focus
import raven.compose.foundation.RavenTestScope
import raven.compose.foundation.runRavenTest
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.Window
import raven.compose.ui.graphics.ravenColor
import raven.compose.ui.input.key.Key
import raven.compose.ui.theme.Look
import raven.compose.ui.theme.RavenTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The glass sample, headless. */
class GlassTest {
    private var dark by mutableStateOf(true)
    private var quits = 0
    private val player = Player()

    private fun RavenTestScope.show() {
        // The player's time moves every frame while it plays: waiting for idle holds it.
        mainClock.autoAdvance = false
        host.start {
            RavenTheme(look = Look.Glass, dark = dark) {
                Window(onCloseRequest = {}, title = "Glass") { GlassScreen(dark, { dark = it }, onQuit = { quits++ }, player = player) }
            }
        }
        waitForIdle()
    }

    private fun windowColor() = ravenColor { r, g, b, a -> Rtheme_get_color(R_COLOR_WINDOW, r, g, b, a) }

    @Test
    fun theSwitchTurnsTheGlassLight() = runRavenTest {
        show()
        val darkWindow = windowColor()
        onNodeWithTag("dark").performClick()
        waitForIdle()
        assertEquals(false, dark)
        assertNotEquals(darkWindow, windowColor(), "the light glass")
    }

    @Test
    fun theMoreMenuOpensAndQuits() = runRavenTest {
        show()
        onNodeWithTag("more").performClick()
        assertEquals(1, openMenus().size)
        clickOpenMenuItem("Lossless audio")
        waitForIdle()
        onNodeWithTag("more").performClick()
        clickOpenMenuItem("Quit")
        assertEquals(1, quits)
        pressKey(Key.Q, ctrl = true)
        waitForIdle()
        assertEquals(2, quits, "its shortcut")
    }

    @Test
    fun theTimeMovesWhilePlayingAndTheSliderSeeks() = runRavenTest {
        show()
        val start = player.seconds
        mainClock.advanceTimeBy(2000)
        assertTrue(player.seconds >= start + 1.9f, "playing: ${player.seconds}")

        onNodeWithTag("play").performClick()
        waitForIdle()
        assertEquals(false, player.playing)
        val paused = player.seconds
        mainClock.advanceTimeBy(1000)
        assertEquals(paused, player.seconds, "paused, the time stays")

        // The slider moves with the keyboard: the time goes where it was left.
        Rwidget_focus(onNodeWithTag("position").fetchWidget())
        pressKey(Key.MoveEnd)
        waitForIdle()
        assertEquals(SONG_SECONDS, player.seconds)
        onNodeWithText("4:00").assertExists()
    }
}
