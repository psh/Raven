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
import raven.cinterop.R_INVALID_ID
import raven.cinterop.Rid
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_parent
import raven.cinterop.Rwidget_get_rect
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_get_focused_widget
import raven.compose.foundation.ScrollTarget
import raven.compose.foundation.canScroll
import raven.compose.foundation.scrollOffset
import raven.compose.foundation.setScrollOffset
import raven.compose.foundation.warnSecondScrollable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The scroll position of a `LazyColumn` or `LazyRow`, as Compose's: the first item showing, at
 * least in part, and how far it is scrolled past, in dp, read once a frame since Raven reports no
 * scrolling. The list composes only the items in view, so the places of the others come from
 * their sizes as last measured, or the average for those never measured. The place is kept by the
 * first visible item's key: when items come or go above it, or one above turns out a different size,
 * it stays where it was on screen. A position asked for before the list is shown is applied once it
 * is. Remember it with [rememberLazyListState], which saves it.
 */
@Stable
public class LazyListState(firstVisibleItemIndex: Int = 0, firstVisibleItemScrollOffset: Int = 0) : ScrollTarget {
    public var firstVisibleItemIndex: Int by mutableIntStateOf(firstVisibleItemIndex)
        private set

    public var firstVisibleItemScrollOffset: Int by mutableIntStateOf(firstVisibleItemScrollOffset)
        private set

    // Where the viewport starts, by index and, once known, by key.
    private var anchor = LazyAnchor(firstVisibleItemIndex, firstVisibleItemScrollOffset.toFloat())
    private var anchorKey: Any? = null

    // The item holding the focus, kept composed while it is scrolled out of view.
    private var pinnedKey: Any? = null
    private var viewport = 0f
    private val sizes = LazySizes()

    // Moved on when the list should compose a new window.
    private var generation by mutableIntStateOf(0)

    /** What the list last applied: its items, its window and its gap, which its scroll box's children are. */
    private class Composed(val items: LazyListItems, val window: LazyWindow, val gap: Float)

    private var composed: Composed? = null
    private var owner: Any? = null
    private var box: Rid? = null
    private var vertical = true

    // The box's offset as this state left it, to tell the user's scrolling; null until placed.
    private var placedOffset: Float? = null

    /** Puts the item at [index] at the top (or the start), [scrollOffset] dp past it, as far as the content allows. */
    public suspend fun scrollToItem(index: Int, scrollOffset: Int = 0) {
        jumpTo(index, scrollOffset)
    }

    /** As [scrollToItem]: Raven doesn't animate scrolling, so it jumps. */
    public suspend fun animateScrollToItem(index: Int, scrollOffset: Int = 0): Unit = scrollToItem(index, scrollOffset)

    /**
     * Anchors the list at the item, and scrolls the box to its estimated place now: the next frame
     * composes the items there and puts it exactly at the start.
     */
    internal fun jumpTo(index: Int, scrollOffset: Int) {
        require(index >= 0) { "an item's index is not negative, not $index" }
        anchor = LazyAnchor(index, scrollOffset.toFloat())
        val composed = composed
        anchorKey = composed?.items?.takeIf { index < it.count }?.keyOf(index)
        if (firstVisibleItemIndex != index) firstVisibleItemIndex = index
        if (firstVisibleItemScrollOffset != scrollOffset) firstVisibleItemScrollOffset = scrollOffset
        generation++
        val box = box?.takeIf(::canScroll) ?: return
        if (composed == null) return
        setScrollOffset(box, vertical, windowOf(composed.items, composed.gap).offset)
        placedOffset = scrollOffset(box, vertical)
    }

    /** How many items the list has, as last composed. */
    internal val itemCount: Int get() = composed?.items?.count ?: 0

    // -----------------------------------------------------------------------------------------
    // The list's side: the window to compose, and what was composed.

    /** The window the list composes [items] in, [gap] dp apart; read in composition, so a new one recomposes it. */
    internal fun windowFor(items: LazyListItems, gap: Float): LazyWindow {
        generation
        return windowOf(items, gap)
    }

    private fun windowOf(items: LazyListItems, gap: Float): LazyWindow {
        val index = anchorKey?.let(items::indexOf) ?: anchor.index
        return lazyListWindow(items.count, items::keyOf, sizes, gap, LazyAnchor(index, anchor.offset), viewport, pinnedKey?.let(items::indexOf))
    }

    /** The list [owner] applied [window] over [items]: its box's children are the window's slots now. */
    internal fun composed(owner: Any, items: LazyListItems, window: LazyWindow, gap: Float) {
        if (this.owner != null && this.owner !== owner) return
        this.owner = owner
        composed = Composed(items, window, gap)
        sizes.retainOnly(items.keys)
        // With no items yet, the position waits for them.
        if (items.count > 0) {
            anchor = window.anchor
            anchorKey = items.keyOf(window.anchor.index)
        }
    }

    /** The list [owner] left the composition. */
    internal fun released(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        composed = null
    }

    // -----------------------------------------------------------------------------------------
    // The scroll box's side.

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
        box = null
        placedOffset = null
    }

    /** At the start of the frame: the user's scrolling, the viewport, the focus and the sizes, for the window to compose. */
    override fun follow(widget: Rid) {
        if (canScroll(widget)) observe(widget)
    }

    /** Once the frame's window is composed and laid out: the anchor put exactly at the start, and the position read. */
    override fun settle(widget: Rid) {
        if (!canScroll(widget)) return
        val composed = composed ?: return
        val spans = spans(widget, composed)
        val target = spans?.firstOrNull { it.slot == LazySlot.Item(anchor.index) }?.let { it.start + anchor.offset }
            ?: windowOf(composed.items, composed.gap).offset
        if (abs(target - scrollOffset(widget, vertical)) >= 0.5f) setScrollOffset(widget, vertical, target)
        val offset = scrollOffset(widget, vertical)
        placedOffset = offset
        // Clamped to the content: the start is somewhere else now.
        if (abs(offset - target) >= 0.5f) anchorAt(offset, composed, spans)
        readFirstVisible(offset, composed, spans)
        // What this frame composed is measured, for the next frame.
        observe(widget)
    }

    private fun observe(widget: Rid) {
        val composed = composed ?: return
        var changed = false
        val size = span(widget).let { it.second - it.first }
        if (size != viewport) {
            viewport = size
            changed = true
        }
        val spans = spans(widget, composed)
        spans?.forEach { span ->
            val slot = span.slot
            if (slot is LazySlot.Item && sizes.put(composed.items.keyOf(slot.index), span.end - span.start)) changed = true
        }
        val offset = scrollOffset(widget, vertical)
        val placed = placedOffset
        if (placed != null && abs(offset - placed) >= 0.5f) {
            // The user's scrolling (or Raven's, bringing something into view).
            anchorAt(offset, composed, spans)
            placedOffset = offset
            readFirstVisible(offset, composed, spans)
            changed = true
        }
        val focused = focusedKey(widget, composed)
        if (focused != pinnedKey) {
            pinnedKey = focused
            changed = true
        }
        if (changed && windowOf(composed.items, composed.gap).slots != composed.window.slots) generation++
    }

    /** One of the box's children: its slot, and where it starts and ends in the content. */
    private class Span(val slot: LazySlot, val start: Float, val end: Float)

    /** The box's children as their slots, null when they aren't the slots last composed. */
    private fun spans(widget: Rid, composed: Composed): List<Span>? {
        val slots = composed.window.slots
        if (Rwidget_get_child_count(widget) != slots.size) return null
        val origin = span(widget).first - scrollOffset(widget, vertical)
        return slots.mapIndexed { i, slot ->
            val (start, end) = span(Rwidget_get_child(widget, i))
            Span(slot, start - origin, end - origin)
        }
    }

    /** Anchors the list at [offset] in the content: an item composed there, or a place estimated within a Spacer. */
    private fun anchorAt(offset: Float, composed: Composed, spans: List<Span>?) {
        val items = composed.items
        if (items.count == 0) return
        val span = spans?.firstOrNull { it.end > offset } ?: spans?.lastOrNull()
        val at = when (val slot = span?.slot) {
            is LazySlot.Item -> LazyAnchor(slot.index, offset - span.start)
            is LazySlot.Space -> {
                // The Spacer was sized by the estimates of when it was composed: the place within it is near enough.
                val places = LazyPlaces(items.count, composed.gap) { sizes.sizeOf(items.keyOf(it)) }
                val position = places.start(slot.from) + (offset - span.start).coerceAtLeast(0f)
                val index = places.itemAt(position).coerceIn(slot.from, slot.to)
                LazyAnchor(index, position - places.start(index))
            }
            null -> LazyPlaces(items.count, composed.gap) { sizes.sizeOf(items.keyOf(it)) }.anchorAt(offset)
        }
        anchor = at
        anchorKey = items.keyOf(at.index.coerceIn(0, items.count - 1))
    }

    /** The first item composed that ends past [offset], and how far it is scrolled past. */
    private fun readFirstVisible(offset: Float, composed: Composed, spans: List<Span>?) {
        val span = spans?.firstOrNull { it.slot is LazySlot.Item && it.end > offset }
        val (index, past) = if (span != null) {
            (span.slot as LazySlot.Item).index to (offset - span.start).coerceAtLeast(0f)
        } else {
            val window = windowOf(composed.items, composed.gap)
            window.firstVisibleIndex to window.firstVisibleOffset.coerceAtLeast(0f)
        }
        if (index != firstVisibleItemIndex) firstVisibleItemIndex = index
        val scrolled = past.roundToInt()
        if (scrolled != firstVisibleItemScrollOffset) firstVisibleItemScrollOffset = scrolled
    }

    /** The key of the item holding the window's focus, if one does. */
    private fun focusedKey(widget: Rid, composed: Composed): Any? {
        val window = Rwidget_get_window(widget)
        if (window == R_INVALID_ID) return null
        var focused = Rwindow_get_focused_widget(window)
        while (focused != R_INVALID_ID) {
            val parent = Rwidget_get_parent(focused)
            if (parent == widget) break
            focused = parent
        }
        if (focused == R_INVALID_ID) return null
        val slots = composed.window.slots
        if (Rwidget_get_child_count(widget) != slots.size) return null
        val slot = (0 until slots.size).firstOrNull { Rwidget_get_child(widget, it) == focused }?.let(slots::get)
        return (slot as? LazySlot.Item)?.let { composed.items.keyOf(it.index) }
    }

    /** Where [widget] starts and ends along the axis, in the window. */
    private fun span(widget: Rid): Pair<Float, Float> = memScoped {
        val x = alloc<FloatVar>()
        val y = alloc<FloatVar>()
        val w = alloc<FloatVar>()
        val h = alloc<FloatVar>()
        Rwidget_get_rect(widget, x.ptr, y.ptr, w.ptr, h.ptr)
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
