package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import kotlinx.coroutines.runBlocking
import raven.cinterop.R_FALSE
import raven.cinterop.R_HORIZONTAL
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_TRUE
import raven.cinterop.R_VERTICAL
import raven.cinterop.Rcarousel_get_arrows_visible
import raven.cinterop.Rcarousel_get_autoplay
import raven.cinterop.Rcarousel_get_indicators_visible
import raven.cinterop.Rcarousel_get_loop
import raven.cinterop.Rcarousel_get_orientation
import raven.cinterop.Rcarousel_get_page
import raven.cinterop.Rcarousel_get_page_count
import raven.cinterop.Rid
import raven.cinterop.Rwidget_focus
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.compose.foundation.layout.size
import raven.compose.foundation.pager.HorizontalPager
import raven.compose.foundation.pager.PagerState
import raven.compose.foundation.pager.VerticalPager
import raven.compose.foundation.pager.rememberPagerState
import raven.compose.ui.Modifier
import raven.compose.ui.input.key.Key
import raven.compose.ui.test.RavenTestScope
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.test.widgetRect
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** HorizontalPager and VerticalPager over Raven's Carousel, and PagerState. */
class PagerTest {
    private fun RavenTestScope.pager(): Rid = onNodeWithTag("pager").fetchWidget()

    @Test
    fun everyPageIsComposedAndTheStatesMovesAreClamped() = runRavenTest {
        val state = PagerState { 3 }
        setContent {
            HorizontalPager(state, Modifier.size(400.dp, 300.dp).testTag("pager"), loop = true) { Text("page $it", Modifier.testTag("page$it")) }
        }
        val pager = pager()
        assertEquals(3, Rcarousel_get_page_count(pager))
        (0..2).forEach { onNodeWithTag("page$it").assertExists() }
        assertEquals(R_HORIZONTAL, Rcarousel_get_orientation(pager))

        runBlocking { state.scrollToPage(1) }
        assertEquals(1 to 1, state.currentPage to Rcarousel_get_page(pager))
        runBlocking { state.animateScrollToPage(9) }
        assertEquals(2 to 2, state.currentPage to Rcarousel_get_page(pager), "clamped, not wrapped with loop")
        runBlocking { state.scrollToPage(-4) }
        assertEquals(0, state.currentPage)
    }

    @Test
    fun theUsersKeysAndADotMoveTheCurrentPage() = runRavenTest {
        var looping by mutableStateOf(false)
        val state = PagerState { 3 }
        setContent {
            HorizontalPager(state, Modifier.size(400.dp, 300.dp).testTag("pager"), loop = looping) { Text("page $it") }
        }
        val pager = pager()
        Rwidget_focus(pager)
        pressKey(Key.DirectionRight)
        waitForIdle()
        assertEquals(1, state.currentPage, "Right")
        pressKey(Key.MoveEnd)
        waitForIdle()
        assertEquals(2, state.currentPage, "End")
        pressKey(Key.DirectionRight)
        waitForIdle()
        assertEquals(2, state.currentPage, "the last page, without loop")
        looping = true
        waitForIdle()
        assertEquals(R_TRUE, Rcarousel_get_loop(pager))
        pressKey(Key.DirectionRight)
        waitForIdle()
        assertEquals(0, state.currentPage, "with loop, the first after the last")

        // The dots: a capsule centred along the bottom, 18 dp apart, the page shown a 20 dp pill.
        val rect = widgetRect(pager)
        val window = Rwidget_get_window(pager)
        val left = ((400f - (18f * 2 + 20f + 14f)) / 2f).toInt().toFloat()
        Rwindow_inject_mouse_move(window, rect.x + left + 7f + 10f + 2 * 18f, rect.y + 300f - 12f - 11f)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
        waitForIdle()
        assertEquals(2, state.currentPage, "the third dot")
    }

    @Test
    fun aChangeOfPageCountAddsAndRemovesPages() = runRavenTest {
        var count by mutableStateOf(2)
        lateinit var state: PagerState
        setContent {
            state = rememberPagerState(initialPage = 3) { count }
            HorizontalPager(state, Modifier.size(400.dp, 300.dp).testTag("pager")) { Text("page $it", Modifier.testTag("page$it")) }
        }
        val pager = pager()
        assertEquals(2, Rcarousel_get_page_count(pager))
        assertEquals(1 to 1, state.currentPage to Rcarousel_get_page(pager), "the initial page, clamped to the pages")
        count = 5
        waitForIdle()
        assertEquals(5, Rcarousel_get_page_count(pager))
        onNodeWithTag("page4").assertExists()
        runBlocking { state.scrollToPage(3) }
        assertEquals(3 to 3, state.currentPage to Rcarousel_get_page(pager))
        count = 2
        waitForIdle()
        assertEquals(2, Rcarousel_get_page_count(pager))
        assertEquals(1, state.currentPage, "Raven clamps the page shown, and the state reads it")
        onNodeWithTag("page4").assertDoesNotExist()
    }

    @Test
    fun theAutoplayAdvances() = runRavenTest {
        val state = PagerState { 3 }
        setContent {
            VerticalPager(state, Modifier.size(300.dp, 400.dp).testTag("pager"), autoplay = 50.milliseconds, indicators = false, arrows = false) {
                Text("page $it")
            }
        }
        val pager = pager()
        assertEquals(R_VERTICAL, Rcarousel_get_orientation(pager))
        assertEquals(0.05, Rcarousel_get_autoplay(pager), 1e-6)
        assertEquals(R_FALSE, Rcarousel_get_indicators_visible(pager))
        assertEquals(R_FALSE, Rcarousel_get_arrows_visible(pager))
        val start = TimeSource.Monotonic.markNow()
        while (state.currentPage == 0 && start.elapsedNow() < 5.seconds) frame()
        assertTrue(state.currentPage > 0, "the timer went to the next page")
    }

    @Test
    fun thePageIsSaved() = runRavenTest {
        var shown by mutableStateOf(true)
        var state: PagerState? = null
        setContent {
            val holder = rememberSaveableStateHolder()
            if (shown) {
                holder.SaveableStateProvider("page") {
                    val pages = rememberPagerState { 4 }.also { state = it }
                    HorizontalPager(pages, Modifier.size(400.dp, 300.dp).testTag("pager")) { Text("page $it") }
                }
            }
        }
        runBlocking { state!!.scrollToPage(2) }
        shown = false
        waitForIdle()
        shown = true
        waitForIdle()
        assertEquals(2, state!!.currentPage)
        assertEquals(2, Rcarousel_get_page(pager()), "restored, and shown")
        assertEquals(4, state!!.pageCount)
    }
}
