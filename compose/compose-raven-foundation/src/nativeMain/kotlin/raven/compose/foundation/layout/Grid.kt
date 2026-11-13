package raven.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import raven.cinterop.R_WIDGET_GRID
import raven.cinterop.Rgrid_set_column_fill
import raven.cinterop.Rgrid_set_row_fill
import raven.cinterop.Rid
import raven.compose.ui.ChildKind
import raven.compose.ui.GridCellElement
import raven.compose.ui.LayoutAxis
import raven.compose.ui.MainAxis
import raven.compose.ui.Modifier
import raven.compose.ui.PanelLayout
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.dp

/** The size of a [Grid]'s column or row. */
public sealed interface GridTrack {
    /** As large as what is in it. */
    public data object Auto : GridTrack

    /** A share of the space left, [weight] against the other `Fill` tracks. */
    public data class Fill(val weight: Float = 1f) : GridTrack {
        init {
            require(weight > 0f) { "a Fill track's weight must be positive, not $weight" }
        }
    }
}

/** What a [Grid]'s children can say about their place in it. */
public interface GridScope {
    /**
     * Puts the child in the cell at [column] and [row], from 0, spanning [columnSpan] columns and
     * [rowSpan] rows. A child without one is in the first cell.
     */
    public fun Modifier.cell(column: Int, row: Int, columnSpan: Int = 1, rowSpan: Int = 1): Modifier =
        this then GridCellElement(column, row, columnSpan, rowSpan)
}

private object GridScopeInstance : GridScope

private val GridWidget = WidgetDescriptor("Grid", R_WIDGET_GRID, ChildKind.Many)

/** A grid's tracks, and those it set before, so a track left out goes back to `Auto`. */
private class GridTracks(val applied: AppliedTracks, val columns: List<GridTrack>, val rows: List<GridTrack>) {
    override fun equals(other: Any?): Boolean = other is GridTracks && columns == other.columns && rows == other.rows

    override fun hashCode(): Int = columns.hashCode() * 31 + rows.hashCode()
}

private class AppliedTracks {
    var columns = 0
    var rows = 0

    fun apply(grid: Rid, columns: List<GridTrack>, rows: List<GridTrack>) {
        for (i in 0 until maxOf(columns.size, this.columns)) Rgrid_set_column_fill(grid, i, columns.getOrNull(i).fill())
        for (i in 0 until maxOf(rows.size, this.rows)) Rgrid_set_row_fill(grid, i, rows.getOrNull(i).fill())
        this.columns = columns.size
        this.rows = rows.size
    }

    private fun GridTrack?.fill(): Float = (this as? GridTrack.Fill)?.weight ?: 0f
}

private val Tracks = Property<GridTracks> { widget, tracks -> tracks.applied.apply(widget, tracks.columns, tracks.rows) }

/**
 * Raven's Grid: its children in cells given by `Modifier.cell`, each filling its cell unless it is
 * aligned. [columns] and [rows] size the tracks, `Auto` by default; there are as many as the cells
 * need, so the lists only need the tracks that aren't `Auto`. [spacing] is the gap between tracks,
 * in both directions.
 */
@Composable
public fun Grid(
    columns: List<GridTrack>,
    rows: List<GridTrack>,
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    content: @Composable GridScope.() -> Unit,
) {
    val applied = remember { AppliedTracks() }
    RavenWidget(GridWidget, modifier, {
        bind(PanelLayout(LayoutAxis.None, mainAxis = MainAxis.Cells))
        bind(GridTracks(applied, columns, rows), Tracks)
        bind(spacing, PanelSpacing)
    }) { GridScopeInstance.content() }
}
