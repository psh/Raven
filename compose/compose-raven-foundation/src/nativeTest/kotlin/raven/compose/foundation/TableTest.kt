package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.runBlocking
import raven.cinterop.R_FALSE
import raven.cinterop.R_METRIC_ROW_HEIGHT
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rtable_get_cell
import raven.cinterop.Rtable_get_column_count
import raven.cinterop.Rtable_get_column_header
import raven.cinterop.Rtable_get_column_width
import raven.cinterop.Rtable_get_row_count
import raven.cinterop.Rtable_get_selected
import raven.cinterop.Rtable_get_sort_column
import raven.cinterop.Rtable_is_sort_descending
import raven.cinterop.Rtable_set_column_width
import raven.cinterop.Rtheme_get_metric
import raven.cinterop.Rwidget_focus
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.ui.Alignment
import raven.compose.ui.Modifier
import raven.compose.ui.input.key.Key
import raven.compose.ui.test.RavenTestScope
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.test.widgetRect
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals

/** Table over Raven's virtual table. */
class TableTest {
    private data class Person(val name: String, val age: Int, val city: String)

    private val people = listOf(Person("Ada", 36, "London"), Person("Grace", 85, "Arlington"), Person("Linus", 54, "Portland"))

    private fun RavenTestScope.table() = onNodeWithTag("table").fetchWidget()

    private fun cell(table: Rid, row: Int, column: Int) = Rtable_get_cell(table, row, column).toRavenString()

    private fun rows(table: Rid) = List(Rtable_get_row_count(table)) { row -> List(Rtable_get_column_count(table)) { cell(table, row, it) } }

    private fun headers(table: Rid) = List(Rtable_get_column_count(table)) { Rtable_get_column_header(table, it).toRavenString() }

    private fun click(table: Rid, x: Float, y: Float) {
        val rect = widgetRect(table)
        val window = Rwidget_get_window(table)
        Rwindow_inject_mouse_move(window, rect.x + x, rect.y + y)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
    }

    private val rowHeight get() = Rtheme_get_metric(R_METRIC_ROW_HEIGHT)

    /** A click on the header of the first column, 160 dp wide, inside the table's 1 dp border. */
    private fun RavenTestScope.clickFirstHeader() {
        click(table(), 1f + 80f, 1f + (rowHeight + 4f) / 2)
        waitForIdle()
    }

    private fun RavenTestScope.clickRow(row: Int) {
        click(table(), 1f + 80f, 1f + rowHeight + 4f + rowHeight * row + rowHeight / 2)
        waitForIdle()
    }

    @Test
    fun theCellsAreAskedOfTheItemsAndTheColumns() = runRavenTest {
        var shown by mutableStateOf(people)
        setContent {
            Table(shown, -1, {}, Modifier.testTag("table").fillMaxSize()) {
                column("Name", width = 160.dp) { it.name }
                column("Age", width = 60.dp, align = Alignment.End) { it.age.toString() }
                column("City", fill = 1f) { it.city }
            }
        }
        val table = table()
        assertEquals(listOf("Name", "Age", "City"), headers(table))
        assertEquals(listOf(listOf("Ada", "36", "London"), listOf("Grace", "85", "Arlington"), listOf("Linus", "54", "Portland")), rows(table))
        assertEquals(160f, Rtable_get_column_width(table, 0))

        shown = shown.reversed() + Person("Tim", 69, "London")
        waitForIdle()
        assertEquals(listOf("Linus", "Grace", "Ada", "Tim"), List(4) { cell(table, it, 0) }, "a changed list, shown")
    }

    @Test
    fun columnsAreMatchedByPlace() = runRavenTest {
        var header by mutableStateOf("Name")
        var width by mutableStateOf(160.dp)
        var withCity by mutableStateOf(false)
        setContent {
            Table(people, -1, {}, Modifier.testTag("table").fillMaxSize()) {
                column(header, width = width) { it.name }
                column("Age") { it.age.toString() }
                if (withCity) column("City") { it.city }
            }
        }
        val table = table()
        header = "Who"
        waitForIdle()
        assertEquals(listOf("Who", "Age"), headers(table))

        // The user drags the edge: the width is Raven's until the app's changes.
        Rtable_set_column_width(table, 0, 220f)
        header = "Person"
        waitForIdle()
        assertEquals(220f, Rtable_get_column_width(table, 0), "the user's width kept")
        width = 100.dp
        waitForIdle()
        assertEquals(100f, Rtable_get_column_width(table, 0), "the app's new width")

        withCity = true
        waitForIdle()
        assertEquals(listOf("Person", "Age", "City"), headers(table), "made again")
        assertEquals("Arlington", cell(table, 1, 2))
    }

    @Test
    fun sortingIsTheAppsAndARefusedSortReverts() = runRavenTest {
        var sort by mutableStateOf<TableSort?>(null)
        var take by mutableStateOf(true)
        val reported = mutableListOf<TableSort?>()
        setContent {
            val sorted = when (sort) {
                null -> people
                else -> if (sort!!.descending) people.sortedByDescending { it.name } else people.sortedBy { it.name }
            }
            Table(sorted, -1, {}, Modifier.testTag("table").fillMaxSize(), sort = sort, onSortChange = { reported += it; if (take) sort = it }) {
                column("Name", width = 160.dp) { it.name }
                column("Age", sortable = false) { it.age.toString() }
            }
        }
        val table = table()
        assertEquals(-1, Rtable_get_sort_column(table))
        clickFirstHeader()
        assertEquals(listOf<TableSort?>(TableSort(0, false)), reported)
        assertEquals(0 to R_FALSE, Rtable_get_sort_column(table) to Rtable_is_sort_descending(table))

        clickFirstHeader()
        assertEquals(TableSort(0, true), reported.last())
        assertEquals(R_TRUE, Rtable_is_sort_descending(table))
        assertEquals(listOf("Linus", "Grace", "Ada"), List(3) { cell(table, it, 0) }, "the app sorted its items")

        take = false
        clickFirstHeader()
        assertEquals(TableSort(0, false), reported.last())
        assertEquals(R_TRUE, Rtable_is_sort_descending(table), "refused, the arrow reverts")

        sort = null
        waitForIdle()
        assertEquals(-1, Rtable_get_sort_column(table))
        sort = TableSort(0, false)
        waitForIdle()
        assertEquals(0 to R_FALSE, Rtable_get_sort_column(table) to Rtable_is_sort_descending(table))
    }

    @Test
    fun theSelectionIsControlledAndRowsActivate() = runRavenTest {
        var selected by mutableStateOf(-1)
        var take by mutableStateOf(true)
        val reported = mutableListOf<Int>()
        val activated = mutableListOf<Int>()
        setContent {
            Table(
                people, selected, { reported += it; if (take) selected = it }, Modifier.testTag("table").fillMaxSize(),
                onActivate = { activated += it },
            ) { column("Name", width = 160.dp) { it.name } }
        }
        val table = table()
        clickRow(1)
        assertEquals(listOf(1), reported)
        assertEquals(1, Rtable_get_selected(table))

        take = false
        clickRow(2)
        assertEquals(listOf(1, 2), reported)
        assertEquals(1, Rtable_get_selected(table), "refused, it reverts")

        Rwidget_focus(table)
        pressKey(Key.Enter)
        waitForIdle()
        assertEquals(listOf(1), activated)

        selected = 0
        waitForIdle()
        assertEquals(0, Rtable_get_selected(table))
    }

    @Test
    fun aTableStateRevealsARow() = runRavenTest {
        val state = TableState()
        runBlocking { state.revealRow(3) }
        setContent {
            Table((0 until 100_000).toList(), -1, {}, Modifier.testTag("table").fillMaxSize(), state = state, gridLines = true, striped = true) {
                column("Row") { "row $it" }
            }
        }
        assertEquals(100_000, Rtable_get_row_count(table()))
        // Raven has no getter for a table's position: the call reaches it without an error.
        runBlocking { state.revealRow(90_000) }
        assertEquals("row 90000", cell(table(), 90_000, 0))
    }
}
