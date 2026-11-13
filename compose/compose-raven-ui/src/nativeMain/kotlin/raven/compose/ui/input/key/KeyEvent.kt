@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui.input.key

import raven.compose.ui.InputElement
import raven.compose.ui.InputEvent
import raven.compose.ui.InputKind
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.Modifier

/** Whether a key went down or came up. */
public enum class KeyEventType { KeyDown, KeyUp }

/**
 * A key going down or up, with the modifier keys held at the time. [isMetaPressed] is the Super
 * key: Cmd on macOS. [isRepeat] is a key down that the keyboard's auto-repeat sent.
 */
public data class KeyEvent(
    val key: Key,
    val type: KeyEventType,
    val isCtrlPressed: Boolean = false,
    val isShiftPressed: Boolean = false,
    val isAltPressed: Boolean = false,
    val isMetaPressed: Boolean = false,
    val isRepeat: Boolean = false,
)

/** A handler of the keys that reach the widget. */
internal data class OnKeyEventElement(val onKeyEvent: (KeyEvent) -> Boolean) : InputElement {
    override fun onInput(event: InputEvent): Boolean = event.toKeyEvent()?.let(onKeyEvent) ?: false
}

/**
 * Calls [onKeyEvent] with each key that reaches the widget: the keys of the focused widget, which
 * bubble up to its ancestors until one consumes them. Returning true consumes the key, so nothing
 * above sees it and Raven does nothing more with it. The widget's own handling comes after, so
 * consuming a key a text field would type keeps it out of the field. Raven doesn't tunnel, so there
 * is no `onPreviewKeyEvent`.
 */
public fun Modifier.onKeyEvent(onKeyEvent: (KeyEvent) -> Boolean): Modifier = this then OnKeyEventElement(onKeyEvent)

/** The [KeyEvent] of a key down or up; null for any other event. */
internal fun InputEvent.toKeyEvent(): KeyEvent? {
    val type = when (kind) {
        InputKind.KeyDown -> KeyEventType.KeyDown
        InputKind.KeyUp -> KeyEventType.KeyUp
        else -> return null
    }
    return KeyEvent(Key.of(key), type, isCtrlPressed, isShiftPressed, isAltPressed, isMetaPressed, isRepeat)
}
