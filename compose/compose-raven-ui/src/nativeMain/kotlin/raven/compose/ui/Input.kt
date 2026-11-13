@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.pointed
import kotlinx.cinterop.staticCFunction
import raven.cinterop.REvent
import raven.cinterop.RKey
import raven.cinterop.R_EVENT_FOCUS_GAINED
import raven.cinterop.R_EVENT_FOCUS_LOST
import raven.cinterop.R_EVENT_KEY_DOWN
import raven.cinterop.R_EVENT_KEY_UP
import raven.cinterop.R_EVENT_MOUSE_DOWN
import raven.cinterop.R_EVENT_MOUSE_ENTER
import raven.cinterop.R_EVENT_MOUSE_LEAVE
import raven.cinterop.R_EVENT_MOUSE_MOVE
import raven.cinterop.R_EVENT_MOUSE_UP
import raven.cinterop.R_EVENT_MOUSE_WHEEL
import raven.cinterop.R_FALSE
import raven.cinterop.R_MOD_ALT
import raven.cinterop.R_MOD_CTRL
import raven.cinterop.R_MOD_SHIFT
import raven.cinterop.R_MOD_SUPER
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_MOUSE_MIDDLE
import raven.cinterop.R_MOUSE_RIGHT
import raven.cinterop.R_MOUSE_X1
import raven.cinterop.R_MOUSE_X2
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.compose.ui.input.pointer.PointerButton
import raven.native.callbackTarget

// Generic input (section 7): Raven's one event callback per widget, shared by every input element of
// the node's Modifier chain.

/** What a Raven input event is, for the input elements: the events Raven delivers to a widget, but typed text. */
@InternalComposeRavenApi
public enum class InputKind { Press, Release, Move, Enter, Exit, Scroll, KeyDown, KeyUp, FocusGained, FocusLost }

/**
 * One input event of a widget, copied from Raven's `REvent`, which lasts only for the callback.
 * [x] and [y] are relative to the widget, [windowX] and [windowY] to its [window], all in logical
 * units. [button] is the button pressed or released, [clicks] Raven's click count (2 for the second
 * click of a double click), and [scrollX] and [scrollY] the wheel's lines, positive up and left as
 * Raven gives them. [completesClick] is a release of the primary button that a press over the same
 * widget began, with the pointer still over it: a click.
 */
@InternalComposeRavenApi
public class InputEvent internal constructor(
    public val kind: InputKind,
    public val window: Rid,
    public val x: Float,
    public val y: Float,
    public val windowX: Float,
    public val windowY: Float,
    public val button: PointerButton?,
    public val clicks: Int,
    public val key: RKey,
    public val isCtrlPressed: Boolean,
    public val isShiftPressed: Boolean,
    public val isAltPressed: Boolean,
    public val isMetaPressed: Boolean,
    public val isRepeat: Boolean,
    public val scrollX: Float,
    public val scrollY: Float,
    public val completesClick: Boolean,
)

/**
 * An element of a Modifier chain that handles input. A node with one registers Raven's event
 * callback, which hands each event to the node's input elements in chain order, until one returns
 * true: that one consumes the event, so neither the widget nor its ancestors see it.
 */
@InternalComposeRavenApi
public interface InputElement : Modifier.Element {
    public fun onInput(event: InputEvent): Boolean
}

/** The kind of a Raven event, or null for one the input elements don't take (typed text). */
internal fun REvent.inputKind(): InputKind? = when (type) {
    R_EVENT_MOUSE_DOWN -> InputKind.Press
    R_EVENT_MOUSE_UP -> InputKind.Release
    R_EVENT_MOUSE_MOVE -> InputKind.Move
    R_EVENT_MOUSE_ENTER -> InputKind.Enter
    R_EVENT_MOUSE_LEAVE -> InputKind.Exit
    R_EVENT_MOUSE_WHEEL -> InputKind.Scroll
    R_EVENT_KEY_DOWN -> InputKind.KeyDown
    R_EVENT_KEY_UP -> InputKind.KeyUp
    R_EVENT_FOCUS_GAINED -> InputKind.FocusGained
    R_EVENT_FOCUS_LOST -> InputKind.FocusLost
    else -> null
}

internal fun REvent.pointerButton(): PointerButton? = when (button) {
    R_MOUSE_LEFT -> PointerButton.Primary
    R_MOUSE_RIGHT -> PointerButton.Secondary
    R_MOUSE_MIDDLE -> PointerButton.Tertiary
    R_MOUSE_X1 -> PointerButton.Back
    R_MOUSE_X2 -> PointerButton.Forward
    else -> null
}

/** [raw] as an [InputEvent] of [kind], which [completesClick] or not. */
internal fun inputEvent(raw: REvent, kind: InputKind, completesClick: Boolean = false): InputEvent = InputEvent(
    kind = kind,
    window = raw.window,
    x = raw.local_x,
    y = raw.local_y,
    windowX = raw.x,
    windowY = raw.y,
    button = if (kind == InputKind.Press || kind == InputKind.Release) raw.pointerButton() else null,
    clicks = raw.clicks,
    key = raw.key,
    isCtrlPressed = raw.modifiers and R_MOD_CTRL != 0u,
    isShiftPressed = raw.modifiers and R_MOD_SHIFT != 0u,
    isAltPressed = raw.modifiers and R_MOD_ALT != 0u,
    isMetaPressed = raw.modifiers and R_MOD_SUPER != 0u,
    isRepeat = raw.repeat == R_TRUE,
    scrollX = raw.wheel_x,
    scrollY = raw.wheel_y,
    completesClick = completesClick,
)

/**
 * The event callback of every node with an input element: the node hands the event to its input
 * elements. Nothing they throw crosses into C: the host's error path receives it.
 */
internal val onWidgetInput = staticCFunction { _: Rid, event: CPointer<REvent>?, userData: COpaquePointer? ->
    val node = userData!!.callbackTarget<RavenNode>()
    try {
        if (node.dispatchInput(event!!.pointed)) R_TRUE else R_FALSE
    } catch (t: Throwable) {
        checkNotNull(node.host) { "an input element needs a node made through the binding layer" }.reportError(t)
        R_FALSE
    }
}
