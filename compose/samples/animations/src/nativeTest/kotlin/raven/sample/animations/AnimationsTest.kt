@file:OptIn(InternalComposeRavenApi::class)

package raven.sample.animations

import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.cinterop.R_FALSE
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_TRUE
import raven.compose.foundation.RavenTestScope
import raven.compose.foundation.runRavenTest
import raven.compose.foundation.widgetRect
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.input.key.Key
import raven.cinterop.Rwidget_focus
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The animations sample, headless, on the test's clock. */
class AnimationsTest {
    private lateinit var state: Animations

    private fun RavenTestScope.show() {
        state = Animations()
        host.start { AnimationsWindow(onCloseRequest = {}, state = state) }
        waitForIdle()
    }

    @Test
    fun aTransitionIsPlayedOnTheCard() = runRavenTest {
        show()
        assertEquals("Pop", state.cardTitle)
        onNodeWithTag("play.Drop").performClick()
        waitForIdle()
        assertEquals("Drop", state.cardTitle)
        onNodeWithTag("play.Fade").performClick()
        onNodeWithTag("play.all").performClick()
        mainClock.advanceTimeBy(500)
        assertEquals("Drop", state.cardTitle, "the stage card drops for Play them all")
    }

    @Test
    fun cardsComeAndGoAndTheIslandSaysSo() = runRavenTest {
        show()
        onNodeWithText("3 cards").assertExists()
        onNodeWithTag("cards.add").performClick()
        waitForIdle()
        onNodeWithText("Card 4").assertExists()
        onNodeWithText("4 cards").assertExists()
        assertTrue(state.islandExpanded, "unfolded to tell it")

        onNodeWithTag("cards.remove").performClick()
        onNodeWithTag("cards.remove").performClick()
        onNodeWithTag("cards.remove").performClick()
        waitForIdle()
        onNodeWithText("Card 2").assertDoesNotExist()
        onNodeWithText("1 card").assertExists()
    }

    @Test
    fun theSpringsFollowThePointerAndSettle() = runRavenTest {
        show()
        mainClock.advanceTimeBy(3000)
        val springs = state.springs!!
        fun settledAt(target: Float) = springs.positions.all { abs(it.value - target * springs.track) < 0.5f }
        assertTrue(springs.track > 0f)
        assertTrue(settledAt(0.5f), "in the middle at first: ${springs.positions.map { it.value }}")

        // The pointer near the right end of the tracks.
        val canvas = onNodeWithTag("springs").fetchWidget()
        val rect = widgetRect(canvas)
        val window = Rwidget_get_window(canvas)
        Rwindow_inject_mouse_move(window, rect.x + rect.width - 20f, rect.y + rect.height / 2)
        mainClock.advanceTimeBy(50)
        val moving = springs.positions.map { it.value }
        assertTrue(moving.distinct().size > 1, "each in its own way: $moving")
        mainClock.advanceTimeBy(3000)
        assertTrue(settledAt(springs.target), "settled at ${springs.target}: ${springs.positions.map { it.value }}")

        // A click kicks them to the right, past where they rest, and they come back.
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
        mainClock.advanceTimeBy(80)
        assertTrue(springs.positions.any { it.value > springs.target * springs.track + 1f }, "kicked: ${springs.positions.map { it.value }}")
        mainClock.advanceTimeBy(4000)
        assertTrue(settledAt(springs.target))
    }

    @Test
    fun speedAndReduceMotionTuneEveryAnimation() = runRavenTest {
        show()
        state.islandExpanded = true
        // Unfolded, once Raven's island has finished unfolding.
        mainClock.advanceTimeBy(1500)
        // The slider moves with the keyboard, as the user would move it.
        Rwidget_focus(onNodeWithTag("speed").fetchWidget())
        pressKey(Key.DirectionLeft)
        waitForIdle()
        assertTrue(raven.compose.animation.core.RavenAnimation.speed < 1f, "slower: ${raven.compose.animation.core.RavenAnimation.speed}")
        onNodeWithTag("reduce").performClick()
        waitForIdle()
        assertEquals(false, raven.compose.animation.core.RavenAnimation.enabled)
    }
}
