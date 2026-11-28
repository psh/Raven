package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_TILE_VIEW
import raven.cinterop.Rid
import raven.cinterop.Rtile_view_clear_selection
import raven.cinterop.Rtile_view_refresh
import raven.cinterop.Rtile_view_scroll_to
import raven.cinterop.Rtile_view_set_empty_text
import raven.cinterop.Rtile_view_set_selected
import raven.cinterop.Rtile_view_set_tile_size
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwidget_is_valid
import raven.cinterop.Rwidget_set_attribute
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.TileItemValue
import raven.compose.ui.TileViewActivated
import raven.compose.ui.TileViewContext
import raven.compose.ui.TileViewItem
import raven.compose.ui.TileViewSelection
import raven.compose.ui.TileViewSelectionChanged
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.WidgetFollower
import raven.compose.ui.bind
import raven.compose.ui.graphics.painter.Painter
import raven.compose.ui.graphics.painter.TextureRegion
import raven.compose.ui.rememberErrorReporter
import raven.compose.ui.setTileViewItems
import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp

private val TileViewWidget = WidgetDescriptor(
    "TileView", R_WIDGET_TILE_VIEW, events = listOf(TileViewItem, TileViewSelectionChanged, TileViewActivated, TileViewContext),
)

/**
 * What a [TileView]'s tile shows: its name [text], the [icon] over it, a dimmer second line
 * [detail], and a small [badge] at its top right.
 */
@Immutable
public data class TileContent(val text: String, val icon: Painter? = null, val detail: String? = null, val badge: String? = null)

/** How a [TileView] places its tiles. */
public enum class TileLayout {
    /** A grid of tiles, the icon over the name. */
    Tiles,

    /** One item a line, as wide as the view, the icon on the left. */
    Rows,
}

/**
 * Raven's virtualised grid of tiles, one for each of [items], for collections such as a folder of
 * files: Raven asks for the tiles in view only, while it paints, so ten thousand tiles cost what
 * ten do. [tile] says what an item's tile shows; it isn't `@Composable`, as Raven calls it while
 * painting, reading the latest [items]. Make its painters in composition beforehand.
 *
 * - **The selection** is a controlled set of keys, each item known by its [key], so it survives the
 *   items being reordered: the user's change (a click, Ctrl or Shift and a click, a rubber band,
 *   Ctrl + A, a right click selecting first) calls [onSelectionChange], and one the app doesn't
 *   take reverts. Raven always selects several.
 * - [onActivate] gets a tile double-clicked or Enter pressed on it; [contextMenuItems], given the
 *   tile right-clicked or null for the empty space, lists the menu opened at the pointer.
 * - [tileSize], [iconSize], [spacing] (null: Raven's), [layout] and [emptyText] are Raven's. [state]
 *   reveals items.
 */
@Composable
public fun <T> TileView(
    items: List<T>,
    key: (T) -> Any,
    selection: Set<Any>,
    onSelectionChange: (Set<Any>) -> Unit,
    modifier: Modifier = Modifier,
    tileSize: DpSize = DpSize(112.dp, 112.dp),
    iconSize: Dp = 64.dp,
    spacing: Dp? = null,
    layout: TileLayout = TileLayout.Tiles,
    emptyText: String? = null,
    onActivate: (T) -> Unit = {},
    contextMenuItems: ((T?) -> List<ContextMenuItem>)? = null,
    state: TileViewState = rememberTileViewState(),
    tile: (T) -> TileContent,
) {
    val keys = items.map(key)
    val indexOfKey = HashMap<Any, Int>(keys.size * 2)
    keys.forEachIndexed { index, k -> require(indexOfKey.put(k, index) == null) { "the key $k is used by two items: keys must be unique" } }
    // A key the items haven't is left out.
    val selected = selection.mapNotNullTo(LinkedHashSet()) { indexOfKey[it] }
    val mirror = remember { TileMirror() }
    val follower = remember { TileViewFollower() }
    val reportError = rememberErrorReporter()
    val menu = remember(reportError) { ContextMenu(reportError) }
    DisposableEffect(menu) { onDispose { menu.discard() } }
    val describe: (Int) -> TileItemValue? = describe@{ index ->
        val item = items.getOrNull(index) ?: return@describe null
        val content = tile(item)
        val icon = content.icon
        if (icon != null) mirror.tileView?.let(icon::addViewer)
        TileItemValue(content.text, content.detail, content.badge, icon?.texture ?: R_INVALID_ID, icon?.region ?: TextureRegion.Whole)
    }
    val onContext: (Int, Float, Float) -> Unit = { index, x, y ->
        val entries = contextMenuItems?.invoke(items.getOrNull(index)).orEmpty()
        mirror.tileView?.let { menu.open(entries, Rwidget_get_window(it), x, y) }
    }
    RavenWidget(TileViewWidget, modifier, {
        bind(follower)
        set(state) { follower.state = it }
        bind(describe, TileViewItem)
        // The tiles before the selection, which is of their indices; a copy, so a list changed in
        // place is seen to change.
        set(TileItems(items.toList(), selected)) { mirror.sync(widget, it) }
        bind(selected, TileViewSelection)
        bind({ indices: Set<Int> -> onSelectionChange(indices.mapNotNullTo(LinkedHashSet()) { keys.getOrNull(it) }) }, TileViewSelectionChanged)
        bind({ index: Int -> items.getOrNull(index)?.let(onActivate) ?: Unit }, TileViewActivated)
        bind(if (contextMenuItems != null) onContext else null, TileViewContext)
        bind(TileSize(tileSize, iconSize), TileSizeProperty)
        bind(spacing, SpacingProperty)
        bind(layout, LayoutProperty)
        bind(emptyText, EmptyTextProperty)
    })
}

/** The items a composition gives, with the indices it selects. */
private data class TileItems(val items: List<Any?>, val selected: Set<Int>)

/**
 * Gives Raven the tiles: the same number redraws them (`Rtile_view_refresh`), a new number gives
 * them again (`Rtile_view_set_items`), which clears Raven's selection, so the app's is shown again.
 */
private class TileMirror {
    var tileView: Rid? = null
        private set
    private var count = -1

    fun sync(widget: Rid, data: TileItems) {
        tileView = widget
        if (data.items.size == count) {
            Rtile_view_refresh(widget)
            return
        }
        count = data.items.size
        setTileViewItems(widget, count)
        Rtile_view_clear_selection(widget)
        data.selected.forEach { Rtile_view_set_selected(widget, it, R_TRUE) }
    }
}

private data class TileSize(val size: DpSize, val icon: Dp)

private val TileSizeProperty = Property<TileSize> { widget, (size, icon) ->
    Rtile_view_set_tile_size(widget, size.width.value, size.height.value, icon.value)
}

// Raven has no C setter for these: they go through its XML attributes.
private val SpacingProperty = Property<Dp?> { widget, spacing -> Rwidget_set_attribute(widget, "Spacing", "${(spacing ?: 8.dp).value}") }

private val LayoutProperty = Property<TileLayout> { widget, layout -> Rwidget_set_attribute(widget, "Layout", layout.name) }

private val EmptyTextProperty = Property<String?> { widget, text -> Rtile_view_set_empty_text(widget, text.orEmpty()) }

/**
 * What an app can ask of a [TileView]'s position: [revealItem]. Raven can't report the position,
 * so it isn't saved; on a state not attached to a tile view, [revealItem] does nothing.
 */
@Stable
public class TileViewState internal constructor() {
    internal var tileView: Rid? = null

    /** Scrolls just enough to show the tile at [index] (`Rtile_view_scroll_to`). */
    public suspend fun revealItem(index: Int) {
        tileView?.takeIf { Rwidget_is_valid(it) == R_TRUE }?.let { Rtile_view_scroll_to(it, index) }
    }
}

/** A [TileViewState]. */
@Composable
public fun rememberTileViewState(): TileViewState = remember { TileViewState() }

/** Points a [TileViewState] at its tile view while the view's node holds it. */
private class TileViewFollower : WidgetFollower {
    private var widget: Rid? = null

    var state: TileViewState? = null
        set(value) {
            if (value === field) return
            widget?.let { view ->
                if (field?.tileView == view) field?.tileView = null
                value?.attachTo(view)
            }
            field = value
        }

    private fun TileViewState.attachTo(view: Rid) {
        if (tileView != null && tileView != view) warnSecondScrollable("TileViewState") else tileView = view
    }

    override fun attach(widget: Rid) {
        this.widget = widget
        state?.attachTo(widget)
    }

    override fun follow(widget: Rid) = Unit

    override fun detach(widget: Rid) {
        if (state?.tileView == widget) state?.tileView = null
        this.widget = null
    }
}
