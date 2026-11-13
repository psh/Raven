package raven.compose.foundation

import raven.compose.ui.InputElement
import raven.compose.ui.InputEvent
import raven.compose.ui.InputKind
import raven.compose.ui.Modifier
import raven.compose.ui.focus.FocusableElement
import raven.compose.ui.input.pointer.PointerButton

/**
 * A click handler: a press of the primary button, then its release over the widget. With
 * [onDoubleClick], the second click of a double click calls it instead of [onClick].
 */
internal data class ClickableElement(
    val enabled: Boolean,
    val onClick: () -> Unit,
    val onDoubleClick: (() -> Unit)? = null,
) : InputElement {
    override fun onInput(event: InputEvent): Boolean {
        if (!enabled || event.button != PointerButton.Primary) return false
        if (event.kind == InputKind.Release && event.completesClick) {
            if (onDoubleClick != null && event.clicks == 2) onDoubleClick() else onClick()
        }
        // Both the press and the release are the click's.
        return event.kind == InputKind.Press || event.kind == InputKind.Release
    }
}

/**
 * Calls [onClick] when the widget is clicked with the primary button: pressed, then released with
 * the pointer still over it. Both are consumed, so the widget's own handling and its ancestors
 * don't see them. While [enabled] is false it does nothing, and lets them through.
 */
public fun Modifier.clickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this then ClickableElement(enabled, onClick)

/**
 * As [clickable], and the second click of a double click calls [onDoubleClick] instead of
 * [onClick], from Raven's click count. Raven reports each click as it comes, so the first click of
 * a double click has called [onClick] already. There is no long click, which needs a timer.
 */
public fun Modifier.combinedClickable(
    enabled: Boolean = true,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = this then ClickableElement(enabled, onClick, onDoubleClick)

/**
 * Lets the widget take the keyboard focus: from a click, from Tab, or from a `FocusRequester`.
 * Raven has no getter for whether a widget takes the focus by itself, so `focusable(false)`, or
 * dropping `focusable()` from a chain, takes it away even from a widget that takes it by itself (a
 * button, a text field).
 */
public fun Modifier.focusable(enabled: Boolean = true): Modifier = this then FocusableElement(enabled)
