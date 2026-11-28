package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.runBlocking
import raven.cinterop.R_FALSE
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_METRIC_ROW_HEIGHT
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rtheme_get_metric
import raven.cinterop.Rtree_item_get_child
import raven.cinterop.Rtree_item_get_child_count
import raven.cinterop.Rtree_item_is_expanded
import raven.cinterop.Rtree_view_get_item
import raven.cinterop.Rtree_view_get_selected_item
import raven.cinterop.Rtree_view_set_selected_item
import raven.cinterop.Rwidget_focus
import raven.cinterop.Rwidget_get_desired_size
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.ui.Modifier
import raven.compose.ui.input.key.Key
import raven.compose.ui.test.RavenTestScope
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.test.widgetRect
import raven.compose.ui.testTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TreeView's controlled expansion, its lazy children and revealItem; and a tab renamed. */
class TreeViewExpansionTest {
    private data class Node(val id: Int, val name: String, val children: List<Node> = emptyList())

    // A > (A1 > (A1a), A2), B > (B1)
    private val tree = listOf(
        Node(1, "A", listOf(Node(11, "A1", listOf(Node(111, "A1a"))), Node(12, "A2"))),
        Node(2, "B", listOf(Node(21, "B1"))),
    )

    private fun RavenTestScope.view(): Rid = onNodeWithTag("tree").fetchWidget()

    private fun item(tree: Rid, vararg path: Int): Rid {
        var item = Rtree_view_get_item(tree, path[0])
        for (i in path.drop(1)) item = Rtree_item_get_child(item, i)
        return item
    }

    private val row get() = Rtheme_get_metric(R_METRIC_ROW_HEIGHT)

    /** Clicks the shown row [row] of the tree: on its arrow, or on its text. */
    private fun RavenTestScope.clickRow(row: Int, arrow: Boolean, twice: Boolean = false) {
        val view = view()
        val rect = widgetRect(view)
        val window = Rwidget_get_window(view)
        Rwindow_inject_mouse_move(window, rect.x + if (arrow) 9f else 60f, rect.y + 1f + this@TreeViewExpansionTest.row * (row + 0.5f))
        repeat(if (twice) 2 else 1) {
            Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
            Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
        }
        waitForIdle()
    }

    @Test
    fun eachUserPathIsReportedAndARefusalReverts() = runRavenTest {
        var expanded by mutableStateOf(emptySet<Any>())
        var selection by mutableStateOf(emptySet<Any>())
        var take by mutableStateOf(true)
        val reported = mutableListOf<Set<Any>>()
        setContent {
            TreeView(
                tree, { it.children }, { it.name }, { it.id }, selection, { selection = it },
                expanded, { reported += it; if (take) expanded = it },
                modifier = Modifier.testTag("tree").fillMaxSize(),
            )
        }
        val view = view()
        val a = item(view, 0)
        clickRow(0, arrow = true)
        assertEquals(setOf<Any>(1), reported.last(), "the arrow")
        assertEquals(R_TRUE, Rtree_item_is_expanded(a))
        clickRow(0, arrow = true)
        assertEquals(emptySet(), reported.last())

        clickRow(0, arrow = false, twice = true)
        assertEquals(setOf<Any>(1), reported.last(), "a double click")
        Rwidget_focus(view)
        pressKey(Key.DirectionLeft)
        waitForIdle()
        assertEquals(emptySet(), reported.last(), "Left")
        pressKey(Key.DirectionRight)
        waitForIdle()
        assertEquals(setOf<Any>(1), reported.last(), "Right")
        assertEquals(setOf<Any>(1), expanded)

        // Raven opens the branches above an item it selects: both reported, in one set.
        expanded = emptySet()
        waitForIdle()
        Rtree_view_set_selected_item(view, item(view, 0, 0, 0))
        waitForIdle()
        assertEquals(setOf<Any>(1, 11), expanded)

        // A collapse around the selection moves it to the collapsed item.
        selection = setOf(111)
        waitForIdle()
        clickRow(0, arrow = true)
        assertEquals(setOf<Any>(11), expanded, "A closed; A1 stays open inside it")
        assertEquals(setOf<Any>(1), selection)
        assertEquals(a, Rtree_view_get_selected_item(view))

        take = false
        clickRow(0, arrow = true)
        assertEquals(setOf<Any>(1, 11), reported.last())
        assertEquals(R_FALSE, Rtree_item_is_expanded(a), "refused, it reverts")

        // The app's own change.
        take = true
        expanded = setOf(2)
        waitForIdle()
        assertEquals(R_FALSE, Rtree_item_is_expanded(a))
        assertEquals(R_TRUE, Rtree_item_is_expanded(item(view, 1)))
    }

    @Test
    fun childrenAreAskedForOnlyForTheShownItems() = runRavenTest {
        var expanded by mutableStateOf(emptySet<Any>())
        val asked = mutableListOf<Int>()
        setContent {
            TreeView(
                tree, { asked += it.id; it.children }, { it.name }, { it.id }, emptySet(), {}, expanded, { expanded = it },
                modifier = Modifier.testTag("tree").fillMaxSize(),
            )
        }
        assertEquals(setOf(1, 2), asked.toSet(), "the roots, so their arrows show")
        val view = view()
        assertEquals(2, Rtree_item_get_child_count(item(view, 0)))
        assertEquals(0, Rtree_item_get_child_count(item(view, 0, 0)), "A1's children not asked for yet")

        asked.clear()
        clickRow(0, arrow = true)
        assertEquals(setOf(1, 11, 12, 2), asked.toSet(), "A open: its children are shown, so theirs are asked for")
        assertEquals(1, Rtree_item_get_child_count(item(view, 0, 0)))

        // Closed again, its items stay, and they aren't asked for.
        asked.clear()
        clickRow(0, arrow = true)
        assertEquals(setOf(1, 2), asked.toSet())
        assertEquals(1, Rtree_item_get_child_count(item(view, 0, 0)))
    }

    @Test
    fun revealItemOpensTheBranchesAboveIt() = runRavenTest {
        var expanded by mutableStateOf(emptySet<Any>())
        val state = TreeViewState()
        runBlocking { state.revealItem(111) }
        setContent {
            TreeView(
                tree, { it.children }, { it.name }, { it.id }, emptySet(), {}, expanded, { expanded = it },
                modifier = Modifier.testTag("tree").fillMaxSize(), state = state,
            )
        }
        assertEquals(emptySet(), expanded, "not given to a tree yet, it did nothing")
        runBlocking { state.revealItem(111) }
        assertEquals(setOf<Any>(1, 11), expanded)
        waitForIdle()
        val view = view()
        assertEquals(R_TRUE, Rtree_item_is_expanded(item(view, 0)))
        assertEquals(R_TRUE, Rtree_item_is_expanded(item(view, 0, 0)))
        assertEquals(true, item(view, 0, 0, 0) != R_INVALID_ID)

        // Shown already: scrolled to at once, nothing opened.
        runBlocking { state.revealItem(2) }
        assertEquals(setOf<Any>(1, 11), expanded)
        runBlocking { state.revealItem(999) }
        assertEquals(setOf<Any>(1, 11), expanded, "a key not in the tree does nothing")
    }

    @Test
    fun theExpansionIsSaveable() = runRavenTest {
        var shown by mutableStateOf(true)
        setContent {
            val holder = rememberSaveableStateHolder()
            if (shown) {
                holder.SaveableStateProvider("page") {
                    var expanded by rememberSaveable { mutableStateOf(setOf<Any>()) }
                    TreeView(
                        tree, { it.children }, { it.name }, { it.id }, emptySet(), {}, expanded, { expanded = it },
                        modifier = Modifier.testTag("tree").fillMaxSize(),
                    )
                }
            }
        }
        clickRow(1, arrow = true)
        assertEquals(R_TRUE, Rtree_item_is_expanded(item(view(), 1)))
        shown = false
        waitForIdle()
        shown = true
        waitForIdle()
        assertEquals(R_TRUE, Rtree_item_is_expanded(item(view(), 1)), "B came back open")
        assertEquals(R_FALSE, Rtree_item_is_expanded(item(view(), 0)))
    }

    @Test
    fun aTabIsRenamedThroughItsTitle() = runRavenTest {
        var title by mutableStateOf("Draft")
        setContent {
            TabView(0, {}, Modifier.testTag("tabs")) {
                Tab(title) { Text("page") }
            }
        }
        // Raven has no getter for a page's title, but it measures the tabs by their titles.
        fun width() = memScoped {
            val w = alloc<FloatVar>()
            val h = alloc<FloatVar>()
            Rwidget_get_desired_size(onNodeWithTag("tabs").fetchWidget(), w.ptr, h.ptr)
            w.value
        }
        val before = width()
        title = "Quarterly report for the whole team"
        waitForIdle()
        assertTrue(width() > before + 50f, "the new title, read by Raven: ${width()} after $before")
    }
}
