@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.pointed
import kotlinx.cinterop.staticCFunction
import raven.cinterop.REvent
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rwidget_is_valid
import raven.cinterop.Rwidget_set_event_callback
import raven.cinterop.Rwindow_get_focused_widget
import raven.cinterop.Rwindow_has_popup
import raven.cinterop.Rwindow_set_event_hook
import raven.compose.ui.input.key.toKeyEvent

// `Window(onKeyEvent)`: the keys nothing in the window consumed. Raven gives a key to the focused
// widget, which bubbles it up to the window's root, so the root's event callback sees what the
// widgets left. With nothing focused Raven gives the key to no widget, so the window's event hook,
// which sees every event first, takes it then.

/** Has the window's root and event hook hand the keys nothing consumed to [RavenWindow.onKeyEvent]. */
internal fun RavenWindow.listenForKeys(userData: COpaquePointer) {
    Rwidget_set_event_callback(root.widget, onRootInput, userData)
    Rwindow_set_event_hook(window, onWindowEvent, userData)
}

/** Gives a key down or up to [RavenWindow.onKeyEvent], and returns whether it consumed it. */
private fun RavenWindow.leftOverKey(raw: REvent): Boolean {
    val kind = raw.inputKind()
    if (kind != InputKind.KeyDown && kind != InputKind.KeyUp) return false
    return try {
        onKeyEvent(inputEvent(raw, kind).toKeyEvent()!!)
    } catch (t: Throwable) {
        reportError(t)
        false
    }
}

private val onRootInput = staticCFunction { _: Rid, event: CPointer<REvent>?, userData: COpaquePointer? ->
    if (userData!!.asStableRef<RavenWindow>().get().leftOverKey(event!!.pointed)) R_TRUE else R_FALSE
}

private val onWindowEvent = staticCFunction { window: Rid, event: CPointer<REvent>?, userData: COpaquePointer? ->
    // A focused widget, or an open menu or dialog, sees the key first: the root sees what they leave.
    val focused = Rwindow_get_focused_widget(window)
    if (Rwidget_is_valid(focused) == R_TRUE || Rwindow_has_popup(window) == R_TRUE) {
        R_FALSE
    } else if (userData!!.asStableRef<RavenWindow>().get().leftOverKey(event!!.pointed)) {
        R_TRUE
    } else {
        R_FALSE
    }
}
