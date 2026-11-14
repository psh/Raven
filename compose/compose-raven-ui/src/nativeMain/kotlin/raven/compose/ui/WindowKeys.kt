@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.pointed
import kotlinx.cinterop.staticCFunction
import raven.cinterop.REvent
import raven.cinterop.R_FALSE
import raven.cinterop.R_MOUSE_X1
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rwidget_is_valid
import raven.cinterop.Rwidget_set_event_callback
import raven.cinterop.Rwindow_get_focused_widget
import raven.cinterop.Rwindow_has_popup
import raven.cinterop.Rwindow_set_event_hook
import raven.compose.ui.input.key.Key
import raven.compose.ui.input.key.KeyEvent
import raven.compose.ui.input.key.KeyEventType
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

/**
 * Gives a key down or up to [RavenWindow.onKeyEvent], then, when it didn't consume it, a back key
 * to the window's back ([RavenWindow.backKeys]); returns whether either consumed it.
 */
private fun RavenWindow.leftOverKey(raw: REvent): Boolean {
    val kind = raw.inputKind()
    if (kind != InputKind.KeyDown && kind != InputKind.KeyUp) return false
    return try {
        val event = inputEvent(raw, kind).toKeyEvent()!!
        onKeyEvent(event) || (backKeys && event.isBackKey() && (event.type == KeyEventType.KeyUp || back()))
    } catch (t: Throwable) {
        reportError(t)
        false
    }
}

/** Cmd+[ or Alt+Left: back, as browsers and file managers have it. */
private fun KeyEvent.isBackKey(): Boolean =
    (key == Key.LeftBracket && isMetaPressed && !isAltPressed && !isCtrlPressed) ||
        (key == Key.DirectionLeft && isAltPressed && !isMetaPressed && !isCtrlPressed)

/** The mouse's Back button, wherever the pointer is: back. Its release is consumed with it. */
private fun RavenWindow.backButton(raw: REvent): Boolean {
    if (!backKeys || raw.button != R_MOUSE_X1) return false
    return when (raw.inputKind()) {
        InputKind.Press -> back()
        InputKind.Release -> true
        else -> false
    }
}

private val onRootInput = staticCFunction { _: Rid, event: CPointer<REvent>?, userData: COpaquePointer? ->
    if (userData!!.asStableRef<RavenWindow>().get().leftOverKey(event!!.pointed)) R_TRUE else R_FALSE
}

private val onWindowEvent = staticCFunction { window: Rid, event: CPointer<REvent>?, userData: COpaquePointer? ->
    val ravenWindow = userData!!.asStableRef<RavenWindow>().get()
    // A focused widget, or an open menu or dialog, sees the key first: the root sees what they leave.
    val focused = Rwindow_get_focused_widget(window)
    if (ravenWindow.backButton(event!!.pointed)) {
        R_TRUE
    } else if (Rwidget_is_valid(focused) == R_TRUE || Rwindow_has_popup(window) == R_TRUE) {
        R_FALSE
    } else if (userData!!.asStableRef<RavenWindow>().get().leftOverKey(event!!.pointed)) {
        R_TRUE
    } else {
        R_FALSE
    }
}
