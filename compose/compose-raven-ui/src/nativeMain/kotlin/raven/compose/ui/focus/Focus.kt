@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui.focus

import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rwidget_focus
import raven.cinterop.Rwidget_is_focused
import raven.cinterop.Rwidget_is_valid
import raven.cinterop.Rwindow_clear_focus
import raven.compose.ui.InputElement
import raven.compose.ui.InputEvent
import raven.compose.ui.InputKind
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.Modifier

// Keyboard focus is Raven's: these ask for it, follow it and clear it. Tab traversal is Raven's own.

/**
 * Whether the widget may take the keyboard focus, as `Modifier.focusable` gives it. The last one in
 * the chain wins.
 */
@InternalComposeRavenApi
public data class FocusableElement(val enabled: Boolean) : Modifier.Element

/**
 * Moves the keyboard focus to a widget from code: give the widget `Modifier.focusRequester(it)`,
 * then call [requestFocus], in an event handler or a `LaunchedEffect`. The widget must be able to
 * take the focus (a text field, a button, or `Modifier.focusable()`); Raven ignores the request
 * otherwise.
 */
public class FocusRequester {
    // The widgets of the nodes whose chains hold this requester, in the order they took it.
    private val widgets = mutableListOf<Rid>()

    internal fun attach(widget: Rid) {
        widgets += widget
    }

    internal fun detach(widget: Rid) {
        widgets -= widget
    }

    /**
     * Gives the keyboard focus to the first widget of this requester that takes it. Throws when no
     * widget in the composition has it, as Compose does.
     */
    public fun requestFocus() {
        check(widgets.isNotEmpty()) { "FocusRequester is not initialized: no composable has Modifier.focusRequester with it" }
        for (widget in widgets) {
            if (Rwidget_is_valid(widget) != R_TRUE) continue
            Rwidget_focus(widget)
            if (Rwidget_is_focused(widget) == R_TRUE) return
        }
    }
}

/** A [FocusRequester] in a chain. Every one in the chain applies. */
internal data class FocusRequesterElement(val requester: FocusRequester) : Modifier.Element

/** Lets [focusRequester] move the keyboard focus to this widget. */
public fun Modifier.focusRequester(focusRequester: FocusRequester): Modifier = this then FocusRequesterElement(focusRequester)

/** Whether a widget has the keyboard focus. */
public interface FocusState {
    public val isFocused: Boolean
}

private data class RavenFocusState(override val isFocused: Boolean) : FocusState

/** A handler of the widget's focus changes. It never consumes them. */
internal data class OnFocusChangedElement(val onFocusChanged: (FocusState) -> Unit) : InputElement {
    override fun onInput(event: InputEvent): Boolean {
        when (event.kind) {
            InputKind.FocusGained -> onFocusChanged(RavenFocusState(isFocused = true))
            InputKind.FocusLost -> onFocusChanged(RavenFocusState(isFocused = false))
            else -> {}
        }
        return false
    }
}

/**
 * Calls [onFocusChanged] each time the widget takes or loses the keyboard focus. Raven tells only
 * the widget itself, so a parent isn't told when a child's focus changes, and nothing is called
 * for the state the widget starts in.
 */
public fun Modifier.onFocusChanged(onFocusChanged: (FocusState) -> Unit): Modifier = this then OnFocusChangedElement(onFocusChanged)

/** What the window's keyboard focus can be told: `LocalFocusManager.current`. */
public interface FocusManager {
    /**
     * Leaves the window with no focused widget: the widget that had the focus loses it, and keys go
     * to the window (`Window(onKeyEvent)`) until something takes the focus again. [force] is
     * Compose's, and changes nothing: Raven always clears it.
     */
    public fun clearFocus(force: Boolean = false)
}

/** The [FocusManager] of one Raven window. */
internal class WindowFocusManager(private val window: Rid) : FocusManager {
    override fun clearFocus(force: Boolean) = Rwindow_clear_focus(window)
}
