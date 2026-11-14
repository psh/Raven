package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rlist_view_scroll_to_item
import raven.cinterop.Rscroll_box_get_offset
import raven.cinterop.Rscroll_box_scroll_to_end
import raven.cinterop.Rscroll_box_set_offset
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwidget_is_valid
import raven.compose.ui.WidgetFollower
import raven.compose.ui.layoutWarningSink
import kotlin.math.roundToInt

/** A state that follows one scroll box: attached, read once a frame, and detached. */
internal interface ScrollTarget {
    /** Takes [widget], scrolling along [vertical]; false when the state already follows another. */
    fun attach(widget: Rid, vertical: Boolean): Boolean

    fun detach(widget: Rid)

    /** Applies a position asked for before, once the box is in a window, then reads where it is. */
    fun follow(widget: Rid)
}

/** Has a scroll box's node follow its state's [target], which can change from one composition to the next. */
internal class ScrollFollower(private val vertical: Boolean) : WidgetFollower {
    private var widget: Rid? = null
    private var attached: ScrollTarget? = null

    var target: ScrollTarget? = null
        set(value) {
            if (value === field) return
            widget?.let { box ->
                attached?.detach(box)
                attached = value?.takeIf { it.attach(box, vertical) }
            }
            field = value
        }

    override fun attach(widget: Rid) {
        this.widget = widget
        attached = target?.takeIf { it.attach(widget, vertical) }
    }

    override fun follow(widget: Rid) {
        attached?.follow(widget)
    }

    override fun detach(widget: Rid) {
        attached?.detach(widget)
        attached = null
        this.widget = null
    }
}

/** The scroll box's offset along its axis, in dp. */
internal fun scrollOffset(box: Rid, vertical: Boolean): Float = memScoped {
    val x = alloc<FloatVar>()
    val y = alloc<FloatVar>()
    Rscroll_box_get_offset(box, x.ptr, y.ptr)
    if (vertical) y.value else x.value
}

internal fun setScrollOffset(box: Rid, vertical: Boolean, offset: Float) =
    if (vertical) Rscroll_box_set_offset(box, 0f, offset) else Rscroll_box_set_offset(box, offset, 0f)

/** Whether [box] can be scrolled now: Raven lays out its window first, which a box in none has not. */
internal fun canScroll(box: Rid): Boolean = Rwidget_is_valid(box) == R_TRUE && Rwidget_get_window(box) != R_INVALID_ID

/** The warning for a state given to a second scrollable. */
internal fun warnSecondScrollable(state: String) =
    layoutWarningSink("$state: a state follows one scrollable at most, so only the first uses it")

/**
 * The scroll position of a `VerticalScroll` or `HorizontalScroll`, as Compose's: [value] is the
 * offset in dp, read once a frame since Raven reports no scrolling. A position asked for before the
 * box is shown is applied once it is. Remember it with [rememberScrollState], which saves it.
 */
@Stable
public class ScrollState(initial: Int) : ScrollTarget {
    /** The offset in dp, along the box's axis. */
    public var value: Int by mutableIntStateOf(initial)
        private set

    private var box: Rid? = null
    private var vertical = true

    // What was asked for while it couldn't be applied: an offset, or the end.
    private var pending: Int? = initial.takeIf { it > 0 }
    private var pendingEnd = false

    /** Scrolls to [value] dp, which Raven clamps to the content, and returns how far it moved. */
    public suspend fun scrollTo(value: Int): Float {
        val box = box?.takeIf(::canScroll) ?: run {
            pending = value
            pendingEnd = false
            return 0f
        }
        val before = this.value
        setScrollOffset(box, vertical, value.toFloat())
        read(box)
        return (this.value - before).toFloat()
    }

    /** As [scrollTo]: Raven doesn't animate scrolling, so it jumps. */
    public suspend fun animateScrollTo(value: Int): Float = scrollTo(value)

    /** Scrolls to the end of the content (`Rscroll_box_scroll_to_end`), a compose-raven extension. */
    public suspend fun scrollToEnd() {
        val box = box?.takeIf(::canScroll) ?: run {
            pendingEnd = true
            pending = null
            return
        }
        Rscroll_box_scroll_to_end(box)
        read(box)
    }

    override fun attach(widget: Rid, vertical: Boolean): Boolean {
        if (box != null && box != widget) {
            warnSecondScrollable("ScrollState")
            return false
        }
        box = widget
        this.vertical = vertical
        return true
    }

    override fun detach(widget: Rid) {
        if (box != widget) return
        // A box that comes back starts where this one was.
        pending = value.takeIf { it > 0 }
        box = null
    }

    override fun follow(widget: Rid) {
        if (!canScroll(widget)) return
        pending?.let { setScrollOffset(widget, vertical, it.toFloat()) }
        if (pendingEnd) Rscroll_box_scroll_to_end(widget)
        pending = null
        pendingEnd = false
        read(widget)
    }

    private fun read(widget: Rid) {
        val offset = scrollOffset(widget, vertical).roundToInt()
        if (offset != value) value = offset
    }

    public companion object {
        /** Saves the offset. */
        public val Saver: Saver<ScrollState, Int> = Saver(save = { it.value }, restore = { ScrollState(it) })
    }
}

/** A [ScrollState] at [initial] dp, saved and restored wherever saveable state is kept. */
@Composable
public fun rememberScrollState(initial: Int = 0): ScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(initial) }

/**
 * What an app can ask of a `ListView`'s position: [revealItem]. Raven can't report the list's
 * position, so it isn't saved; on a state not attached to a list, [revealItem] does nothing.
 */
@Stable
public class ListViewState internal constructor() {
    internal var list: Rid? = null

    /** Scrolls just enough to show the row at [index] (`Rlist_view_scroll_to_item`). */
    public suspend fun revealItem(index: Int) {
        list?.takeIf { Rwidget_is_valid(it) == R_TRUE }?.let { Rlist_view_scroll_to_item(it, index) }
    }
}

/** A [ListViewState]. */
@Composable
public fun rememberListViewState(): ListViewState = remember { ListViewState() }

/** Points a [ListViewState] at its list while the list's node holds it. */
internal class ListViewFollower : WidgetFollower {
    private var widget: Rid? = null

    var state: ListViewState? = null
        set(value) {
            if (value === field) return
            widget?.let { list ->
                if (field?.list == list) field?.list = null
                value?.attachTo(list)
            }
            field = value
        }

    private fun ListViewState.attachTo(list: Rid) {
        if (this.list != null && this.list != list) warnSecondScrollable("ListViewState") else this.list = list
    }

    override fun attach(widget: Rid) {
        this.widget = widget
        state?.attachTo(widget)
    }

    override fun follow(widget: Rid) = Unit

    override fun detach(widget: Rid) {
        if (state?.list == widget) state?.list = null
        this.widget = null
    }
}
