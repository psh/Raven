package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.runBlocking
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.HorizontalScroll
import raven.compose.foundation.layout.VerticalScroll
import raven.compose.foundation.layout.height
import raven.compose.foundation.layout.size
import raven.compose.foundation.layout.width
import raven.compose.foundation.lazy.LazyColumn
import raven.compose.foundation.lazy.LazyListState
import raven.compose.foundation.relocation.BringIntoViewRequester
import raven.compose.foundation.relocation.bringIntoViewRequester
import raven.compose.ui.Modifier
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** BringIntoViewRequester. */
class BringIntoViewTest {
    @Test
    fun aWidgetBelowTheViewIsScrolledJustIntoIt() = runRavenTest {
        val state = ScrollState(0)
        val requester = BringIntoViewRequester()
        setContent {
            VerticalScroll(Modifier.height(100.dp), state) {
                repeat(20) {
                    val modifier = if (it == 10) Modifier.bringIntoViewRequester(requester) else Modifier
                    Box(modifier.size(50.dp, 30.dp)) {}
                }
            }
        }
        runBlocking { requester.bringIntoView() }
        frame()
        assertEquals(11 * 30 - 100, state.value, "its bottom at the view's bottom")
    }

    @Test
    fun aWidgetAboveTheViewIsScrolledBackToTheTop() = runRavenTest {
        val state = ScrollState(0)
        val requester = BringIntoViewRequester()
        setContent {
            VerticalScroll(Modifier.height(100.dp), state) {
                repeat(20) {
                    val modifier = if (it == 2) Modifier.bringIntoViewRequester(requester) else Modifier
                    Box(modifier.size(50.dp, 30.dp)) {}
                }
            }
        }
        runBlocking { state.scrollToEnd() }
        runBlocking { requester.bringIntoView() }
        frame()
        assertEquals(60, state.value, "its top at the view's top")
    }

    @Test
    fun aWidgetInViewDoesntScroll() = runRavenTest {
        val state = ScrollState(0)
        val requester = BringIntoViewRequester()
        setContent {
            VerticalScroll(Modifier.height(100.dp), state) {
                repeat(20) {
                    val modifier = if (it == 1) Modifier.bringIntoViewRequester(requester) else Modifier
                    Box(modifier.size(50.dp, 30.dp)) {}
                }
            }
        }
        runBlocking { requester.bringIntoView() }
        frame()
        assertEquals(0, state.value)
    }

    @Test
    fun nestedScrollsEachScrollOnTheirOwnAxis() = runRavenTest {
        val outer = ScrollState(0)
        val inner = ScrollState(0)
        val requester = BringIntoViewRequester()
        setContent {
            // Wide enough for the row and the column's scroll bar, so the row keeps its 100.
            VerticalScroll(Modifier.size(200.dp, 100.dp), outer) {
                repeat(5) { Box(Modifier.size(50.dp, 30.dp)) {} }
                HorizontalScroll(Modifier.width(100.dp), inner) {
                    repeat(10) {
                        val modifier = if (it == 7) Modifier.bringIntoViewRequester(requester) else Modifier
                        Box(modifier.size(40.dp, 30.dp)) {}
                    }
                }
            }
        }
        runBlocking { requester.bringIntoView() }
        frame()
        assertEquals(8 * 40 - 100, inner.value, "the row scrolled across")
        assertEquals(6 * 30 - 100, outer.value, "the column scrolled down to the row")
    }

    @Test
    fun anItemOfALazyColumnIsScrolledIntoView() = runRavenTest {
        val state = LazyListState()
        val requester = BringIntoViewRequester()
        setContent {
            LazyColumn(Modifier.height(100.dp), state) {
                // Four items are in view; the fifth is composed too, one past the edge.
                items(30) {
                    val modifier = if (it == 4) Modifier.bringIntoViewRequester(requester) else Modifier
                    Text("Item $it", modifier.height(25.dp))
                }
            }
        }
        runBlocking { requester.bringIntoView() }
        frame()
        assertEquals(1, state.firstVisibleItemIndex, "item 4 is the last of the four shown")
    }

    @Test
    fun aRequesterAttachedToNothingDoesNothing() = runRavenTest {
        var shown by mutableStateOf(true)
        val requester = BringIntoViewRequester()
        runBlocking { requester.bringIntoView() }
        setContent {
            VerticalScroll(Modifier.height(100.dp).testTag("scroll")) {
                if (shown) Box(Modifier.bringIntoViewRequester(requester).size(50.dp, 30.dp)) {}
            }
        }
        shown = false
        waitForIdle()
        runBlocking { requester.bringIntoView() }
        onNodeWithTag("scroll").fetchWidget()
    }
}
