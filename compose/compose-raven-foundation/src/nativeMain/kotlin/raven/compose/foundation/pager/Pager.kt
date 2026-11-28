package raven.compose.foundation.pager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import raven.cinterop.R_FALSE
import raven.cinterop.R_HORIZONTAL
import raven.cinterop.R_ROLE_NONE
import raven.cinterop.R_TRUE
import raven.cinterop.R_VERTICAL
import raven.cinterop.R_WIDGET_CAROUSEL
import raven.cinterop.R_WIDGET_OVERLAY
import raven.cinterop.ROrientation
import raven.cinterop.Rcarousel_get_page
import raven.cinterop.Rcarousel_get_page_count
import raven.cinterop.Rcarousel_set_arrows_visible
import raven.cinterop.Rcarousel_set_autoplay
import raven.cinterop.Rcarousel_set_indicators_visible
import raven.cinterop.Rcarousel_set_loop
import raven.cinterop.Rcarousel_set_orientation
import raven.cinterop.Rcarousel_set_page
import raven.cinterop.Rid
import raven.cinterop.Rwidget_is_valid
import raven.compose.foundation.warnSecondScrollable
import raven.compose.ui.CarouselPageChanged
import raven.compose.ui.ChildKind
import raven.compose.ui.Modifier
import raven.compose.ui.PanelLayout
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.WidgetFollower
import raven.compose.ui.bind
import kotlin.time.Duration

private val CarouselWidget = WidgetDescriptor("Pager", R_WIDGET_CAROUSEL, ChildKind.Many, events = listOf(CarouselPageChanged))

/** The internal Box each page is in, the size of the pager. */
private val PageWidget = WidgetDescriptor("PagerPage", R_WIDGET_OVERLAY, ChildKind.Many, role = R_ROLE_NONE)

private fun Boolean.toRaven() = if (this) R_TRUE else R_FALSE

private val Orientation = Property<ROrientation> { widget, orientation -> Rcarousel_set_orientation(widget, orientation) }
private val Loop = Property<Boolean> { widget, loop -> Rcarousel_set_loop(widget, loop.toRaven()) }
private val Indicators = Property<Boolean> { widget, visible -> Rcarousel_set_indicators_visible(widget, visible.toRaven()) }
private val Arrows = Property<Boolean> { widget, visible -> Rcarousel_set_arrows_visible(widget, visible.toRaven()) }
private val Autoplay = Property<Duration?> { widget, every ->
    Rcarousel_set_autoplay(widget, every?.takeIf { it.isPositive() }?.inWholeMilliseconds?.div(1000.0) ?: 0.0)
}

/**
 * The page of a [HorizontalPager] or [VerticalPager], as Compose's: [currentPage], the page shown,
 * follows the user (a swipe, the keys, a dot, an arrow) and the autoplay; [scrollToPage] and
 * [animateScrollToPage] go to a page, clamped to the pages. A page asked for before the pager has
 * its pages is shown once it has. Remember it with [rememberPagerState], which saves the page.
 */
@Stable
public class PagerState(currentPage: Int = 0, pageCount: () -> Int) {
    /** How many pages there are, as the lambda given says, reading its state. */
    public val pageCount: Int get() = pageCountProvider()

    internal var pageCountProvider: () -> Int = pageCount

    /** The page shown, from 0. */
    public var currentPage: Int by mutableIntStateOf(currentPage)
        private set

    private var carousel: Rid? = null

    // A page asked for that the pager couldn't show yet, and whether to glide to it.
    private var pending: Pair<Int, Boolean>? = (currentPage to false).takeIf { currentPage > 0 }

    /** Shows [page] at once, clamped to the pages. */
    public suspend fun scrollToPage(page: Int): Unit = move(page, animated = false)

    /** Glides to [page] (`Rcarousel_set_page` animated), clamped to the pages. */
    public suspend fun animateScrollToPage(page: Int): Unit = move(page, animated = true)

    internal fun move(page: Int, animated: Boolean) {
        val target = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (target != currentPage) currentPage = target
        val box = carousel?.takeIf { Rwidget_is_valid(it) == R_TRUE }
        if (box != null && target < Rcarousel_get_page_count(box)) {
            Rcarousel_set_page(box, target, animated.toRaven())
            pending = null
        } else {
            pending = target to animated
        }
    }

    /** The user or the autoplay went to [page]. */
    internal fun shown(page: Int) {
        pending = null
        if (page != currentPage) currentPage = page
    }

    internal fun attach(widget: Rid): Boolean {
        if (carousel != null && carousel != widget) {
            warnSecondScrollable("PagerState")
            return false
        }
        carousel = widget
        return true
    }

    internal fun detach(widget: Rid) {
        if (carousel != widget) return
        carousel = null
        // A pager that comes back starts where this one was.
        pending = (currentPage to false).takeIf { currentPage > 0 }
    }

    /** Once the frame's pages are in: a page asked for is shown, and the page shown is read. */
    internal fun settle(widget: Rid) {
        val count = Rcarousel_get_page_count(widget)
        pending?.let { (page, animated) ->
            // Clamped to the pages the state counts; shown once Raven has that page.
            val target = page.coerceAtMost((pageCount - 1).coerceAtLeast(0))
            if (target < count) {
                Rcarousel_set_page(widget, target, animated.toRaven())
                pending = null
            }
        }
        // Raven keeps the page shown as pages come and go, and clamps it when its page goes.
        if (pending == null && count > 0) Rcarousel_get_page(widget).let { if (it != currentPage) currentPage = it }
    }

    internal companion object {
        /** Saves the page; the count is the composition's again. */
        val Saver: Saver<PagerState, Int> = Saver(save = { it.currentPage }, restore = { PagerState(it) { 0 } })
    }
}

/** A [PagerState] at [initialPage], with [pageCount] pages, saved and restored wherever saveable state is kept. */
@Composable
public fun rememberPagerState(initialPage: Int = 0, pageCount: () -> Int): PagerState =
    rememberSaveable(saver = PagerState.Saver) { PagerState(initialPage, pageCount) }.apply { pageCountProvider = pageCount }

/** Points a [PagerState] at its carousel while the carousel's node holds it. */
private class PagerFollower : WidgetFollower {
    private var widget: Rid? = null
    private var attached: PagerState? = null

    var state: PagerState? = null
        set(value) {
            if (value === field) return
            widget?.let { box ->
                attached?.detach(box)
                attached = value?.takeIf { it.attach(box) }
            }
            field = value
        }

    override fun attach(widget: Rid) {
        this.widget = widget
        attached = state?.takeIf { it.attach(widget) }
    }

    override fun follow(widget: Rid) = Unit

    override fun settle(widget: Rid) {
        attached?.settle(widget)
    }

    override fun detach(widget: Rid) {
        attached?.detach(widget)
        attached = null
        this.widget = null
    }
}

/**
 * Pages shown one at a time side by side, over Raven's Carousel, as Compose's: `content(page)` for
 * each of the [state]'s pages, every page composed, Raven painting only the page shown and the one
 * coming in. The user swipes them with the pointer, a finger or a trackpad, and goes to one with the
 * keys, a dot ([indicators]), an arrow ([arrows], shown under the pointer) or the wheel. [loop] lets
 * the user and the [autoplay] go from the last page to the first; [autoplay], Raven's timer, goes to
 * the next page every so long (null: never), waiting while the pointer or the keyboard is in the pager.
 */
@Composable
public fun HorizontalPager(
    state: PagerState,
    modifier: Modifier = Modifier,
    loop: Boolean = false,
    indicators: Boolean = true,
    arrows: Boolean = true,
    autoplay: Duration? = null,
    content: @Composable (page: Int) -> Unit,
) {
    Pager(R_HORIZONTAL, state, modifier, loop, indicators, arrows, autoplay, content)
}

/** Pages shown one at a time one above the other, as [HorizontalPager] shows them side by side. */
@Composable
public fun VerticalPager(
    state: PagerState,
    modifier: Modifier = Modifier,
    loop: Boolean = false,
    indicators: Boolean = true,
    arrows: Boolean = true,
    autoplay: Duration? = null,
    content: @Composable (page: Int) -> Unit,
) {
    Pager(R_VERTICAL, state, modifier, loop, indicators, arrows, autoplay, content)
}

@Composable
private fun Pager(
    orientation: ROrientation,
    state: PagerState,
    modifier: Modifier,
    loop: Boolean,
    indicators: Boolean,
    arrows: Boolean,
    autoplay: Duration?,
    content: @Composable (page: Int) -> Unit,
) {
    val follower = remember { PagerFollower() }
    RavenWidget(CarouselWidget, modifier, {
        bind(follower)
        set(state) { follower.state = it }
        bind(orientation, Orientation)
        bind(loop, Loop)
        bind(indicators, Indicators)
        bind(arrows, Arrows)
        bind(autoplay, Autoplay)
        bind({ page: Int -> state.shown(page) }, CarouselPageChanged)
    }) {
        repeat(state.pageCount) { page ->
            key(page) {
                RavenWidget(PageWidget, Modifier, { bind(PanelLayout.Box) }) { content(page) }
            }
        }
    }
}
