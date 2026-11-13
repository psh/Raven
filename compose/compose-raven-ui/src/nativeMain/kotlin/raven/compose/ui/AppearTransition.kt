package raven.compose.ui

import raven.cinterop.RTransition
import raven.cinterop.R_TRANSITION_DROP
import raven.cinterop.R_TRANSITION_FADE
import raven.cinterop.R_TRANSITION_FLY_DOWN
import raven.cinterop.R_TRANSITION_FLY_LEFT
import raven.cinterop.R_TRANSITION_FLY_RIGHT
import raven.cinterop.R_TRANSITION_FLY_UP
import raven.cinterop.R_TRANSITION_NONE
import raven.cinterop.R_TRANSITION_POP
import raven.cinterop.R_TRANSITION_SLIDE_LEFT
import raven.cinterop.R_TRANSITION_SLIDE_RIGHT
import raven.cinterop.R_TRANSITION_ZOOM
import raven.cinterop.Rid
import raven.cinterop.Rwidget_play_transition
import raven.cinterop.Rwidget_set_transition
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * How a widget arrives: one of Raven's transitions, which animate what the widget draws, not its
 * layout. They are springs, so they have no duration.
 */
public enum class AppearTransition {
    /** Fades in. */
    Fade,

    /** Grows from smaller, with a bounce. */
    Pop,

    /** Shrinks from larger. */
    Zoom,

    /** Rises from below. */
    FlyUp,

    /** Comes down from above. */
    FlyDown,

    /** Comes in from the right, moving left. */
    FlyLeft,

    /** Comes in from the left, moving right. */
    FlyRight,

    /** Falls from above and lands with a squash. */
    Drop,

    /** Slides a little way left, as a page pushed in. */
    SlideLeft,

    /** Slides a little way right, as a page popped back. */
    SlideRight,
}

/** An appear transition. The last one in the chain wins. */
internal data class AppearTransitionElement(val transition: AppearTransition, val delay: Duration) : Modifier.Element {
    init {
        require(!delay.isNegative()) { "delay must not be negative, not $delay" }
    }
}

/**
 * Plays [transition] each time the widget appears: when its window is first drawn, when it is
 * added to a window that is shown, and when it becomes visible again. [delay] holds it back, so
 * the items of a group given growing delays arrive one after the other. A widget already shown
 * doesn't play it again when the Modifier changes or the widget moves among its siblings.
 */
public fun Modifier.appearTransition(transition: AppearTransition, delay: Duration = Duration.ZERO): Modifier =
    this then AppearTransitionElement(transition, delay)

/** The Raven calls of `Modifier.appearTransition`. Raven has no getter, so tests replace them to see them. */
internal open class AppearCalls {
    /** Gives [widget] its transition, or none for `null`. Raven plays it at once, as well as on each appearance. */
    open fun set(widget: Rid, transition: AppearTransition?, delay: Duration) =
        Rwidget_set_transition(widget, transition.toRaven(), delay.toDouble(DurationUnit.SECONDS).toFloat())

    /** Plays [transition] on [widget] now, after [delay], keeping the one it was given: a `TransitionRequester`'s. */
    open fun play(widget: Rid, transition: AppearTransition, delay: Duration) =
        Rwidget_play_transition(widget, transition.toRaven(), delay.toDouble(DurationUnit.SECONDS).toFloat())

    /** Stops the transition [widget] is playing, keeping the one it was given. */
    open fun stop(widget: Rid) = Rwidget_play_transition(widget, R_TRANSITION_NONE, 0f)
}

internal var appearCalls: AppearCalls = AppearCalls()

private fun AppearTransition?.toRaven(): RTransition = when (this) {
    null -> R_TRANSITION_NONE
    AppearTransition.Fade -> R_TRANSITION_FADE
    AppearTransition.Pop -> R_TRANSITION_POP
    AppearTransition.Zoom -> R_TRANSITION_ZOOM
    AppearTransition.FlyUp -> R_TRANSITION_FLY_UP
    AppearTransition.FlyDown -> R_TRANSITION_FLY_DOWN
    AppearTransition.FlyLeft -> R_TRANSITION_FLY_LEFT
    AppearTransition.FlyRight -> R_TRANSITION_FLY_RIGHT
    AppearTransition.Drop -> R_TRANSITION_DROP
    AppearTransition.SlideLeft -> R_TRANSITION_SLIDE_LEFT
    AppearTransition.SlideRight -> R_TRANSITION_SLIDE_RIGHT
}
