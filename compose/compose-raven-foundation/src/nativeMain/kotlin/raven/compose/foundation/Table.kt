package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_TABLE
import raven.cinterop.Rid
import raven.cinterop.Rtable_add_column
import raven.cinterop.Rtable_clear_columns
import raven.cinterop.Rtable_get_sort_column
import raven.cinterop.Rtable_is_sort_descending
import raven.cinterop.Rtable_scroll_to_row
import raven.cinterop.Rtable_set_column_align
import raven.cinterop.Rtable_set_column_fill
import raven.cinterop.Rtable_set_column_header
import raven.cinterop.Rtable_set_column_sortable
import raven.cinterop.Rtable_set_column_width
import raven.cinterop.Rtable_set_grid_lines
import raven.cinterop.Rtable_set_header_visible
import raven.cinterop.Rtable_set_striped
import raven.cinterop.Rtable_sort
import raven.cinterop.Rwidget_is_valid
import raven.compose.ui.Alignment
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.TableActivated
import raven.compose.ui.TableCellText
import raven.compose.ui.TableRows
import raven.compose.ui.TableSelected
import raven.compose.ui.TableSelectionChanged
import raven.compose.ui.TableSortChanged
import raven.compose.ui.TableSortOrder
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.WidgetFollower
import raven.compose.ui.bind
import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.dp

private val TableWidget = WidgetDescriptor(
    "Table", R_WIDGET_TABLE, events = listOf(TableCellText, TableSelectionChanged, TableActivated, TableSortChanged),
)

/** A [Table]'s sort: by the column at [column], down when [descending]. The app sorts its items. */
@Immutable
public data class TableSort(val column: Int, val descending: Boolean = false)

/** The DSL of a [Table]'s columns, in order. */
public interface TableScope<T> {
    /**
     * A column headed [header], showing `text(item)` in each row: [width] wide, or sharing what is
     * left over in proportion to [fill] when it is above 0, its texts placed by [align]. The user
     * sorts by it with a click on its header unless it isn't [sortable].
     */
    public fun column(
        header: String,
        width: Dp = 120.dp,
        fill: Float = 0f,
        align: Alignment.Horizontal = Alignment.Start,
        sortable: Boolean = true,
        text: (T) -> String,
    )
}

/** What Raven is told of a column; its text stays on the Kotlin side. */
private data class TableColumnSpec(val header: String, val width: Float, val fill: Float, val align: Float, val sortable: Boolean)

private class TableColumns<T> : TableScope<T> {
    val specs = ArrayList<TableColumnSpec>()
    val texts = ArrayList<(T) -> String>()

    override fun column(header: String, width: Dp, fill: Float, align: Alignment.Horizontal, sortable: Boolean, text: (T) -> String) {
        val place = when (align) {
            Alignment.CenterHorizontally -> 0.5f
            Alignment.End -> 1f
            else -> 0f
        }
        specs += TableColumnSpec(header, width.value, fill, place, sortable)
        texts += text
    }
}

/**
 * Matches Raven's columns to the specs by place: what changed is set, and a different number of
 * columns makes them all again, keeping the sort. A width is set only when the app's changes, so a
 * column the user resized keeps its width until then.
 */
private class TableColumnMirror {
    private var applied: List<TableColumnSpec> = emptyList()

    fun sync(table: Rid, specs: List<TableColumnSpec>) {
        if (specs.size != applied.size) {
            val sorted = Rtable_get_sort_column(table)
            val descending = Rtable_is_sort_descending(table)
            Rtable_clear_columns(table)
            for ((i, spec) in specs.withIndex()) {
                Rtable_add_column(table, spec.header, spec.width)
                Rtable_set_column_fill(table, i, spec.fill)
                Rtable_set_column_align(table, i, spec.align)
                Rtable_set_column_sortable(table, i, spec.sortable.toRaven())
            }
            if (sorted in specs.indices) Rtable_sort(table, sorted, descending)
        } else {
            for ((i, spec) in specs.withIndex()) {
                val before = applied[i]
                if (spec.header != before.header) Rtable_set_column_header(table, i, spec.header)
                if (spec.width != before.width) Rtable_set_column_width(table, i, spec.width)
                if (spec.fill != before.fill) Rtable_set_column_fill(table, i, spec.fill)
                if (spec.align != before.align) Rtable_set_column_align(table, i, spec.align)
                if (spec.sortable != before.sortable) Rtable_set_column_sortable(table, i, spec.sortable.toRaven())
            }
        }
        applied = specs
    }
}

private fun Boolean.toRaven() = if (this) R_TRUE else R_FALSE

private val HeaderVisible = Property<Boolean> { widget, visible -> Rtable_set_header_visible(widget, visible.toRaven()) }
private val GridLines = Property<Boolean> { widget, visible -> Rtable_set_grid_lines(widget, visible.toRaven()) }
private val Striped = Property<Boolean> { widget, striped -> Rtable_set_striped(widget, striped.toRaven()) }

/**
 * Raven's sortable data grid, for text cells: a row for each of [items] and the columns [columns]
 * declares, `column("Name", width = 160.dp) { it.name }`. It is always virtual: only the cells
 * drawn are asked for, reading the latest [items] and columns, so 100,000 rows cost what ten do.
 *
 * - **Sorting is the app's**: a click on a header calls [onSortChange] (null when Raven turns
 *   sorting off), and the arrow shows the [sort] passed, so a sort the app doesn't take reverts. The
 *   app sorts [items] itself: the rows show them in the order given, and every index is an index
 *   of [items].
 * - **The selection** is [ListView]'s: the row at [selectedIndex], or none at -1, controlled, a
 *   choice the app doesn't take reverting. [onActivate] gets a row double-clicked, or Enter pressed
 *   on it.
 * - **Columns** are matched by place. The user resizes one by dragging its header's edge, and
 *   keeps that width until the app's `width` changes.
 * - [state] reveals rows; [headerVisible], [gridLines] and [striped] are Raven's.
 */
@Composable
public fun <T> Table(
    items: List<T>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sort: TableSort? = null,
    onSortChange: (TableSort?) -> Unit = {},
    state: TableState = rememberTableState(),
    onActivate: (Int) -> Unit = {},
    headerVisible: Boolean = true,
    gridLines: Boolean = false,
    striped: Boolean = false,
    columns: TableScope<T>.() -> Unit,
) {
    require(selectedIndex in -1..items.lastIndex) { "selectedIndex $selectedIndex is not -1 or an index of the ${items.size} items" }
    val declared = TableColumns<T>().apply(columns)
    val mirror = remember { TableColumnMirror() }
    val follower = remember { TableFollower() }
    val texts = declared.texts
    val cellText: (Int, Int) -> String = { row, column ->
        val item = items.getOrNull(row)
        if (item == null) "" else texts.getOrNull(column)?.invoke(item) ?: ""
    }
    RavenWidget(TableWidget, modifier, {
        bind(follower)
        set(state) { follower.state = it }
        // The columns first: the rows, the sort and the selection are of them.
        set(declared.specs.toList()) { mirror.sync(widget, it) }
        bind(cellText, TableCellText)
        // A copy: a list changed in place, a SnapshotStateList say, is equal to itself.
        bind(items.toList(), TableRows)
        bind(sort?.let { it.column to it.descending } ?: (-1 to false), TableSortOrder)
        bind(selectedIndex, TableSelected)
        bind(onSelectedIndexChange, TableSelectionChanged)
        bind({ column: Int, descending: Boolean -> onSortChange(if (column < 0) null else TableSort(column, descending)) }, TableSortChanged)
        bind(onActivate, TableActivated)
        bind(headerVisible, HeaderVisible)
        bind(gridLines, GridLines)
        bind(striped, Striped)
    })
}

/**
 * What an app can ask of a [Table]'s position: [revealRow]. Raven can't report the table's
 * position, so it isn't saved; on a state not attached to a table, [revealRow] does nothing.
 */
@Stable
public class TableState internal constructor() {
    internal var table: Rid? = null

    /** Scrolls just enough to show the row at [index] (`Rtable_scroll_to_row`). */
    public suspend fun revealRow(index: Int) {
        table?.takeIf { Rwidget_is_valid(it) == R_TRUE }?.let { Rtable_scroll_to_row(it, index) }
    }
}

/** A [TableState]. */
@Composable
public fun rememberTableState(): TableState = remember { TableState() }

/** Points a [TableState] at its table while the table's node holds it. */
private class TableFollower : WidgetFollower {
    private var widget: Rid? = null

    var state: TableState? = null
        set(value) {
            if (value === field) return
            widget?.let { table ->
                if (field?.table == table) field?.table = null
                value?.attachTo(table)
            }
            field = value
        }

    private fun TableState.attachTo(table: Rid) {
        if (this.table != null && this.table != table) warnSecondScrollable("TableState") else this.table = table
    }

    override fun attach(widget: Rid) {
        this.widget = widget
        state?.attachTo(widget)
    }

    override fun follow(widget: Rid) = Unit

    override fun detach(widget: Rid) {
        if (state?.table == widget) state?.table = null
        this.widget = null
    }
}
