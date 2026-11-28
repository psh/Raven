package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.runBlocking
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rscroll_box_set_offset
import raven.cinterop.Rwidget_focus
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_is_focused
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.height
import raven.compose.foundation.layout.width
import raven.compose.foundation.lazy.LazyColumn
import raven.compose.foundation.lazy.LazyListState
import raven.compose.foundation.lazy.LazyRow
import raven.compose.foundation.lazy.items
import raven.compose.ui.Modifier
import raven.compose.ui.test.RavenTestScope
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.test.widgetRect
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** LazyColumn and LazyRow composing only the items in view. */
class LazyVirtualisationTest {
    private fun RavenTestScope.widget(tag: String): Rid = onNodeWithTag(tag).fetchWidget()

    private fun RavenTestScope.exists(tag: String): Boolean = onAllNodesWithTag(tag).fetchCount() > 0

    /** Where the node with [tag] is from the list's top, in dp. */
    private fun RavenTestScope.top(tag: String): Float = widgetRect(widget(tag)).y - widgetRect(widget("list")).y

    private fun LazyListState.position() = firstVisibleItemIndex to firstVisibleItemScrollOffset

    @Test
    fun tenThousandItemsComposeOnlyAScreenful() = runRavenTest {
        val state = LazyListState()
        setContent {
            LazyColumn(Modifier.height(100.dp).testTag("list"), state) {
                items((0 until 10_000).toList(), key = { it }) { Box(Modifier.height(20.dp).testTag("item$it")) {} }
            }
        }
        // Items 0 to 4 are in view, 5 one past the end, and a Spacer stands in for the rest.
        assertEquals((0..5).toList(), (0 until 10_000).filter { it < 50 && exists("item$it") })
        assertEquals(7, Rwidget_get_child_count(widget("list")))
        assertEquals(0 to 0, state.position())
    }

    @Test
    fun scrollingComposesTheItemsComingIntoViewAndDisposesTheOthers() = runRavenTest {
        val state = LazyListState()
        setContent {
            LazyColumn(Modifier.height(100.dp).testTag("list"), state) {
                items((0 until 10_000).toList(), key = { it }) { Box(Modifier.height(20.dp).testTag("item$it")) {} }
            }
        }
        // Into the Spacer, as dragging the scroll bar does.
        Rscroll_box_set_offset(widget("list"), 0f, 1_000f)
        frame()
        waitForIdle()
        assertEquals(50 to 0, state.position())
        assertTrue(exists("item49") && exists("item50") && exists("item55"), "the items there, and one past each edge")
        assertTrue(!exists("item0") && !exists("item48") && !exists("item56"))
        assertEquals(0f, top("item50"))

        onNodeWithTag("list").performScrollBy(dy = 30f)
        frame()
        waitForIdle()
        assertEquals(51 to 10, state.position())
        assertEquals(-10f, top("item51"))
        assertTrue(exists("item56") && !exists("item49"))
    }

    @Test
    fun scrollToItemFarDownPutsAnUnmeasuredItemAtTheTop() = runRavenTest {
        val state = LazyListState()
        setContent {
            LazyColumn(Modifier.height(100.dp).testTag("list"), state) {
                // Sizes the estimate doesn't know: 20, 30 or 40 dp.
                items((0 until 10_000).toList(), key = { it }) { Box(Modifier.height((20 + it % 3 * 10).dp).testTag("item$it")) {} }
            }
        }
        runBlocking { state.scrollToItem(9_000, 5) }
        waitForIdle()
        assertEquals(9_000 to 5, state.position())
        assertEquals(-5f, top("item9000"), "exactly at the top, however far the estimate was")

        onNodeWithTag("list").performScrollToIndex(3_000)
        assertEquals(3_000 to 0, state.position())
        assertEquals(0f, top("item3000"))

        runBlocking { state.scrollToItem(9_999) }
        waitForIdle()
        assertTrue(exists("item9999"), "the end, clamped")
        assertEquals(100f, top("item9999") + widgetRect(widget("item9999")).height)
    }

    @Test
    fun theFirstVisibleItemKeepsItsPlaceWhenTheSizesAboveItAreCorrected() = runRavenTest {
        val state = LazyListState()
        setContent {
            LazyColumn(Modifier.height(100.dp).testTag("list"), state) {
                // The items above 50 are taller than the estimate the ones below give.
                items((0 until 200).toList(), key = { it }) { Box(Modifier.height(if (it < 50) 70.dp else 20.dp).testTag("item$it")) {} }
            }
        }
        runBlocking { state.scrollToItem(50) }
        waitForIdle()
        assertEquals(0f, top("item50"))
        // Item 49 comes into view, measured at last: item 50 stays where the user scrolled it.
        onNodeWithTag("list").performScrollBy(dy = -10f)
        frame()
        waitForIdle()
        assertEquals(10f, top("item50"))
        assertEquals(49 to 60, state.position())
    }

    @Test
    fun theFirstVisibleItemKeepsItsPlaceWhenItemsComeAndGoAboveIt() = runRavenTest {
        var keys by mutableStateOf((0 until 1_000).toList())
        val state = LazyListState()
        setContent {
            LazyColumn(Modifier.height(100.dp).testTag("list"), state) {
                items(keys, key = { it }) { Box(Modifier.height(20.dp).testTag("item$it")) {} }
            }
        }
        runBlocking { state.scrollToItem(100, 7) }
        waitForIdle()
        val widget = widget("item100")
        keys = (-5 until 0).toList() + keys
        waitForIdle()
        assertEquals(105 to 7, state.position(), "five inserted above")
        assertEquals(-7f, top("item100"))
        assertEquals(widget, widget("item100"), "the same widget")

        keys = keys.filter { it !in 10 until 30 }
        waitForIdle()
        assertEquals(85 to 7, state.position(), "twenty removed above")
        assertEquals(-7f, top("item100"))

        // Moving the items in view below the first moves their widgets.
        val shown = (101..104).associateWith { widget("item$it") }
        keys = keys.map { if (it in 101..104) 205 - it else it }
        waitForIdle()
        assertEquals(shown, (101..104).associateWith { widget("item$it") })
        assertEquals(13f, top("item104"), "where 101 was")
        assertEquals(85 to 7, state.position())
    }

    @Test
    fun rememberSaveableInAnItemComesBackWithItAndRememberDoesNot() = runRavenTest {
        var made = 0
        val saved = HashMap<Int, Int>()
        val remembered = HashMap<Int, Int>()
        val state = LazyListState()
        setContent {
            LazyColumn(Modifier.height(100.dp).testTag("list"), state) {
                items((0 until 1_000).toList(), key = { it }) {
                    saved[it] = rememberSaveable { made++ }
                    remembered[it] = remember { made++ }
                    Box(Modifier.height(20.dp)) {}
                }
            }
        }
        val first = saved.getValue(0) to remembered.getValue(0)
        runBlocking { state.scrollToItem(500) }
        waitForIdle()
        runBlocking { state.scrollToItem(0) }
        waitForIdle()
        assertEquals(first.first, saved.getValue(0), "saveable state, kept by key")
        assertNotEquals(first.second, remembered.getValue(0), "remember, lost with the item")
    }

    @Test
    fun theFocusedItemStaysComposedWhileScrolledAway() = runRavenTest {
        val state = LazyListState()
        setContent {
            LazyColumn(Modifier.height(100.dp).testTag("list"), state) {
                items((0 until 1_000).toList(), key = { it }) {
                    Button({}, Modifier.height(30.dp).testTag("item$it")) { Text("Item $it") }
                }
            }
        }
        val button = widget("item1")
        Rwidget_focus(button)
        frame()
        runBlocking { state.scrollToItem(500) }
        waitForIdle()
        assertEquals(button, widget("item1"), "pinned")
        assertEquals(R_TRUE, Rwidget_is_focused(button))
        assertTrue(!exists("item2") && exists("item500"))

        Rwidget_focus(widget("item500"))
        frame()
        waitForIdle()
        assertTrue(!exists("item1"), "let go once the focus moved")
    }

    @Test
    fun aLazyRowComposesOnlyTheItemsInView() = runRavenTest {
        val state = LazyListState()
        setContent {
            LazyRow(Modifier.width(100.dp).testTag("list"), state) {
                items(10_000) { Box(Modifier.width(25.dp).height(10.dp).testTag("item$it")) {} }
            }
        }
        assertEquals((0..4).toList(), (0 until 20).filter { exists("item$it") })
        runBlocking { state.scrollToItem(7_000, 3) }
        waitForIdle()
        assertEquals(7_000 to 3, state.position())
        assertEquals(-3f, widgetRect(widget("item7000")).x - widgetRect(widget("list")).x)
        assertEquals((6_999..7_005).toList(), (6_990 until 7_010).filter { exists("item$it") })
    }
}
