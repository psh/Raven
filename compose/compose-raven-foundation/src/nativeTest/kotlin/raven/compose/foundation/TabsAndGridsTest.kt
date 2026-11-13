package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import raven.cinterop.R_FALSE
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_MOUSE_MIDDLE
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rtab_view_get_active
import raven.cinterop.Rtab_view_get_tab
import raven.cinterop.Rtab_view_get_tab_count
import raven.cinterop.Rtab_view_show_tab
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Grid
import raven.compose.foundation.layout.GridTrack
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.size
import raven.compose.foundation.layout.width
import raven.compose.foundation.lazy.grid.GridCells
import raven.compose.foundation.lazy.grid.LazyVerticalGrid
import raven.compose.foundation.lazy.grid.items
import raven.compose.ui.Modifier
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.ui.graphics.painter.BitmapPainter
import raven.compose.ui.graphics.painter.TextureRegion
import raven.compose.ui.testTag
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TabView, Grid and LazyVerticalGrid. */
class TabsAndGridsTest {
    @AfterTest
    fun restoreTabCalls() {
        tabCalls = TabCalls()
    }

    private fun RavenTestScope.widget(tag: String) = onNodeWithTag(tag).fetchWidget()

    private fun RavenTestScope.rect(tag: String) = widgetRect(widget(tag))

    private fun pages(tabView: Rid): List<Rid> = List(Rtab_view_get_tab_count(tabView)) { Rtab_view_get_tab(tabView, it) }

    @Test
    fun theSelectionIsControlled() = runRavenTest {
        var selected by mutableStateOf(2)
        var take by mutableStateOf(true)
        val reported = mutableListOf<Int>()
        setContent {
            TabView(selected, { reported += it; if (take) selected = it }, Modifier.testTag("tabs").fillMaxSize()) {
                Tab("One") { Text("first") }
                Tab("Two") { Text("second") }
                Tab("Three") { Text("third") }
            }
        }
        val tabs = widget("tabs")
        assertEquals(3, Rtab_view_get_tab_count(tabs))
        assertEquals(2, Rtab_view_get_active(tabs), "the app's, set once the tabs are in")

        Rtab_view_show_tab(tabs, pages(tabs)[0])
        waitForIdle()
        assertEquals(listOf(0), reported)
        assertEquals(0, Rtab_view_get_active(tabs))

        take = false
        Rtab_view_show_tab(tabs, pages(tabs)[1])
        waitForIdle()
        assertEquals(listOf(0, 1), reported)
        assertEquals(0, Rtab_view_get_active(tabs), "refused, it reverts")

        selected = 1
        waitForIdle()
        assertEquals(1, Rtab_view_get_active(tabs))
    }

    @Test
    fun aTabAddedLaterCanBeTheSelectedOne() = runRavenTest {
        var count by mutableStateOf(1)
        setContent {
            TabView(count - 1, {}, Modifier.testTag("tabs").fillMaxSize()) {
                repeat(count) { Tab("Tab $it") { Text("page $it") } }
            }
        }
        count = 3
        waitForIdle()
        assertEquals(2, Rtab_view_get_active(widget("tabs")))
    }

    @Test
    fun closingATabAsksTheApp() = runRavenTest {
        var names by mutableStateOf(listOf("a.txt", "b.txt"))
        var take by mutableStateOf(false)
        val asked = mutableListOf<String>()
        setContent {
            TabView(0, {}, Modifier.testTag("tabs").fillMaxSize(), documents = true) {
                for (name in names) {
                    Tab(name, onClose = { asked += name; if (take) names = names - name }) { Text(name) }
                }
            }
        }
        val tabs = widget("tabs")
        middleClickFirstTab(tabs)
        waitForIdle()
        assertEquals(listOf("a.txt"), asked)
        assertEquals(2, Rtab_view_get_tab_count(tabs), "refused, the tab stays")

        take = true
        middleClickFirstTab(tabs)
        waitForIdle()
        assertEquals(listOf("a.txt", "a.txt"), asked)
        assertEquals(1, Rtab_view_get_tab_count(tabs))
        onNodeWithText("b.txt").assertExists()
    }

    @Test
    fun aTabThatIsntClosableIsntAsked() = runRavenTest {
        val asked = mutableListOf<String>()
        setContent {
            TabView(0, {}, Modifier.testTag("tabs").fillMaxSize(), documents = true) {
                Tab("Level", closable = false, onClose = { asked += "Level" }) { Text("level") }
            }
        }
        middleClickFirstTab(widget("tabs"))
        waitForIdle()
        assertEquals(emptyList(), asked)
    }

    @Test
    fun tabIconsAreSetOnTheirTab() = runRavenTest {
        val icons = mutableListOf<Pair<Rid, Rid>>()
        tabCalls = object : TabCalls() {
            override fun setIcon(tabView: Rid, page: Rid, texture: Rid, region: TextureRegion) {
                icons += page to texture
            }
        }
        var withIcon by mutableStateOf(true)
        lateinit var painter: BitmapPainter
        setContent {
            painter = remember { BitmapPainter(ImageBitmap(2, 2, ByteArray(16) { -1 })) }
            TabView(0, {}, Modifier.testTag("tabs"), documents = true) {
                Tab("Doc", icon = if (withIcon) painter else null) { Text("doc") }
            }
        }
        val page = pages(widget("tabs")).single()
        assertEquals(listOf(page to painter.texture), icons)
        withIcon = false
        waitForIdle()
        assertEquals(page to R_INVALID_ID, icons.last())
    }

    @Test
    fun gridChildrenTakeTheirCellsAndSpans() = runRavenTest {
        setContent {
            Grid(listOf(GridTrack.Auto, GridTrack.Fill()), emptyList(), Modifier.testTag("grid").width(300.dp), spacing = 10.dp) {
                Box(Modifier.size(50.dp).testTag("a")) {}
                Box(Modifier.cell(1, 0).testTag("b")) {}
                Box(Modifier.cell(0, 1, columnSpan = 2).testTag("c")) {}
            }
        }
        val a = rect("a")
        val b = rect("b")
        val c = rect("c")
        assertEquals(a.x + 50f + 10f, b.x, "the next column, after the gap")
        assertEquals(a.y, b.y)
        assertEquals(300f - 60f, b.width, "the Fill column takes what is left, which b fills")
        assertEquals(a.y + 50f + 10f, c.y)
        assertEquals(300f, c.width, "spanning both columns")
    }

    @Test
    fun aLazyGridHasAMinimumCellSize() = runRavenTest {
        setContent {
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.testTag("grid").size(400.dp), minCellSize = DpSize(60.dp, 70.dp)) {
                items((1..4).toList(), key = { it }) { Text("$it", Modifier.testTag("item$it")) }
            }
        }
        val first = rect("item1")
        val fourth = rect("item4")
        assertTrue(first.width >= 60f && first.height >= 70f, "$first")
        assertEquals(first.x, fourth.x, "the fourth starts the second row")
        assertTrue(fourth.y >= first.y + 70f)
        assertEquals(rect("item2").y, first.y)
    }

    /** Middle-clicks inside the first tab of a documents tab view, which closes a closable tab. */
    private fun middleClickFirstTab(tabView: Rid) {
        val rect = widgetRect(tabView)
        val window = Rwidget_get_window(tabView)
        Rwindow_inject_mouse_move(window, rect.x + 12f, rect.y + 10f)
        Rwindow_inject_mouse_button(window, R_MOUSE_MIDDLE, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_MIDDLE, R_FALSE)
    }
}
