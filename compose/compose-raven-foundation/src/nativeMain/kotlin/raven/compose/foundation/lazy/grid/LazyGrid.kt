package raven.compose.foundation.lazy.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import raven.cinterop.R_WIDGET_UNIFORM_GRID
import raven.cinterop.Runiform_grid_set_columns
import raven.cinterop.Runiform_grid_set_min_cell_size
import raven.compose.foundation.layout.PanelSpacing
import raven.compose.foundation.layout.VerticalScroll
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.ui.ChildKind
import raven.compose.ui.LayoutAxis
import raven.compose.ui.MainAxis
import raven.compose.ui.Modifier
import raven.compose.ui.PanelLayout
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp

/** How a [LazyVerticalGrid] makes its columns. */
public sealed interface GridCells {
    /** [count] columns of one width, sharing the grid's width. */
    public data class Fixed(val count: Int) : GridCells {
        init {
            require(count > 0) { "a grid has one column at least, not $count" }
        }
    }
}

/** What an item of a [LazyVerticalGrid] is composed in. */
public interface LazyGridItemScope

private object LazyGridItemScopeInstance : LazyGridItemScope

/** The DSL of [LazyVerticalGrid]: the items, in order, as `LazyListScope`'s. */
public interface LazyGridScope {
    /** One item. A [key] keeps it, and what it holds, when the items around it change. */
    public fun item(key: Any? = null, content: @Composable LazyGridItemScope.() -> Unit)

    /** [count] items, each composed by [itemContent] with its index. */
    public fun items(count: Int, key: ((index: Int) -> Any)? = null, itemContent: @Composable LazyGridItemScope.(index: Int) -> Unit)
}

/** An item for each of [items]. */
public inline fun <T> LazyGridScope.items(
    items: List<T>,
    noinline key: ((item: T) -> Any)? = null,
    crossinline itemContent: @Composable LazyGridItemScope.(item: T) -> Unit,
): Unit = items(items.size, key?.let { { index: Int -> it(items[index]) } }) { itemContent(items[it]) }

/** An item for each of [items], with its index. */
public inline fun <T> LazyGridScope.itemsIndexed(
    items: List<T>,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    crossinline itemContent: @Composable LazyGridItemScope.(index: Int, item: T) -> Unit,
): Unit = items(items.size, key?.let { { index: Int -> it(index, items[index]) } }) { itemContent(it, items[it]) }

private class LazyGridItem(val key: Any?, val content: @Composable LazyGridItemScope.() -> Unit)

private class LazyGridContent : LazyGridScope {
    val items = mutableListOf<LazyGridItem>()

    override fun item(key: Any?, content: @Composable LazyGridItemScope.() -> Unit) {
        items += LazyGridItem(key, content)
    }

    override fun items(count: Int, key: ((index: Int) -> Any)?, itemContent: @Composable LazyGridItemScope.(index: Int) -> Unit) {
        require(count >= 0) { "count must not be negative, not $count" }
        repeat(count) { index -> items += LazyGridItem(key?.invoke(index)) { itemContent(index) } }
    }
}

/** The key of an item given none: its place, which can't be equal to a key the app gives. */
private data class PlaceKey(val index: Int)

private val UniformGridWidget = WidgetDescriptor("LazyVerticalGrid", R_WIDGET_UNIFORM_GRID, ChildKind.Many)

private val Columns = Property<GridCells> { widget, cells ->
    when (cells) {
        is GridCells.Fixed -> Runiform_grid_set_columns(widget, cells.count)
    }
}

/** No minimum is a minimum of 0 by 0. */
private val MinCellSize = Property<DpSize?> { widget, size -> Runiform_grid_set_min_cell_size(widget, size?.width?.value ?: 0f, size?.height?.value ?: 0f) }

/**
 * A scrolling grid of the items [content] declares, in [columns], left to right then top to
 * bottom, with Compose's DSL. Raven's UniformGrid gives every cell one size, that of the largest
 * item, at least [minCellSize], and each item fills its cell. [spacing] is the gap between cells,
 * in both directions. As `LazyColumn`, it composes every item in phase 3.
 */
@Composable
public fun LazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    minCellSize: DpSize? = null,
    spacing: Dp = 0.dp,
    content: LazyGridScope.() -> Unit,
) {
    VerticalScroll(modifier) {
        RavenWidget(UniformGridWidget, Modifier.fillMaxWidth(), {
            bind(PanelLayout(LayoutAxis.None, mainAxis = MainAxis.Cells))
            bind(columns, Columns)
            bind(minCellSize, MinCellSize)
            bind(spacing, PanelSpacing)
        }) { LazyGridItems(content) }
    }
}

@Composable
private fun LazyGridItems(content: LazyGridScope.() -> Unit) {
    val items = LazyGridContent().apply(content).items
    val keys = HashSet<Any>()
    for (item in items) {
        if (item.key != null) require(keys.add(item.key)) { "the key ${item.key} is used by two items: keys must be unique" }
    }
    for ((index, item) in items.withIndex()) {
        key(item.key ?: PlaceKey(index)) { item.content(LazyGridItemScopeInstance) }
    }
}
