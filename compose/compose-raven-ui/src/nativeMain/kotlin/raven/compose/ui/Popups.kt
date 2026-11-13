@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import raven.cinterop.R_TRUE
import raven.cinterop.Rmenu_open
import raven.cinterop.Rwidget_get_rect
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwidget_is_valid
import raven.compose.ui.unit.DpOffset

// What foundation's menus opened from code need: a popup's anchor, and the host's error path.

/**
 * Opens this [popup][RavenNode.isPopup] Menu under its parent's widget, as Compose's
 * `DropdownMenu` anchors to its parent: at the parent's bottom-left plus [offset]. A parent that is
 * a content node stands for the composable that holds it, so the anchor is that composable's widget.
 */
@InternalComposeRavenApi
public fun RavenNode.openMenuUnderParent(offset: DpOffset) {
    check(isPopup) { "only a popup opens under its parent" }
    check(Rwidget_is_valid(widget) == R_TRUE) { "$name has left the composition" }
    val parent = checkNotNull(parent) { "$name opens under its parent, and it has none" }
    val anchor = if (parent.isContentNode) parent.parent ?: parent else parent
    memScoped {
        val x = alloc<FloatVar>()
        val y = alloc<FloatVar>()
        val width = alloc<FloatVar>()
        val height = alloc<FloatVar>()
        Rwidget_get_rect(anchor.widget, x.ptr, y.ptr, width.ptr, height.ptr)
        Rmenu_open(widget, Rwidget_get_window(anchor.widget), x.value + offset.x.value, y.value + height.value + offset.y.value)
    }
}

/**
 * The host's error path, for a callback that a composable registers with Raven by itself: what it
 * throws must not cross into C.
 */
@InternalComposeRavenApi
@Composable
public fun rememberErrorReporter(): (Throwable) -> Unit {
    val host = LocalRavenHost.current
    return remember(host) { { t: Throwable -> host.reportError(t) } }
}
