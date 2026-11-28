package raven.compose.foundation

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.height
import raven.compose.foundation.layout.width
import raven.compose.foundation.lazy.LazyColumn
import raven.compose.foundation.lazy.LazyListItemInfo
import raven.compose.foundation.lazy.LazyListLayoutInfo
import raven.compose.foundation.lazy.LazyListState
import raven.compose.foundation.lazy.LazyRow
import raven.compose.foundation.lazy.items
import raven.compose.foundation.lazy.itemsIndexed
import raven.compose.ui.Modifier
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** LazyListState's layoutInfo and canScroll flags, and the DSL's contentType. */
class LazyListLayoutInfoTest {
    private fun LazyListLayoutInfo.indices() = visibleItemsInfo.map { it.index }

    private fun LazyListState.flags() = canScrollBackward to canScrollForward

    @Test
    fun theLayoutInfoFollowsTheScrollAndTheItems() = runRavenTest {
        var keys by mutableStateOf((0 until 100).toList())
        val state = LazyListState()
        setContent {
            LazyColumn(Modifier.height(100.dp).testTag("list"), state) {
                items(keys, key = { it }) { Box(Modifier.height(20.dp)) {} }
            }
        }
        assertEquals(LazyListLayoutInfo((0..4).map { LazyListItemInfo(it, it, it * 20, 20) }, 100, 0, 100), state.layoutInfo)

        onNodeWithTag("list").performScrollBy(dy = 30f)
        frame()
        waitForIdle()
        assertEquals((1..6).toList(), state.layoutInfo.indices())
        assertEquals(LazyListItemInfo(1, 1, -10, 20), state.layoutInfo.visibleItemsInfo.first())
        assertEquals(LazyListItemInfo(6, 6, 90, 20), state.layoutInfo.visibleItemsInfo.last())

        keys = listOf(-1) + keys.filter { it % 2 == 0 }
        waitForIdle()
        // Item 1 is gone, so the start stays at its place, with the item there now.
        assertEquals(51, state.layoutInfo.totalItemsCount)
        assertEquals(listOf(0, 2, 4, 6, 8, 10), state.layoutInfo.visibleItemsInfo.map { it.key })
        assertEquals(-10, state.layoutInfo.visibleItemsInfo.first().offset)
    }

    @Test
    fun theFlagsAtEachEnd() = runRavenTest {
        val state = LazyListState()
        val short = LazyListState()
        setContent {
            Column {
                LazyColumn(Modifier.height(100.dp), state) { items(100) { Box(Modifier.height(20.dp)) {} } }
                LazyRow(Modifier.width(100.dp), short) { items(3) { Box(Modifier.width(20.dp).height(10.dp)) {} } }
            }
        }
        assertEquals(false to true, state.flags(), "at the start")
        runBlocking { state.scrollToItem(50) }
        waitForIdle()
        assertEquals(true to true, state.flags())
        runBlocking { state.scrollToItem(99) }
        waitForIdle()
        assertEquals(true to false, state.flags(), "at the end")
        assertEquals(false to false, short.flags(), "nowhere to go")
        assertEquals(listOf(0 to 0, 1 to 20, 2 to 40), short.layoutInfo.visibleItemsInfo.map { it.index to it.offset })
        assertEquals(3 to 100, short.layoutInfo.totalItemsCount to short.layoutInfo.viewportEndOffset)
    }

    @Test
    fun aRecompositionReadsTheLayoutInfo() = runRavenTest {
        val state = LazyListState()
        setContent {
            val scope = rememberCoroutineScope()
            val pastFirst by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0) > 0 } }
            Column {
                if (pastFirst) Button({ scope.launch { state.scrollToItem(0) } }, Modifier.testTag("top")) { Text("Top") }
                LazyColumn(Modifier.height(100.dp), state) { items(100) { Box(Modifier.height(20.dp)) {} } }
            }
        }
        onNodeWithTag("top").assertDoesNotExist()
        runBlocking { state.scrollToItem(10) }
        waitForIdle()
        onNodeWithTag("top").performClick()
        waitForIdle()
        assertEquals(0, state.firstVisibleItemIndex)
        onNodeWithTag("top").assertDoesNotExist()
    }

    @Test
    fun contentTypeIsAcceptedAndIgnored() = runRavenTest {
        setContent {
            LazyColumn(Modifier.height(100.dp)) {
                item(key = "header", contentType = "header") { Text("Header", Modifier.testTag("header")) }
                items(2, key = { "n$it" }, contentType = { "number" }) { Text("n$it", Modifier.testTag("n$it")) }
                items(listOf("a"), key = { it }, contentType = { "letter" }) { Text(it, Modifier.testTag(it)) }
                itemsIndexed(listOf("b"), contentType = { _, _ -> "letter" }) { _, it -> Text(it, Modifier.testTag(it)) }
            }
        }
        listOf("header", "n0", "n1", "a", "b").forEach { onNodeWithTag(it).assertExists() }
    }
}
