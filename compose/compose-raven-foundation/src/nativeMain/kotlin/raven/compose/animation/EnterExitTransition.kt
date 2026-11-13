package raven.compose.animation

import androidx.compose.runtime.Immutable
import raven.compose.animation.core.AnimationSpec
import raven.compose.animation.core.Spring
import raven.compose.animation.core.spring
import raven.compose.ui.AppearTransition

/**
 * How [AnimatedVisibility]'s content arrives, as Compose's, played as the nearest of Raven's appear
 * transitions: [fadeIn] is Fade, [scaleIn] is Pop (from smaller) or Zoom (from larger), and the
 * slides are the Fly transitions. Raven's transitions are its own springs, so the animation specs
 * are ignored. Combined with `+`, a slide wins over a scale, and a scale over a fade.
 */
@Immutable
public class EnterTransition internal constructor(
    private val fade: Boolean,
    private val scale: AppearTransition?,
    private val slide: AppearTransition?,
) {
    /** The Raven transition the content appears with; null for none. */
    internal val appearTransition: AppearTransition? get() = slide ?: scale ?: if (fade) AppearTransition.Fade else null

    /** Both: the more telling of the two is played. */
    public operator fun plus(other: EnterTransition): EnterTransition =
        EnterTransition(fade || other.fade, other.scale ?: scale, other.slide ?: slide)

    override fun equals(other: Any?): Boolean =
        other is EnterTransition && fade == other.fade && scale == other.scale && slide == other.slide

    override fun hashCode(): Int = (fade.hashCode() * 31 + scale.hashCode()) * 31 + slide.hashCode()

    override fun toString(): String = "EnterTransition($appearTransition)"

    public companion object {
        /** The content appears at once. */
        public val None: EnterTransition = EnterTransition(fade = false, scale = null, slide = null)
    }
}

/**
 * How [AnimatedVisibility]'s content leaves: it fades out (`Rwidget_set_opacity`), then is removed.
 * Only [fadeOut] is played as itself; the other exits fall back to a fade with [fadeOut]'s default
 * spec.
 */
@Immutable
public class ExitTransition internal constructor(internal val fade: Fade?) {
    /** A fade to [targetAlpha] with [spec]. */
    internal data class Fade(val spec: AnimationSpec<Float>, val targetAlpha: Float)

    /** Both: a fade given by [fadeOut] wins over a fallen-back one. */
    public operator fun plus(other: ExitTransition): ExitTransition =
        ExitTransition(if (other.fade != null && other.fade != DefaultFade) other.fade else fade ?: other.fade)

    override fun equals(other: Any?): Boolean = other is ExitTransition && fade == other.fade

    override fun hashCode(): Int = fade.hashCode()

    override fun toString(): String = "ExitTransition($fade)"

    public companion object {
        /** The content is removed at once. */
        public val None: ExitTransition = ExitTransition(null)
    }
}

private val DefaultFadeSpec: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow)
private val DefaultFade = ExitTransition.Fade(DefaultFadeSpec, 0f)

/** Fades the content in: Raven's Fade. [animationSpec] and [initialAlpha] are Raven's. */
public fun fadeIn(animationSpec: AnimationSpec<Float> = DefaultFadeSpec, initialAlpha: Float = 0f): EnterTransition =
    EnterTransition(fade = true, scale = null, slide = null)

/** Grows the content from smaller (Raven's Pop), or shrinks it from larger (Zoom), by [initialScale]. */
public fun scaleIn(animationSpec: AnimationSpec<Float> = DefaultFadeSpec, initialScale: Float = 0f): EnterTransition =
    EnterTransition(
        fade = false,
        scale = when {
            initialScale < 1f -> AppearTransition.Pop
            initialScale > 1f -> AppearTransition.Zoom
            else -> null
        },
        slide = null,
    )

/**
 * Brings the content in vertically from [initialOffsetY] (given the content's height): from above
 * (a negative offset, the default) is Raven's FlyDown, from below FlyUp.
 */
public fun slideInVertically(
    animationSpec: AnimationSpec<Any?> = spring(),
    initialOffsetY: (fullHeight: Int) -> Int = { -it / 2 },
): EnterTransition {
    val offset = initialOffsetY(NOMINAL_SIZE)
    val slide = when {
        offset < 0 -> AppearTransition.FlyDown
        offset > 0 -> AppearTransition.FlyUp
        else -> null
    }
    return EnterTransition(fade = false, scale = null, slide = slide)
}

/**
 * Brings the content in horizontally from [initialOffsetX] (given the content's width): from the
 * left (a negative offset, the default) is Raven's FlyRight, from the right FlyLeft.
 */
public fun slideInHorizontally(
    animationSpec: AnimationSpec<Any?> = spring(),
    initialOffsetX: (fullWidth: Int) -> Int = { -it / 2 },
): EnterTransition {
    val offset = initialOffsetX(NOMINAL_SIZE)
    val slide = when {
        offset < 0 -> AppearTransition.FlyRight
        offset > 0 -> AppearTransition.FlyLeft
        else -> null
    }
    return EnterTransition(fade = false, scale = null, slide = slide)
}

/** Fades the content out to [targetAlpha] with [animationSpec], then removes it. */
public fun fadeOut(animationSpec: AnimationSpec<Float> = DefaultFadeSpec, targetAlpha: Float = 0f): ExitTransition {
    require(targetAlpha in 0f..1f) { "targetAlpha must be between 0 and 1, not $targetAlpha" }
    return ExitTransition(ExitTransition.Fade(animationSpec, targetAlpha))
}

/** Falls back to [fadeOut]'s default fade: Raven has no transitions out. */
public fun scaleOut(animationSpec: AnimationSpec<Float> = DefaultFadeSpec, targetScale: Float = 0f): ExitTransition =
    ExitTransition(DefaultFade)

/** Falls back to [fadeOut]'s default fade: Raven has no transitions out. */
public fun slideOutVertically(
    animationSpec: AnimationSpec<Any?> = spring(),
    targetOffsetY: (fullHeight: Int) -> Int = { -it / 2 },
): ExitTransition = ExitTransition(DefaultFade)

/** Falls back to [fadeOut]'s default fade: Raven has no transitions out. */
public fun slideOutHorizontally(
    animationSpec: AnimationSpec<Any?> = spring(),
    targetOffsetX: (fullWidth: Int) -> Int = { -it / 2 },
): ExitTransition = ExitTransition(DefaultFade)

// The size an offset lambda is given to learn its direction: only the sign of what it returns counts.
private const val NOMINAL_SIZE = 100
