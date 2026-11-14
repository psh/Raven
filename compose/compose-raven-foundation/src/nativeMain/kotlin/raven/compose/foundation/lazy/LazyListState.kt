package raven.compose.foundation.lazy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import raven.cinterop.Rid
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_rect
import raven.compose.foundation.ScrollTarget
import raven.compose.foundation.canScroll
import raven.compose.foundation.scrollOffset
import raven.compose.foundation.setScrollOffset
import raven.compose.foundation.warnSecondScrollable
import kotlin.math.roundToInt

/**
 * The scroll position of a `LazyColumn` or `LazyRow`, as Compose's: the first item showing, at
 * least in part, and how far it is scrolled past, in dp, read once a frame since Raven reports no
 * scrolling. Every item is composed in phase 3, so their places are known. A position asked for
 * before the list is shown is applied once it is. Remember it with [rememberLazyListState], which
 * saves it.
 */
@Stable
public class LazyListState(firstVisibleItemIndex: Int = 0, firstVisibleItemScrollOffset: Int = 0) : ScrollTarget {
    public var firstVisibleItemIndex: Int by mutableIntStateOf(firstVisibleItemIndex)
        private set

    public var firstVisibleItemScrollOffset: Int by mutableIntStateOf(firstVisibleItemScrollOffset)
        private set

    private var box: Rid? = null
    private var vertical = true
    private var pending: Pair<Int, Int>? = (firstVisibleItemIndex to firstVisibleItemScrollOffset).takeIf { it != (0 to 0) }

    /** Puts the item at [index] at the top (or the start), [scrollOffset] dp past it, as far as the content allows. */
    public suspend fun scrollToItem(index: Int, scrollOffset: Int = 0) {
        require(index >= 0) { "an item's index is not negative, not $index" }
        pending = index to scrollOffset
        box?.let(::follow)
    }

    /** As [scrollToItem]: Raven doesn't animate scrolling, so it jumps. */
    public suspend fun animateScrollToItem(index: Int, scrollOffset: Int = 0): Unit = scrollToItem(index, scrollOffset)

    override fun attach(widget: Rid, vertical: Boolean): Boolean {
        if (box != null && box != widget) {
            warnSecondScrollable("LazyListState")
            return false
        }
        box = widget
        this.vertical = vertical
        return true
    }

    override fun detach(widget: Rid) {
        if (box != widget) return
        pending = (firstVisibleItemIndex to firstVisibleItemScrollOffset).takeIf { it != (0 to 0) }
        box = null
    }

    override fun follow(widget: Rid) {
        if (!canScroll(widget)) return
        val (index, offset) = pending ?: (null to 0)
        // Applied once the item is there; until then it waits.
        if (index != null && index < Rwidget_get_child_count(widget)) {
            val start = itemStart(widget, Rwidget_get_child(widget, index)) - start(widget) + scrollOffset(widget, vertical)
            setScrollOffset(widget, vertical, start + offset)
            pending = null
        }
        read(widget)
    }

    private fun read(widget: Rid) {
        val top = start(widget)
        val count = Rwidget_get_child_count(widget)
        var first = 0
        var past = 0f
        for (i in 0 until count) {
            val item = Rwidget_get_child(widget, i)
            val (itemTop, itemEnd) = itemSpan(item)
            if (itemEnd > top) {
                first = i
                past = (top - itemTop).coerceAtLeast(0f)
                break
            }
        }
        if (first != firstVisibleItemIndex) firstVisibleItemIndex = first
        val scrolled = past.roundToInt()
        if (scrolled != firstVisibleItemScrollOffset) firstVisibleItemScrollOffset = scrolled
    }

    private fun start(widget: Rid): Float = itemStart(widget, widget)

    @Suppress("UNUSED_PARAMETER")
    private fun itemStart(box: Rid, item: Rid): Float = itemSpan(item).first

    /** Where [item] starts and ends along the axis, in the window. */
    private fun itemSpan(item: Rid): Pair<Float, Float> = memScoped {
        val x = alloc<FloatVar>()
        val y = alloc<FloatVar>()
        val w = alloc<FloatVar>()
        val h = alloc<FloatVar>()
        Rwidget_get_rect(item, x.ptr, y.ptr, w.ptr, h.ptr)
        if (vertical) y.value to y.value + h.value else x.value to x.value + w.value
    }

    public companion object {
        /** Saves the first visible item and how far it is scrolled past. */
        public val Saver: Saver<LazyListState, List<Int>> = Saver(
            save = { listOf(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) },
            restore = { LazyListState(it[0], it[1]) },
        )
    }
}

/** A [LazyListState], saved and restored wherever saveable state is kept. */
@Composable
public fun rememberLazyListState(initialFirstVisibleItemIndex: Int = 0, initialFirstVisibleItemScrollOffset: Int = 0): LazyListState =
    rememberSaveable(saver = LazyListState.Saver) { LazyListState(initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset) }
