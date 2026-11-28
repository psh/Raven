package raven.compose.foundation.lazy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.HorizontalScroll
import raven.compose.foundation.layout.Spacer
import raven.compose.foundation.layout.height
import raven.compose.foundation.layout.width
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
import raven.compose.ui.unit.dp

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

/** [count] items from [start], as one call of the DSL gave them. */
private class LazyInterval(
    val start: Int,
    val count: Int,
    val key: ((index: Int) -> Any)?,
    val content: @Composable LazyItemScope.(index: Int) -> Unit,
)

/**
 * The items [content] declares, as intervals rather than an object each, so ten thousand of them
 * cost little; each item's key (its place without one) and its index by key.
 */
internal class LazyListItems(content: LazyListScope.() -> Unit) : LazyListScope {
    private val intervals = ArrayList<LazyInterval>()

    var count: Int = 0
        private set

    override fun item(key: Any?, content: @Composable LazyItemScope.() -> Unit) {
        intervals += LazyInterval(count, 1, key?.let { k -> { _: Int -> k } }) { content() }
        count++
    }

    override fun items(count: Int, key: ((index: Int) -> Any)?, itemContent: @Composable LazyItemScope.(index: Int) -> Unit) {
        require(count >= 0) { "count must not be negative, not $count" }
        if (count == 0) return
        intervals += LazyInterval(this.count, count, key, itemContent)
        this.count += count
    }

    private val indexOfKey = HashMap<Any, Int>()

    init {
        content()
        for (index in 0 until count) {
            val key = keyOf(index)
            require(indexOfKey.put(key, index) == null) { "the key $key is used by two items: keys must be unique" }
        }
    }

    private fun intervalOf(index: Int): LazyInterval {
        var low = 0
        var high = intervals.size - 1
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (intervals[mid].start <= index) low = mid else high = mid - 1
        }
        return intervals[low]
    }

    /** The item's key, or its place when the DSL gave none. */
    fun keyOf(index: Int): Any {
        val interval = intervalOf(index)
        return interval.key?.invoke(index - interval.start) ?: PlaceKey(index)
    }

    fun indexOf(key: Any): Int? = indexOfKey[key]

    val keys: Set<Any> get() = indexOfKey.keys

    @Composable
    fun Content(index: Int) {
        val interval = intervalOf(index)
        interval.content(LazyItemScopeInstance, index - interval.start)
    }
}

/** The key of the [n]th Spacer of a list's scroll box. */
private data class SpaceKey(val n: Int)

/**
 * Composes [window]'s slots: a Spacer for the items not composed, and each item composed in a
 * group of its key, in its Box, so when the items move the composition moves its widgets with
 * `Rwidget_move_child`. An item's `rememberSaveable` is kept by [holder] under its key while it is
 * scrolled away.
 */
@Composable
private fun LazySlots(vertical: Boolean, items: LazyListItems, window: LazyWindow, holder: SaveableStateHolder) {
    var spaces = 0
    for (slot in window.slots) {
        // One keyed group a child, so the composition can move them among each other.
        val slotKey = when (slot) {
            is LazySlot.Space -> SpaceKey(spaces++)
            is LazySlot.Item -> items.keyOf(slot.index)
        }
        key(slotKey) {
            when (slot) {
                is LazySlot.Space -> Spacer(if (vertical) Modifier.height(slot.size.dp) else Modifier.width(slot.size.dp))
                is LazySlot.Item -> holder.SaveableStateProvider(slotKey) { LazyItemBox(vertical) { items.Content(slot.index) } }
            }
        }
    }
}

/** A list over [state]: its items, read again whenever [content] reads state that changes, and its window. */
@Composable
private fun LazyList(
    vertical: Boolean,
    state: LazyListState,
    spacing: Float,
    content: LazyListScope.() -> Unit,
    scroll: @Composable (@Composable () -> Unit) -> Unit,
) {
    val latest = rememberUpdatedState(content)
    val items by remember { derivedStateOf { LazyListItems(latest.value) } }
    val holder = rememberSaveableStateHolder()
    val owner = remember { Any() }
    val window = state.windowFor(items, spacing)
    SideEffect { state.composed(owner, items, window, spacing) }
    DisposableEffect(state) { onDispose { state.released(owner) } }
    scroll { LazySlots(vertical, items, window, holder) }
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
 * { item -> }`. [state] holds its position. Only the items in view are composed, and one past each
 * edge, so a list of thousands of rows is cheap: an item scrolled away is disposed, its `remember`
 * lost and its `rememberSaveable` kept under its key. Items are measured once composed; one never
 * measured counts as the average of those that were. For rows of text only, Raven's
 * [raven.compose.foundation.ListView] draws them without a widget each.
 */
@Composable
public fun LazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: LazyListScope.() -> Unit,
) {
    LazyList(vertical = true, state, verticalArrangement.spacing.value, content) { slots ->
        VerticalScroll(modifier, state, verticalArrangement, horizontalAlignment) { slots() }
    }
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
    LazyList(vertical = false, state, horizontalArrangement.spacing.value, content) { slots ->
        HorizontalScroll(modifier, state, horizontalArrangement, verticalAlignment) { slots() }
    }
}
