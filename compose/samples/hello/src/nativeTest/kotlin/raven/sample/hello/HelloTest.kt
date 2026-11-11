package raven.sample.hello

import raven.cinterop.Rwidget_get_tooltip
import raven.compose.foundation.runRavenTest
import raven.compose.foundation.widgetRect
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelloTest {
    @Test
    fun theCounterCountsClicks() = runRavenTest {
        setContent { Hello(onQuit = {}) }
        onNodeWithText("Press the button").assertExists()
        onNodeWithText("Click me").performClick()
        onNodeWithText("Clicked 1 time").assertExists()
        onNodeWithText("Click me").performClick()
        onNodeWithText("Clicked 2 times").assertExists()
        onNodeWithText("Press the button").assertDoesNotExist()
    }

    @Test
    fun quitCallsOnQuit() = runRavenTest {
        var quits = 0
        setContent { Hello(onQuit = { quits++ }) }
        onNodeWithText("Quit").performClick()
        assertEquals(1, quits)
    }

    @Test
    fun itLooksLikeTheCExample() = runRavenTest {
        setContent { Hello(onQuit = {}) }
        // Raven has no getter for a label's text size, so the 24 sp title is checked by its height.
        val title = widgetRect(onNodeWithText("Hello from Raven").fetchWidget())
        val counter = widgetRect(onNodeWithText("Press the button").fetchWidget())
        assertTrue(title.height > counter.height * 1.4f, "the title is larger: ${title.height} against ${counter.height}")
        assertEquals(title.x + title.width / 2, counter.x + counter.width / 2, 0.5f, "both are centred")
        assertEquals("Counts the clicks", Rwidget_get_tooltip(onNodeWithContentDescription("Click me").fetchWidget()).toRavenString())
    }
}
