package raven.compose.foundation.lazy

import kotlin.math.roundToInt

/*
 * The lazy list window: the arithmetic of which items a LazyColumn or LazyRow composes, and where,
 * with no widgets in it. Everything is in dp along the list's axis, and an item's place is the sum
 * of the sizes before it and a gap after each. Sizes come from a [LazySizes] cache, by key.
 */

/** The size an item never measured counts as, before any item is. */
internal const val LazyDefaultItemSize: Float = 48f

/** Items' sizes along the axis, by key: those measured, and the estimate for the others. */
internal class LazySizes {
    private val sizes = HashMap<Any, Float>()
    private var sum = 0.0

    /** The average of the sizes measured, [LazyDefaultItemSize] before any is. */
    val estimate: Float get() = if (sizes.isEmpty()) LazyDefaultItemSize else (sum / sizes.size).toFloat()

    val measuredCount: Int get() = sizes.size

    fun measured(key: Any): Float? = sizes[key]

    fun sizeOf(key: Any): Float = sizes[key] ?: estimate

    /** Records [key]'s size; true when it differs from what was known. */
    fun put(key: Any, size: Float): Boolean {
        val before = sizes.put(key, size)
        if (before == size) return false
        sum += size - (before ?: 0f)
        return true
    }

    /** Forgets the sizes of keys no longer in the list, once there are many more of them than items. */
    fun retainOnly(keys: Set<Any>) {
        if (sizes.size <= 2 * keys.size + 64) return
        val iterator = sizes.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in keys) {
                sum -= entry.value
                iterator.remove()
            }
        }
    }
}

/** Where the list's start is: [offset] dp past the start of the item at [index] (negative in the gap before it). */
internal data class LazyAnchor(val index: Int, val offset: Float)

/** One child of the list's scroll box: empty space standing in for items, or an item. */
internal sealed interface LazySlot {
    /** The items [from] to [to], inclusive, not composed, [size] dp in all with the gaps between them. */
    data class Space(val from: Int, val to: Int, val size: Float) : LazySlot

    data class Item(val index: Int) : LazySlot
}

/** An item in view, as the window places it: [offset] from the viewport's start, and [size]. */
internal data class LazyWindowItem(val index: Int, val key: Any, val offset: Float, val size: Float)

/**
 * What the list composes: its scroll box's children ([slots], in order), the [offset] the box
 * should be at, the [anchor] that offset keeps (moved when the offset is clamped), the first item
 * in view and how far it is scrolled past, and the items in view.
 */
internal data class LazyWindow(
    val slots: List<LazySlot>,
    val offset: Float,
    val anchor: LazyAnchor,
    val firstVisibleIndex: Int,
    val firstVisibleOffset: Float,
    val visibleItems: List<LazyWindowItem>,
    val totalSize: Float,
    val viewport: Float,
) {
    companion object {
        val Empty: LazyWindow = LazyWindow(emptyList(), 0f, LazyAnchor(0, 0f), 0, 0f, emptyList(), 0f, 0f)
    }
}

/** The places of [count] items whose sizes [sizeOf] gives, [gap] dp apart. */
internal class LazyPlaces(val count: Int, val gap: Float, sizeOf: (Int) -> Float) {
    private val sizes = FloatArray(count) { sizeOf(it) }
    private val starts = FloatArray(count).also { starts ->
        var at = 0f
        for (i in 0 until count) {
            starts[i] = at
            at += sizes[i] + gap
        }
    }

    fun size(index: Int): Float = sizes[index]

    fun start(index: Int): Float = starts[index]

    fun end(index: Int): Float = starts[index] + sizes[index]

    /** From the first item's start to the last's end. */
    val total: Float = if (count == 0) 0f else end(count - 1)

    /** The first item ending past [position]: the one at it, or after the gap it is in; the last past the end. */
    fun itemAt(position: Float): Int {
        var low = 0
        var high = count - 1
        while (low < high) {
            val mid = (low + high) / 2
            if (end(mid) > position) high = mid else low = mid + 1
        }
        return low
    }

    /** The anchor of [position]. */
    fun anchorAt(position: Float): LazyAnchor = itemAt(position).let { LazyAnchor(it, position - start(it)) }

    /**
     * The space standing in for the items [from] to [to], in whole dp: Raven keeps a scroll box's
     * offset on whole dp, so items of whole sizes stay on them below an estimated Spacer.
     */
    fun space(from: Int, to: Int): LazySlot.Space = LazySlot.Space(from, to, (end(to) - start(from)).roundToInt().toFloat())
}

/**
 * The window for [count] items keyed by [keyOf], sized by [sizes], [gap] dp apart, in a viewport
 * [viewport] dp long whose start is at [anchor]: the items in view and [beyond] more past each edge
 * are composed, and [pinned] (the item holding the focus) wherever it is.
 */
internal fun lazyListWindow(
    count: Int,
    keyOf: (Int) -> Any,
    sizes: LazySizes,
    gap: Float,
    anchor: LazyAnchor,
    viewport: Float,
    pinned: Int? = null,
    beyond: Int = 1,
): LazyWindow {
    if (count == 0) return LazyWindow.Empty.copy(viewport = viewport)
    val places = LazyPlaces(count, gap) { sizes.sizeOf(keyOf(it)) }
    val index = anchor.index.coerceIn(0, count - 1)
    val wanted = places.start(index) + anchor.offset
    val offset = wanted.coerceIn(0f, (places.total - viewport).coerceAtLeast(0f))
    // Clamped, the start is somewhere else; a gap's offset stays with the item after it.
    val kept = if (offset == wanted) LazyAnchor(index, anchor.offset) else places.anchorAt(offset)

    val first = places.itemAt(offset)
    var last = first
    while (last + 1 < count && places.start(last + 1) < offset + viewport) last++
    val from = (first - beyond).coerceAtLeast(0)
    val to = (last + beyond).coerceAtMost(count - 1)

    val slots = ArrayList<LazySlot>(to - from + 5)
    fun spaceBetween(start: Int, end: Int) {
        if (start > end) return
        val pin = pinned?.takeIf { it in start..end }
        if (pin == null) {
            slots += places.space(start, end)
        } else {
            if (pin > start) slots += places.space(start, pin - 1)
            slots += LazySlot.Item(pin)
            if (pin < end) slots += places.space(pin + 1, end)
        }
    }
    spaceBetween(0, from - 1)
    for (i in from..to) slots += LazySlot.Item(i)
    spaceBetween(to + 1, count - 1)

    val visible = (first..last).map { LazyWindowItem(it, keyOf(it), places.start(it) - offset, places.size(it)) }
    return LazyWindow(slots, offset, kept, first, offset - places.start(first), visible, places.total, viewport)
}
