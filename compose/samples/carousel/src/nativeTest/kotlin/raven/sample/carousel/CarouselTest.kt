@file:OptIn(InternalComposeRavenApi::class)

package raven.sample.carousel

import raven.cinterop.R_FALSE
import raven.cinterop.R_HORIZONTAL
import raven.cinterop.R_TRUE
import raven.cinterop.R_VERTICAL
import raven.cinterop.Rcarousel_get_autoplay
import raven.cinterop.Rcarousel_get_loop
import raven.cinterop.Rcarousel_get_orientation
import raven.cinterop.Rcarousel_get_page
import raven.cinterop.Rcarousel_get_page_count
import raven.cinterop.Rwidget_is_enabled
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.test.RavenTestScope
import raven.compose.ui.test.runRavenTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CarouselTest {
    private fun RavenTestScope.pages() = onNodeWithTag("pages").fetchWidget()

    private fun RavenTestScope.status(text: String) = onNodeWithText(text).assertExists()

    private fun RavenTestScope.backEnabled() = Rwidget_is_enabled(onNodeWithTag("back").fetchWidget())

    @Test
    fun fivePagesAndTheStatusOfThePageShown() = runRavenTest {
        setContent { CarouselSample() }
        assertEquals(5, Rcarousel_get_page_count(pages()))
        status("Welcome  -  1 / 5")
        assertEquals(R_FALSE, backEnabled(), "nothing before the first page")
        onNodeWithText("Welcome to Raven").assertExists()
        onNodeWithText("A button on a page").assertExists()
    }

    @Test
    fun backAndNextTurnThePagesAndNextStartsAgainAfterTheLast() = runRavenTest {
        setContent { CarouselSample() }
        onNodeWithText("Next").performClick()
        status("Swipe  -  2 / 5")
        assertEquals(1, Rcarousel_get_page(pages()))
        assertEquals(R_TRUE, backEnabled())
        onNodeWithText("Back").performClick()
        status("Welcome  -  1 / 5")
        repeat(4) { onNodeWithTag("next").performClick() }
        status("Ready  -  5 / 5")
        onNodeWithText("Get started").performClick()
        status("Welcome  -  1 / 5")
        assertEquals(0, Rcarousel_get_page(pages()))
    }

    @Test
    fun theSwitchesLoopAutoplayAndTurnThePagesUpright() = runRavenTest {
        setContent { CarouselSample() }
        onNodeWithTag("loop").performClick()
        assertEquals(R_TRUE, Rcarousel_get_loop(pages()))
        assertEquals(R_TRUE, backEnabled(), "with loop, the last page comes before the first")
        onNodeWithText("Back").performClick()
        status("Ready  -  5 / 5")
        onNodeWithText("Next").assertExists()
        onNodeWithText("Next").performClick()
        status("Welcome  -  1 / 5")

        onNodeWithTag("autoplay").performClick()
        assertEquals(3.0, Rcarousel_get_autoplay(pages()))
        onNodeWithTag("autoplay").performClick()
        assertEquals(0.0, Rcarousel_get_autoplay(pages()))

        onNodeWithText("Next").performClick()
        assertEquals(R_HORIZONTAL, Rcarousel_get_orientation(pages()))
        onNodeWithTag("vertical").performClick()
        assertEquals(R_VERTICAL, Rcarousel_get_orientation(pages()))
        assertEquals(1, Rcarousel_get_page(pages()), "the page kept across the orientation")
        status("Swipe  -  2 / 5")
    }
}
