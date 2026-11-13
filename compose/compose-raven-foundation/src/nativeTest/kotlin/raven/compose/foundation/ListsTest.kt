package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import raven.cinterop.R_FALSE
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_MOUSE_RIGHT
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rlist_view_get_item_count
import raven.cinterop.Rlist_view_get_item_text
import raven.cinterop.Rlist_view_get_selected
import raven.cinterop.Rtree_item_get_child
import raven.cinterop.Rtree_item_get_child_count
import raven.cinterop.Rtree_item_get_text
import raven.cinterop.Rtree_item_is_expanded
import raven.cinterop.Rtree_item_set_expanded
import raven.cinterop.Rtree_view_get_item
import raven.cinterop.Rtree_view_get_item_at
import raven.cinterop.Rtree_view_get_item_count
import raven.cinterop.Rwidget_focus
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.lazy.LazyColumn
import raven.compose.foundation.lazy.LazyRow
import raven.compose.foundation.lazy.items
import raven.compose.foundation.lazy.itemsIndexed
import raven.compose.ui.Modifier
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.ui.graphics.painter.BitmapPainter
import raven.compose.ui.graphics.painter.TextureRegion
import raven.compose.ui.input.key.Key
import raven.compose.ui.testTag
import raven.native.toRavenString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** LazyColumn and LazyRow, and Raven's ListView and TreeView. */
class ListsTest {
    @AfterTest
    fun restoreTreeCalls() {
        treeCalls = TreeCalls()
    }

    private fun RavenTestScope.widget(tag: String) = onNodeWithTag(tag).fetchWidget()

    /** The Raven children of the scroll box's content, in order. */
    private fun RavenTestScope.rows(tag: String): List<Rid> {
        val scroll = widget(tag)
        return List(Rwidget_get_child_count(scroll)) { Rwidget_get_child(scroll, it) }
    }

    @Test
    fun keyedItemsKeepTheirWidgetsWhenTheyMove() = runRavenTest {
        var names by mutableStateOf(listOf("a", "b", "c"))
        setContent {
            LazyColumn(Modifier.testTag("list")) {
                items(names, key = { it }) { Text(it, Modifier.testTag(it)) }
            }
        }
        val before = names.associateWith { widget(it) }
        assertEquals(names.map { before.getValue(it) }, rows("list"))

        names = listOf("c", "a", "b")
        waitForIdle()
        assertEquals(before, names.associateWith { widget(it) }, "the same widgets")
        assertEquals(names.map { before.getValue(it) }, rows("list"), "moved into the new order")

        names = listOf("a", "d")
        waitForIdle()
        assertEquals(before.getValue("a"), widget("a"))
        onNodeWithTag("b").assertDoesNotExist()
        assertEquals(listOf(widget("a"), widget("d")), rows("list"))
    }

    @Test
    fun itemsWithoutKeysAreKnownByTheirPlace() = runRavenTest {
        var names by mutableStateOf(listOf("a", "b"))
        setContent {
            LazyRow(Modifier.testTag("list")) {
                item { Text("first", Modifier.testTag("first")) }
                itemsIndexed(names) { index, name -> Text(name, Modifier.testTag("row$index")) }
            }
        }
        val second = widget("row1")
        names = listOf("b", "a")
        waitForIdle()
        assertEquals(second, widget("row1"), "the place keeps its widget")
        onNodeWithText("a").assertExists()
        assertEquals(3, rows("list").size)
    }

    @Test
    fun keysMustBeUnique() {
        assertFailsWith<IllegalArgumentException> {
            runRavenTest { setContent { LazyColumn { items(listOf("a", "a"), key = { it }) { Text(it) } } } }
        }
    }

    @Test
    fun aListViewAsksForTheRowsItShows() = runRavenTest {
        var fruit by mutableStateOf(listOf("Apple", "Pear"))
        setContent { ListView(fruit, { it.uppercase() }, -1, {}, Modifier.testTag("list")) }
        val list = widget("list")
        assertEquals(2, Rlist_view_get_item_count(list))
        assertEquals("PEAR", Rlist_view_get_item_text(list, 1).toRavenString())

        fruit = listOf("Fig", "Plum", "Kiwi")
        waitForIdle()
        assertEquals(3, Rlist_view_get_item_count(list))
        assertEquals("FIG", Rlist_view_get_item_text(list, 0).toRavenString())
    }

    @Test
    fun aListViewsSelectionIsControlled() = runRavenTest {
        var selected by mutableStateOf(0)
        var take by mutableStateOf(true)
        val reported = mutableListOf<Int>()
        val activated = mutableListOf<Int>()
        setContent {
            ListView(
                listOf("a", "b", "c"), { it }, selected,
                { reported += it; if (take) selected = it },
                Modifier.testTag("list").fillMaxSize(),
                onActivate = { activated += it },
            )
        }
        val list = widget("list")
        assertEquals(0, Rlist_view_get_selected(list))
        Rwidget_focus(list)
        pressKey(Key.DirectionDown)
        waitForIdle()
        assertEquals(listOf(1), reported)
        assertEquals(1, Rlist_view_get_selected(list))

        take = false
        pressKey(Key.DirectionDown)
        waitForIdle()
        assertEquals(listOf(1, 2), reported)
        assertEquals(1, Rlist_view_get_selected(list), "refused, it reverts")

        pressKey(Key.Enter)
        waitForIdle()
        assertEquals(listOf(1), activated)

        selected = -1
        waitForIdle()
        assertEquals(-1, Rlist_view_get_selected(list))
    }

    private data class Node(val id: Int, val name: String, val children: List<Node> = emptyList())

    private fun RavenTestScope.treeTexts(tree: Rid): List<String> {
        val texts = mutableListOf<String>()
        fun visit(item: Rid, depth: Int) {
            texts += "  ".repeat(depth) + Rtree_item_get_text(item).toRavenString()
            repeat(Rtree_item_get_child_count(item)) { visit(Rtree_item_get_child(item, it), depth + 1) }
        }
        repeat(Rtree_view_get_item_count(tree)) { visit(Rtree_view_get_item(tree, it), 0) }
        return texts
    }

    private fun item(tree: Rid, vararg path: Int): Rid {
        var item = Rtree_view_get_item(tree, path[0])
        for (index in path.drop(1)) item = Rtree_item_get_child(item, index)
        return item
    }

    @Test
    fun aTreeIsDiffedByKey() = runRavenTest {
        val docs = Node(1, "Docs", listOf(Node(11, "a.md"), Node(12, "b.md")))
        val src = Node(2, "Src", listOf(Node(21, "main.kt")))
        var roots by mutableStateOf(listOf(docs, src))
        setContent {
            TreeView(roots, { it.children }, { it.name }, { it.id }, emptySet(), {}, modifier = Modifier.testTag("tree"))
        }
        val tree = widget("tree")
        assertEquals(listOf("Docs", "  a.md", "  b.md", "Src", "  main.kt"), treeTexts(tree))
        val docsItem = item(tree, 0)
        val srcItem = item(tree, 1)
        // The user expands Src.
        Rtree_item_set_expanded(srcItem, R_TRUE)

        // Renamed: the same item, re-texted.
        roots = listOf(docs.copy(name = "Documents"), src)
        waitForIdle()
        assertEquals(docsItem, item(tree, 0))
        assertEquals("Documents", Rtree_item_get_text(docsItem).toRavenString())

        // A root inserted first: Raven only appends, so those after it are added again, Src still expanded.
        roots = listOf(Node(3, "Build"), docs, src.copy(children = src.children + Node(22, "util.kt")))
        waitForIdle()
        assertEquals(listOf("Build", "Docs", "  a.md", "  b.md", "Src", "  main.kt", "  util.kt"), treeTexts(tree))
        assertNotEquals(srcItem, item(tree, 2))
        assertEquals(R_TRUE, Rtree_item_is_expanded(item(tree, 2)), "kept expanded")
        assertEquals(R_FALSE, Rtree_item_is_expanded(item(tree, 1)))

        // A child removed from the middle.
        roots = listOf(Node(3, "Build"), docs.copy(children = listOf(Node(12, "b.md"))))
        waitForIdle()
        assertEquals(listOf("Build", "Docs", "  b.md"), treeTexts(tree))
    }

    @Test
    fun onlyNewItemsStartExpanded() = runRavenTest {
        var roots by mutableStateOf(listOf(Node(1, "A", listOf(Node(11, "a")))))
        setContent {
            TreeView(roots, { it.children }, { it.name }, { it.id }, emptySet(), {}, initiallyExpanded = { true }, modifier = Modifier.testTag("tree"))
        }
        val tree = widget("tree")
        assertEquals(R_TRUE, Rtree_item_is_expanded(item(tree, 0)))
        Rtree_item_set_expanded(item(tree, 0), R_FALSE)
        roots = roots + Node(2, "B", listOf(Node(21, "b")))
        waitForIdle()
        assertEquals(R_FALSE, Rtree_item_is_expanded(item(tree, 0)), "the user's choice stays")
        assertEquals(R_TRUE, Rtree_item_is_expanded(item(tree, 1)))
    }

    @Test
    fun aTreesSelectionIsAControlledSetOfKeys() = runRavenTest {
        var selection by mutableStateOf(setOf<Any>(2))
        var take by mutableStateOf(true)
        val reported = mutableListOf<Set<Any>>()
        val activated = mutableListOf<String>()
        val roots = listOf(Node(1, "A"), Node(2, "B"), Node(3, "C"))
        setContent {
            TreeView(
                roots, { it.children }, { it.name }, { it.id }, selection,
                { reported += it; if (take) selection = it },
                onActivate = { activated += it.name },
                modifier = Modifier.testTag("tree").fillMaxSize(),
            )
        }
        val tree = widget("tree")
        assertEquals(setOf(item(tree, 1)), selectedItems(tree))
        Rwidget_focus(tree)
        pressKey(Key.DirectionDown)
        waitForIdle()
        assertEquals(listOf<Set<Any>>(setOf(3)), reported)
        assertEquals(setOf(item(tree, 2)), selectedItems(tree))

        take = false
        pressKey(Key.DirectionUp)
        waitForIdle()
        assertEquals(setOf<Any>(2), reported.last())
        assertEquals(setOf(item(tree, 2)), selectedItems(tree), "refused, it reverts")

        pressKey(Key.Enter)
        waitForIdle()
        assertEquals(listOf("C"), activated)

        // A key the tree hasn't selects nothing.
        selection = setOf(99)
        waitForIdle()
        assertEquals(emptySet(), selectedItems(tree))
    }

    @Test
    fun aMultiSelectTreeTakesSeveralKeys() = runRavenTest {
        setContent {
            TreeView(
                listOf(Node(1, "A"), Node(2, "B"), Node(3, "C")), { it.children }, { it.name }, { it.id },
                setOf(1, 3), {}, multiSelect = true, modifier = Modifier.testTag("tree"),
            )
        }
        val tree = widget("tree")
        assertEquals(setOf(item(tree, 0), item(tree, 2)), selectedItems(tree))
    }

    @Test
    fun aRightClickOpensTheTreesContextMenu() = runRavenTest {
        val asked = mutableListOf<String?>()
        val chosen = mutableListOf<String>()
        setContent {
            TreeView(
                listOf(Node(1, "A"), Node(2, "B")), { it.children }, { it.name }, { it.id }, emptySet(), {},
                contextMenuItems = { node -> asked += node?.name; listOf(ContextMenuItem("Rename") { chosen += "Rename ${node?.name}" }) },
                modifier = Modifier.testTag("tree").fillMaxSize(),
            )
        }
        val tree = widget("tree")
        rightClick(tree, item(tree, 1))
        assertEquals(listOf<String?>("B"), asked)
        assertEquals(1, openMenus().size)
        clickOpenMenuItem("Rename")
        assertEquals(listOf("Rename B"), chosen)

        // In the empty space, the menu is for no item.
        rightClick(tree, R_INVALID_ID)
        assertNull(asked.last())
    }

    @Test
    fun itemIconsAreSetAndUpdated() = runRavenTest {
        val icons = mutableListOf<Pair<Rid, Rid>>()
        treeCalls = object : TreeCalls() {
            override fun setIcon(item: Rid, texture: Rid, region: TextureRegion) {
                icons += item to texture
            }
        }
        lateinit var painter: BitmapPainter
        var withIcon by mutableStateOf(true)
        setContent {
            // Made while Raven runs.
            painter = remember { BitmapPainter(ImageBitmap(2, 2, ByteArray(16) { -1 })) }
            val icon = if (withIcon) painter else null
            TreeView(
                listOf(Node(1, "A")), { it.children }, { it.name }, { it.id }, emptySet(), {},
                itemIcon = { icon }, modifier = Modifier.testTag("tree"),
            )
        }
        val first = item(widget("tree"), 0)
        assertEquals(listOf(first to painter.texture), icons)
        withIcon = false
        waitForIdle()
        assertEquals(first to R_INVALID_ID, icons.last())
    }

    private fun selectedItems(tree: Rid): Set<Rid> =
        (0 until raven.cinterop.Rtree_view_get_selection_count(tree)).map { raven.cinterop.Rtree_view_get_selection(tree, it) }.toSet()

    /** Right-clicks [item] of [tree], or its empty space below the items for `R_INVALID_ID`. */
    private fun RavenTestScope.rightClick(tree: Rid, item: Rid) {
        val rect = widgetRect(tree)
        val x = rect.width / 2
        val y = (1 until rect.height.toInt()).map { it.toFloat() }.lastOrNull { Rtree_view_get_item_at(tree, x, it) == item }
            ?: error("$item isn't shown")
        val window = Rwidget_get_window(tree)
        Rwindow_inject_mouse_move(window, rect.x + x, rect.y + y)
        Rwindow_inject_mouse_button(window, R_MOUSE_RIGHT, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_RIGHT, R_FALSE)
        waitForIdle()
    }
}
