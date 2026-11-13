@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui.input.pointer

import raven.cinterop.RCursor
import raven.cinterop.R_CURSOR_ARROW
import raven.cinterop.R_CURSOR_CROSSHAIR
import raven.cinterop.R_CURSOR_HAND
import raven.cinterop.R_CURSOR_IBEAM
import raven.cinterop.R_CURSOR_MOVE
import raven.cinterop.R_CURSOR_NONE
import raven.cinterop.R_CURSOR_NOT_ALLOWED
import raven.cinterop.R_CURSOR_RESIZE_H
import raven.cinterop.R_CURSOR_RESIZE_NESW
import raven.cinterop.R_CURSOR_RESIZE_NWSE
import raven.cinterop.R_CURSOR_RESIZE_V
import raven.compose.ui.InputElement
import raven.compose.ui.InputEvent
import raven.compose.ui.InputKind
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.Modifier
import raven.compose.ui.geometry.Offset

/** What the pointer did. */
public enum class PointerEventType {
    /** A button went down over the widget. */
    Press,

    /** A button came up over the widget. */
    Release,

    /** The pointer moved over the widget. */
    Move,

    /** The pointer came over the widget. */
    Enter,

    /** The pointer left the widget. */
    Exit,

    /** The wheel turned, or the fingers scrolled on a trackpad, over the widget. */
    Scroll,
}

/** A mouse button: [Back] and [Forward] are the side buttons. */
public enum class PointerButton { Primary, Secondary, Tertiary, Back, Forward }

/** The modifier keys held during a pointer event. [isMetaPressed] is the Super key: Cmd on macOS. */
public data class PointerKeyboardModifiers(
    val isCtrlPressed: Boolean = false,
    val isShiftPressed: Boolean = false,
    val isAltPressed: Boolean = false,
    val isMetaPressed: Boolean = false,
)

/**
 * What the pointer did over a widget: its [type], its [position] in the widget in logical units
 * (dp), the [button] of a press or a release (null otherwise), the [keyboardModifiers], and for a
 * [PointerEventType.Scroll] the [scrollDelta] in lines, positive down and right as in Compose.
 *
 * An event goes on to the widget and its ancestors unless the handler calls [consume].
 */
public class PointerEvent internal constructor(
    public val type: PointerEventType,
    public val position: Offset,
    public val button: PointerButton?,
    public val keyboardModifiers: PointerKeyboardModifiers,
    public val scrollDelta: Offset,
) {
    /** Whether a handler has consumed the event. */
    public var isConsumed: Boolean = false
        private set

    /** Keeps the event from the widget's own handling and from its ancestors. */
    public fun consume() {
        isConsumed = true
    }

    override fun toString(): String = "PointerEvent($type, $position, $button)"
}

/** A handler of one [type] of pointer event. */
internal data class OnPointerEventElement(val type: PointerEventType, val onEvent: (PointerEvent) -> Unit) : InputElement {
    override fun onInput(event: InputEvent): Boolean {
        val pointerEvent = event.toPointerEvent() ?: return false
        if (pointerEvent.type != type) return false
        onEvent(pointerEvent)
        return pointerEvent.isConsumed
    }
}

/**
 * Calls [onEvent] with each pointer event of [eventType] over the widget. Press, Release, Move and
 * Scroll bubble up from the widget under the pointer; Enter and Exit come to each widget the
 * pointer comes over or leaves. The event isn't consumed unless [onEvent] calls
 * [PointerEvent.consume].
 */
public fun Modifier.onPointerEvent(eventType: PointerEventType, onEvent: (PointerEvent) -> Unit): Modifier =
    this then OnPointerEventElement(eventType, onEvent)

/** The [PointerEvent] of a pointer event; null for a key or focus event. */
internal fun InputEvent.toPointerEvent(): PointerEvent? {
    val type = when (kind) {
        InputKind.Press -> PointerEventType.Press
        InputKind.Release -> PointerEventType.Release
        InputKind.Move -> PointerEventType.Move
        InputKind.Enter -> PointerEventType.Enter
        InputKind.Exit -> PointerEventType.Exit
        InputKind.Scroll -> PointerEventType.Scroll
        else -> return null
    }
    // Raven's wheel is positive up and left, Compose's scroll delta positive down and right
    // (subtracted from 0, so an axis that didn't scroll is 0, not -0).
    val scroll = if (type == PointerEventType.Scroll) Offset(0f - scrollX, 0f - scrollY) else Offset.Zero
    val modifiers = PointerKeyboardModifiers(isCtrlPressed, isShiftPressed, isAltPressed, isMetaPressed)
    return PointerEvent(type, Offset(x, y), button, modifiers, scroll)
}

/** The pointer shown over a widget: one of Raven's cursors. */
public enum class PointerIcon(internal val cursor: RCursor) {
    /** The arrow. */
    Default(R_CURSOR_ARROW),

    /** The text beam. */
    Text(R_CURSOR_IBEAM),

    /** The pointing hand of a link. */
    Hand(R_CURSOR_HAND),
    Crosshair(R_CURSOR_CROSSHAIR),
    ResizeHorizontal(R_CURSOR_RESIZE_H),
    ResizeVertical(R_CURSOR_RESIZE_V),

    /** Resizing from the top-left or bottom-right corner. */
    ResizeNwse(R_CURSOR_RESIZE_NWSE),

    /** Resizing from the top-right or bottom-left corner. */
    ResizeNesw(R_CURSOR_RESIZE_NESW),
    Move(R_CURSOR_MOVE),
    NotAllowed(R_CURSOR_NOT_ALLOWED),

    /** No pointer at all, on Windows; the arrow elsewhere. */
    None(R_CURSOR_NONE),
}

/** The pointer icon of a widget. The last one in the chain wins. */
internal data class PointerHoverIconElement(val icon: PointerIcon) : Modifier.Element

/**
 * Shows [icon] while the pointer is over the widget, or over a descendant without an icon of its
 * own (a text field shows its text beam). Without it, the widget shows its parent's. Raven has no
 * getter for a widget's own cursor, so dropping it from a chain leaves a widget that had one of its
 * own (a text field's beam) with its parent's.
 */
public fun Modifier.pointerHoverIcon(icon: PointerIcon): Modifier = this then PointerHoverIconElement(icon)
