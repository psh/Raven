package raven.compose.animation

import raven.cinterop.Rid
import raven.compose.ui.AppearTransition
import raven.compose.ui.Modifier
import raven.compose.ui.appearCalls
import kotlin.time.Duration

/**
 * Plays a transition on demand on a widget already shown, as a `FocusRequester` focuses one: give
 * the widget `Modifier.transitionRequester(requester)`, then call [play]. Remember it with
 * `remember { TransitionRequester() }`.
 */
public class TransitionRequester {
    /** The widget of the node it is attached to, if any. */
    internal var widget: Rid? = null

    /**
     * Plays [transition] on the widget after [delay], whatever its `appearTransition`
     * (`Rwidget_play_transition`), and keeps that for its next appearance. A requester attached to
     * no widget does nothing. Call it on the UI thread: from an event handler or an effect.
     */
    public fun play(transition: AppearTransition, delay: Duration = Duration.ZERO) {
        require(!delay.isNegative()) { "delay must not be negative, not $delay" }
        val widget = widget ?: return
        appearCalls.play(widget, transition, delay)
    }
}

/** A requester of transitions. Several can be in a chain, each attached to the widget. */
internal data class TransitionRequesterElement(val requester: TransitionRequester) : Modifier.Element

/** Attaches [requester] to the widget, so its [TransitionRequester.play] plays transitions on it. */
public fun Modifier.transitionRequester(requester: TransitionRequester): Modifier = this then TransitionRequesterElement(requester)
