package raven.compose.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.compose.animation.core.RavenAnimation
import raven.compose.animation.core.tween
import raven.compose.foundation.Text
import raven.compose.foundation.runRavenTest
import raven.compose.ui.AppearTransition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** AnimatedVisibility: Raven's transitions in, a fade out. */
class AnimatedVisibilityTest {
    @Test
    fun enterTransitionsMapToTheNearestRavenTransition() {
        assertEquals(AppearTransition.Fade, fadeIn().appearTransition)
        assertEquals(AppearTransition.Pop, scaleIn().appearTransition)
        assertEquals(AppearTransition.Zoom, scaleIn(initialScale = 1.2f).appearTransition)
        assertEquals(AppearTransition.FlyDown, slideInVertically().appearTransition)
        assertEquals(AppearTransition.FlyUp, slideInVertically { it }.appearTransition)
        assertEquals(AppearTransition.FlyRight, slideInHorizontally().appearTransition)
        assertEquals(AppearTransition.FlyLeft, slideInHorizontally { it / 2 }.appearTransition)
        assertEquals(AppearTransition.FlyUp, (fadeIn() + slideInVertically { it }).appearTransition, "a slide wins")
        assertEquals(AppearTransition.Pop, (scaleIn() + fadeIn()).appearTransition, "a scale wins over a fade")
        assertNull(EnterTransition.None.appearTransition)
    }

    @Test
    fun exitTransitionsFallBackToAFade() {
        val fade = fadeOut(tween(100), targetAlpha = 0.2f)
        assertEquals(ExitTransition.Fade(tween(100), 0.2f), fade.fade)
        assertEquals(fadeOut().fade, scaleOut().fade)
        assertEquals(fadeOut().fade, slideOutVertically().fade)
        assertEquals(fade.fade, (slideOutHorizontally() + fade).fade, "a given fade wins")
        assertNull(ExitTransition.None.fade)
    }

    @Test
    fun theContentFadesOutBeforeItIsRemoved() = runRavenTest {
        var visible by mutableStateOf(true)
        setContent {
            AnimatedVisibility(visible, exit = fadeOut(tween(200))) { Text("shown") }
        }
        onNodeWithText("shown").assertExists()
        // Waiting for idle would run the fade, which recomposes every frame, to its end.
        mainClock.autoAdvance = false

        visible = false
        mainClock.advanceTimeBy(100)
        onNodeWithText("shown").assertExists()
        mainClock.advanceTimeBy(200)
        onNodeWithText("shown").assertDoesNotExist()

        visible = true
        onNodeWithText("shown").assertExists()
    }

    @Test
    fun comingBackWhileFadingOutKeepsTheContent() = runRavenTest {
        var visible by mutableStateOf(true)
        setContent {
            AnimatedVisibility(visible, exit = fadeOut(tween(200))) { Text("shown") }
        }
        mainClock.autoAdvance = false
        visible = false
        mainClock.advanceTimeBy(100)
        onNodeWithText("shown").assertExists()
        visible = true
        mainClock.advanceTimeBy(500)
        onNodeWithText("shown").assertExists()
    }

    @Test
    fun noExitOrDisabledAnimationsRemoveItAtOnce() = runRavenTest {
        var visible by mutableStateOf(true)
        var exit by mutableStateOf(ExitTransition.None)
        setContent {
            AnimatedVisibility(visible, enter = slideInVertically(), exit = exit) { Text("shown") }
        }
        visible = false
        onNodeWithText("shown").assertDoesNotExist()

        exit = fadeOut(tween(1000))
        visible = true
        onNodeWithText("shown").assertExists()
        RavenAnimation.enabled = false
        visible = false
        onNodeWithText("shown").assertDoesNotExist()
    }
}
