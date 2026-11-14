package raven.compose.foundation.lazy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.HorizontalScroll
import raven.compose.foundation.layout.VerticalScroll
import raven.compose.ui.Alignment
import raven.cinterop.R_ROLE_NONE
import raven.cinterop.R_WIDGET_OVERLAY
import raven.compose.foundation.layout.fillMaxHeight
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.ui.ChildKind
import raven.compose.ui.Modifier
import raven.compose.ui.PanelLayout
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind

/** What an item of a [LazyColumn] or [LazyRow] is composed in. */
public interface LazyItemScope

private object LazyItemScopeInstance : LazyItemScope

/** The DSL of [LazyColumn] and [LazyRow]: the items, in order. */
public interface LazyListScope {
    /** One item. A [key] keeps it, and what it holds, when the items around it change. */
    public fun item(key: Any? = null, content: @Composable LazyItemScope.() -> Unit)

    /** [count] items, each composed by [itemContent] with its index. */
    public fun items(count: Int, key: ((index: Int) -> Any)? = null, itemContent: @Composable LazyItemScope.(index: Int) -> Unit)
}

/** An item for each of [items]. */
public inline fun <T> LazyListScope.items(
    items: List<T>,
    noinline key: ((item: T) -> Any)? = null,
    crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit,
): Unit = items(items.size, key?.let { { index: Int -> it(items[index]) } }) { itemContent(items[it]) }

/** An item for each of [items], with its index. */
public inline fun <T> LazyListScope.itemsIndexed(
    items: List<T>,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    crossinline itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
): Unit = items(items.size, key?.let { { index: Int -> it(index, items[index]) } }) { itemContent(it, items[it]) }

/** One item as the DSL gave it: its key, null for its place, and its content. */
private class LazyItem(val key: Any?, val content: @Composable LazyItemScope.() -> Unit)

private class LazyListContent : LazyListScope {
    val items = mutableListOf<LazyItem>()

    override fun item(key: Any?, content: @Composable LazyItemScope.() -> Unit) {
        items += LazyItem(key, content)
    }

    override fun items(count: Int, key: ((index: Int) -> Any)?, itemContent: @Composable LazyItemScope.(index: Int) -> Unit) {
        require(count >= 0) { "count must not be negative, not $count" }
        repeat(count) { index -> items += LazyItem(key?.invoke(index)) { itemContent(index) } }
    }
}

/**
 * Composes the items [content] declares. A keyed item is composed in a group of its key, so when
 * the items move the composition moves its widgets with `Rwidget_move_child`, rather than
 * composing each again; an item without one is known by its place.
 */
@Composable
private fun LazyItems(vertical: Boolean, content: LazyListScope.() -> Unit) {
    val items = LazyListContent().apply(content).items
    val keys = HashSet<Any>()
    for (item in items) {
        if (item.key != null) require(keys.add(item.key)) { "the key ${item.key} is used by two items: keys must be unique" }
    }
    for ((index, item) in items.withIndex()) {
        key(item.key ?: PlaceKey(index)) { LazyItemBox(vertical) { item.content(LazyItemScopeInstance) } }
    }
}

/**
 * The internal Box each item is in, as each item of Compose's lazy layouts is a node of its own:
 * where it starts is where the item starts, for [LazyListState]. It fills the list across.
 */
private val LazyItemWidget = WidgetDescriptor("LazyItem", R_WIDGET_OVERLAY, ChildKind.Many, role = R_ROLE_NONE)

@Composable
private fun LazyItemBox(vertical: Boolean, content: @Composable () -> Unit) {
    RavenWidget(LazyItemWidget, if (vertical) Modifier.fillMaxWidth() else Modifier.fillMaxHeight(), {
        bind(PanelLayout.Box)
    }) { content() }
}

/** The key of an item given none: its place, which can't be equal to a key the app gives. */
private data class PlaceKey(val index: Int)

/**
 * A scrolling column of the items [content] declares, with Compose's DSL: `items(list, key = { it.id })
 * { item -> }`. [state] holds its position. In phase 3 every item is composed, whether shown or not: it is a [VerticalScroll]
 * holding them, so a list of thousands of rows wants [raven.compose.foundation.ListView].
 */
@Composable
public fun LazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: LazyListScope.() -> Unit,
) {
    VerticalScroll(modifier, state, verticalArrangement, horizontalAlignment) { LazyItems(vertical = true, content) }
}

/** A scrolling row of the items [content] declares, as [LazyColumn] is a column. */
@Composable
public fun LazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: LazyListScope.() -> Unit,
) {
    HorizontalScroll(modifier, state, horizontalArrangement, verticalAlignment) { LazyItems(vertical = false, content) }
}
