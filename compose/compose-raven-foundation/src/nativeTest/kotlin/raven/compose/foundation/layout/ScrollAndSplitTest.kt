package raven.compose.foundation.layout

import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import raven.cinterop.Rscroll_box_get_offset
import raven.cinterop.Rwidget_scroll_into_view
import raven.compose.foundation.Text
import raven.compose.foundation.runRavenTest
import raven.compose.foundation.widgetRect
import raven.compose.ui.Modifier
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrollAndSplitTest {
    @Test
    fun aVerticalScrollHoldsMoreChildrenThanItShows() = runRavenTest {
        setContent {
            VerticalScroll(Modifier.height(100.dp).testTag("scroll")) {
                repeat(50) { Text("Item $it") }
            }
        }
        val scroll = onNodeWithTag("scroll").fetchWidget()
        val view = widgetRect(scroll)
        assertEquals(100f, view.height)
        val first = widgetRect(onNodeWithText("Item 0").fetchWidget())
        val last = onNodeWithText("Item 49").fetchWidget()
        assertTrue(first.height > 0f, "each child keeps its own height")
        assertTrue(widgetRect(last).y > view.y + view.height, "the last child starts below the view")

        Rwidget_scroll_into_view(last)
        waitForIdle()
        frame()
        val offsetY = memScoped {
            val x = alloc<FloatVar>()
            val y = alloc<FloatVar>()
            Rscroll_box_get_offset(scroll, x.ptr, y.ptr)
            y.value
        }
        assertTrue(offsetY > 0f, "scrolled down")
        val shown = widgetRect(last)
        assertTrue(shown.y + shown.height <= view.y + view.height + 0.5f, "the last child is in view")
    }

    @Test
    fun aHorizontalScrollLaysItsChildrenInARow() = runRavenTest {
        setContent {
            HorizontalScroll(Modifier.width(100.dp).testTag("scroll"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(30) { Text("Item $it") }
            }
        }
        val view = widgetRect(onNodeWithTag("scroll").fetchWidget())
        val first = widgetRect(onNodeWithText("Item 0").fetchWidget())
        val second = widgetRect(onNodeWithText("Item 1").fetchWidget())
        assertEquals(first.y, second.y)
        assertEquals(first.x + first.width + 4f, second.x)
        assertTrue(widgetRect(onNodeWithText("Item 29").fetchWidget()).x > view.x + view.width)
    }

    @Test
    fun splitterPanesAreSizedByTheirWeights() = runRavenTest {
        setContent {
            HorizontalSplitter(Modifier.fillMaxSize()) {
                Box(Modifier.testTag("a"))
                Box(Modifier.weight(3f).testTag("b"))
            }
        }
        val a = widgetRect(onNodeWithTag("a").fetchWidget())
        val b = widgetRect(onNodeWithTag("b").fetchWidget())
        assertEquals(3f, b.width / a.width, 0.05f)
        assertTrue(b.x > a.x + a.width, "side by side, a handle between them")
        assertEquals(a.height, b.height, "each pane fills the splitter's height")
        assertTrue(a.height > 500f)
    }

    @Test
    fun aVerticalSplitterStacksEqualPanes() = runRavenTest {
        setContent {
            VerticalSplitter(Modifier.fillMaxSize()) {
                Box(Modifier.testTag("top"))
                Box(Modifier.testTag("bottom"))
            }
        }
        val top = widgetRect(onNodeWithTag("top").fetchWidget())
        val bottom = widgetRect(onNodeWithTag("bottom").fetchWidget())
        assertEquals(top.height, bottom.height, 1f)
        assertTrue(bottom.y > top.y + top.height)
        assertEquals(top.width, bottom.width)
        assertTrue(top.width > 700f, "each pane fills the splitter's width")
    }
}
