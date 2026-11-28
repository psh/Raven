@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import kotlinx.cinterop.Arena
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import raven.cinterop.RTileItem
import raven.cinterop.R_INVALID_ID
import raven.cinterop.Rid
import raven.cinterop.Rtile_view_get_selection
import raven.cinterop.Rtile_view_get_selection_count
import raven.cinterop.Rtile_view_set_activated_callback
import raven.cinterop.Rtile_view_set_context_callback
import raven.cinterop.Rtile_view_set_items
import raven.cinterop.Rtile_view_set_selected
import raven.cinterop.Rtile_view_set_selection_changed_callback
import raven.cinterop.Rtile_view_clear_selection
import raven.cinterop.Rwidget_get_user_handle
import raven.compose.ui.graphics.painter.TextureRegion

// The tile view's controlled value and event kinds.

/** What a tile shows, as the app's `tile` function gives it: Raven's `RTileItem`, in Kotlin. */
@InternalComposeRavenApi
public data class TileItemValue(
    val text: String,
    val detail: String? = null,
    val badge: String? = null,
    val icon: Rid = R_INVALID_ID,
    val region: TextureRegion = TextureRegion.Whole,
)

/** The tiles selected, by index. Raven calls no callback when it is written. */
@InternalComposeRavenApi
public val TileViewSelection: ControlledValue<Set<Int>> = ControlledValue(
    read = { tileSelection(it) },
    write = { widget, indices ->
        Rtile_view_clear_selection(widget)
        indices.forEach { Rtile_view_set_selected(widget, it, 1) }
    },
)

/** What tile `index` shows, asked as Raven paints the tiles in view; null shows an empty tile. */
@InternalComposeRavenApi
public val TileViewItem: Event<(index: Int) -> TileItemValue?> = Event { _, _ -> }

/** The user changed the selection: the tiles selected now. */
@InternalComposeRavenApi
public val TileViewSelectionChanged: Event<(Set<Int>) -> Unit> = Event(marks = TileViewSelection) { widget, userData ->
    Rtile_view_set_selection_changed_callback(widget, onTileViewSelectionChanged, userData)
}

/** A tile activated: double-clicked, or Enter pressed on it. */
@InternalComposeRavenApi
public val TileViewActivated: Event<(Int) -> Unit> = Event { widget, userData ->
    Rtile_view_set_activated_callback(widget, onTileViewActivated, userData)
}

/** A right click: the tile under the pointer (selected first), or -1 in the empty space, and where in the window. */
@InternalComposeRavenApi
public val TileViewContext: Event<(index: Int, x: Float, y: Float) -> Unit> = Event { widget, userData ->
    Rtile_view_set_context_callback(widget, onTileViewContext, userData)
}

/** Gives [widget] [count] tiles, each asked of its [TileViewItem] handler. Raven clears the selection. */
@InternalComposeRavenApi
public fun setTileViewItems(widget: Rid, count: Int) {
    Rtile_view_set_items(widget, count, onTileViewItem, Rwidget_get_user_handle(widget))
}

/** What tile [index] of [node]'s tile view shows, read through the callback Raven calls: for tests. */
@InternalComposeRavenApi
public fun readTile(node: RavenNode, index: Int): TileItemValue = memScoped {
    val item = alloc<RTileItem>()
    item.icon = R_INVALID_ID
    item.uv[2] = 1f
    item.uv[3] = 1f
    onTileViewItem.invoke(node.widget, index, item.ptr, Rwidget_get_user_handle(node.widget))
    TileItemValue(
        item.text?.toKString().orEmpty(),
        item.detail?.toKString(),
        item.badge?.toKString(),
        item.icon,
        TextureRegion(item.uv[0], item.uv[1], item.uv[2], item.uv[3]),
    )
}

private fun tileSelection(widget: Rid): Set<Int> =
    (0 until Rtile_view_get_selection_count(widget)).mapTo(LinkedHashSet()) { Rtile_view_get_selection(widget, it) }

// The texts of the tile described last: Raven draws them before it asks for the next.
private var tileTexts: Arena? = null

private val onTileViewItem = staticCFunction { _: Rid, index: Int, item: CPointer<RTileItem>?, userData: COpaquePointer? ->
    var value: TileItemValue? = null
    dispatchEvent(userData, TileViewItem) { value = it(index) }
    tileTexts?.clear()
    val texts = Arena().also { tileTexts = it }
    val shown = value
    val out = item?.pointed
    if (shown != null && out != null) {
        out.text = shown.text.cstr.getPointer(texts)
        out.detail = shown.detail?.cstr?.getPointer(texts)
        out.badge = shown.badge?.cstr?.getPointer(texts)
        out.icon = shown.icon
        out.uv[0] = shown.region.minU
        out.uv[1] = shown.region.minV
        out.uv[2] = shown.region.maxU
        out.uv[3] = shown.region.maxV
    }
}

private val onTileViewSelectionChanged = staticCFunction { widget: Rid, _: Int, userData: COpaquePointer? ->
    dispatchEvent(userData, TileViewSelectionChanged) { it(tileSelection(widget)) }
}

private val onTileViewActivated = staticCFunction { _: Rid, index: Int, userData: COpaquePointer? ->
    dispatchEvent(userData, TileViewActivated) { it(index) }
}

private val onTileViewContext = staticCFunction { _: Rid, index: Int, x: Float, y: Float, userData: COpaquePointer? ->
    dispatchEvent(userData, TileViewContext) { it(index, x, y) }
}
