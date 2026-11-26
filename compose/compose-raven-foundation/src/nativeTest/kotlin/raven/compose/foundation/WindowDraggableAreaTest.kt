package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.Rid
import raven.compose.foundation.window.WindowDragCalls
import raven.compose.foundation.window.WindowDraggableArea
import raven.compose.foundation.window.windowDragCalls
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.test.RavenTestScope
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.testTag
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** WindowDraggableArea, whose drag a headless window never shows: Raven's call is recorded. */
class WindowDraggableAreaTest {
    private val calls = mutableListOf<Pair<Rid, Boolean>>()
    private val defaultCalls = windowDragCalls
    private val defaultSink = layoutWarningSink

    init {
        windowDragCalls = object : WindowDragCalls() {
            override fun setWindowDrag(widget: Rid, enabled: Boolean) {
                calls += widget to enabled
                super.setWindowDrag(widget, enabled)
            }
        }
    }

    @AfterTest
    fun restore() {
        windowDragCalls = defaultCalls
        layoutWarningSink = defaultSink
    }

    /** As [RavenTestScope.setContent], in a window with `customHeader`. */
    private fun RavenTestScope.setCustomHeaderContent(content: @Composable () -> Unit) {
        host.start { Window(onCloseRequest = {}, title = "Test", customHeader = true, content = content) }
        waitForIdle()
    }

    @Test
    fun theAreaDragsTheWindowAndStopsWhenItLeavesTheChain() = runRavenTest {
        var shown by mutableStateOf(true)
        setCustomHeaderContent {
            if (shown) {
                WindowDraggableArea(Modifier.testTag("area")) { Text("Title") }
            } else {
                Text("Title")
            }
        }
        val area = onNodeWithTag("area").fetchWidget()
        assertEquals(listOf(area to true), calls)
        shown = false
        waitForIdle()
        // Released with its node: the drag is switched off before Raven destroys the widget.
        assertEquals(listOf(area to true, area to false), calls)
    }

    @Test
    fun withTheSystemsTitleBarItWarnsAndDoesNothing() {
        val warnings = mutableListOf<String>()
        layoutWarningSink = { warnings += it }
        var title by mutableStateOf("a")
        runRavenTest {
            setContent { WindowDraggableArea { Text(title) } }
            title = "b"
            waitForIdle()
        }
        assertEquals(emptyList(), calls)
        assertEquals(listOf("WindowDraggableArea: the window has its system title bar (customHeader = false), so it doesn't drag the window"), warnings, "once, not on each recomposition")
    }
}
