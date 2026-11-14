package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import kotlinx.coroutines.runBlocking
import raven.cinterop.Rscroll_box_set_offset
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.HorizontalScroll
import raven.compose.foundation.layout.VerticalScroll
import raven.compose.foundation.layout.height
import raven.compose.foundation.layout.size
import raven.compose.foundation.layout.width
import raven.compose.foundation.lazy.LazyColumn
import raven.compose.foundation.lazy.LazyListState
import raven.compose.foundation.lazy.LazyRow
import raven.compose.foundation.lazy.items
import raven.compose.foundation.lazy.rememberLazyListState
import raven.compose.ui.Modifier
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** ScrollState, LazyListState and ListViewState, and the scroll actions. */
class ScrollStateTest {
    private val defaultSink = layoutWarningSink

    @AfterTest
    fun restore() {
        layoutWarningSink = defaultSink
    }

    @Test
    fun aScrollStateReadsThePositionOnceAFrame() = runRavenTest {
        val state = ScrollState(0)
        setContent {
            VerticalScroll(Modifier.height(100.dp).testTag("scroll"), state) {
                repeat(20) { Box(Modifier.size(50.dp, 30.dp).testTag("box$it")) {} }
            }
        }
        assertEquals(0, state.value)
        Rscroll_box_set_offset(onNodeWithTag("scroll").fetchWidget(), 0f, 120f)
        frame()
        assertEquals(120, state.value, "the user's scroll, read")

        runBlocking { state.scrollTo(30) }
        assertEquals(30, state.value)
        runBlocking { state.scrollToEnd() }
        assertEquals(20 * 30 - 100, state.value, "clamped to the content")
        runBlocking { state.animateScrollTo(10_000) }
        assertEquals(20 * 30 - 100, state.value)

        onNodeWithTag("box2").performScrollTo()
        assertEquals(60, state.value, "scrolled just enough to show it")
    }

    @Test
    fun aPositionAskedForBeforeTheBoxIsShownIsAppliedOnceItIs() = runRavenTest {
        val state = ScrollState(0)
        runBlocking { state.scrollTo(90) }
        setContent {
            HorizontalScroll(Modifier.width(100.dp), state) {
                repeat(10) { Box(Modifier.size(40.dp, 20.dp)) {} }
            }
        }
        frame()
        assertEquals(90, state.value)
    }

    @Test
    fun aScrollStateComesBackWithItsSaveableState() = runRavenTest {
        var shown by mutableStateOf(true)
        var state: ScrollState? = null
        setContent {
            val holder = rememberSaveableStateHolder()
            if (shown) {
                holder.SaveableStateProvider("page") {
                    val scroll = rememberScrollState().also { state = it }
                    VerticalScroll(Modifier.height(100.dp), scroll) { repeat(20) { Box(Modifier.size(50.dp, 30.dp)) {} } }
                }
            }
        }
        runBlocking { state!!.scrollTo(75) }
        shown = false
        waitForIdle()
        shown = true
        waitForIdle()
        frame()
        assertEquals(75, state!!.value, "restored, and applied once shown")
        assertEquals(42, ScrollState.Saver.run { restore(42)!!.value })
    }

    @Test
    fun aStateFollowsOneScrollableAtMost() = runRavenTest {
        val warnings = mutableListOf<String>()
        layoutWarningSink = { warnings += it }
        val state = ScrollState(0)
        setContent {
            Column {
                VerticalScroll(Modifier.height(50.dp).testTag("first"), state) { repeat(5) { Box(Modifier.size(20.dp, 30.dp)) {} } }
                VerticalScroll(Modifier.height(50.dp).testTag("second"), state) { repeat(5) { Box(Modifier.size(20.dp, 30.dp)) {} } }
            }
        }
        assertEquals(listOf("ScrollState: a state follows one scrollable at most, so only the first uses it"), warnings)
        Rscroll_box_set_offset(onNodeWithTag("second").fetchWidget(), 0f, 40f)
        frame()
        assertEquals(0, state.value, "the second isn't followed")
    }

    @Test
    fun aLazyListStateKnowsTheFirstVisibleItem() = runRavenTest {
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            LazyColumn(Modifier.height(100.dp).testTag("list"), state) {
                items((0 until 50).toList(), key = { it }) { Box(Modifier.height(20.dp).testTag("item$it")) {} }
            }
        }
        assertEquals(0 to 0, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset)
        runBlocking { state.scrollToItem(10) }
        assertEquals(10 to 0, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset)
        runBlocking { state.animateScrollToItem(12, 5) }
        assertEquals(12 to 5, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset)

        Rscroll_box_set_offset(onNodeWithTag("list").fetchWidget(), 0f, 215f)
        frame()
        assertEquals(10 to 15, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset, "the user's scroll, read")

        onNodeWithTag("list").performScrollToIndex(30)
        assertEquals(30 to 0, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset)
    }

    @Test
    fun aLazyListPositionIsAppliedOnceItsItemsAreIn() = runRavenTest {
        val state = LazyListState(firstVisibleItemIndex = 4, firstVisibleItemScrollOffset = 3)
        setContent {
            LazyRow(Modifier.width(100.dp), state) { items((0 until 20).toList()) { Box(Modifier.width(25.dp).height(10.dp)) {} } }
        }
        frame()
        assertEquals(4 to 3, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset)
        val saved = with(LazyListState.Saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(state) }!!
        assertEquals(4 to 3, LazyListState.Saver.restore(saved)!!.let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset })
    }

    @Test
    fun aListViewRevealsARow() = runRavenTest {
        val state = ListViewState()
        // Not attached, it does nothing.
        runBlocking { state.revealItem(3) }
        setContent {
            ListView((0 until 100).map { "row $it" }, { it }, -1, {}, Modifier.height(100.dp).testTag("list"), state = state)
        }
        // Raven has no getter for a list's position: the calls reach it without an error.
        runBlocking { state.revealItem(80) }
        onNodeWithTag("list").performScrollToIndex(5)
    }
}
