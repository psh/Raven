@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.foundation.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rwidget_is_valid
import raven.cinterop.Rwidget_set_window_drag
import raven.compose.foundation.layout.Box
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.Modifier
import raven.compose.ui.WidgetAttachedElement
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.windowHasCustomHeader

/**
 * A part of a `Window(customHeader = true)` that moves the window when it is dragged and maximises
 * it on a double click, as a title bar does (`Rwidget_set_window_drag`). The buttons, fields and
 * other controls in [content] keep working; the text and images let the drag through. A
 * `HeaderBar` is one already. [content] is stacked as in a `Box`.
 *
 * Compose Desktop's is an extension of `WindowScope`; compose-raven's needs no scope. In a window
 * that has its system title bar, it does nothing, with a warning.
 */
@Composable
public fun WindowDraggableArea(modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val customHeader = windowHasCustomHeader()
    // Once for each area, not on each recomposition.
    remember(customHeader) {
        if (!customHeader) layoutWarningSink("WindowDraggableArea: the window has its system title bar (customHeader = false), so it doesn't drag the window")
    }
    Box(if (customHeader) modifier.then(WindowDragElement) else modifier) { content() }
}

/** Has the widget it is on drag its window. */
private data object WindowDragElement : WidgetAttachedElement {
    override fun attach(widget: Rid) = windowDragCalls.setWindowDrag(widget, true)

    // A released node's widget may be gone already.
    override fun detach(widget: Rid) {
        if (Rwidget_is_valid(widget) == R_TRUE) windowDragCalls.setWindowDrag(widget, false)
    }
}

/** Raven's window drag, which a headless window never shows: the tests replace [windowDragCalls]. */
internal open class WindowDragCalls {
    open fun setWindowDrag(widget: Rid, enabled: Boolean) = Rwidget_set_window_drag(widget, if (enabled) R_TRUE else R_FALSE)
}

internal var windowDragCalls: WindowDragCalls = WindowDragCalls()
