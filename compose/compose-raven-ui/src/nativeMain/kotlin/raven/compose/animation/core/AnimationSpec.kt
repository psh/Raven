package raven.compose.animation.core

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlin.math.PI
import kotlin.math.abs

/** How a tween's progress, from 0 to 1, maps to the share of the way it has gone. */
@Stable
public fun interface Easing {
    public fun transform(fraction: Float): Float
}

/**
 * The easing of the cubic Bézier curve from (0, 0) to (1, 1) through the control points ([a], [b])
 * and ([c], [d]), as CSS's `cubic-bezier`.
 */
@Immutable
public class CubicBezierEasing(private val a: Float, private val b: Float, private val c: Float, private val d: Float) : Easing {
    init {
        require(a in 0f..1f && c in 0f..1f) { "the control points' x must be between 0 and 1, not $a and $c" }
    }

    override fun transform(fraction: Float): Float {
        if (fraction <= 0f) return 0f
        if (fraction >= 1f) return 1f
        // x(t) grows with t, since both control points' x lie in 0..1: find t by bisection.
        var low = 0f
        var high = 1f
        var t = fraction
        repeat(BISECTION_STEPS) {
            val x = bezier(a, c, t)
            if (abs(x - fraction) < BISECTION_ERROR) return bezier(b, d, t)
            if (x < fraction) low = t else high = t
            t = (low + high) / 2
        }
        return bezier(b, d, t)
    }

    override fun equals(other: Any?): Boolean =
        other is CubicBezierEasing && a == other.a && b == other.b && c == other.c && d == other.d

    override fun hashCode(): Int = ((a.hashCode() * 31 + b.hashCode()) * 31 + c.hashCode()) * 31 + d.hashCode()

    override fun toString(): String = "CubicBezierEasing($a, $b, $c, $d)"

    private companion object {
        const val BISECTION_STEPS = 32
        const val BISECTION_ERROR = 1e-5f

        /** One coordinate of the curve at [t], from 0 through [p1] and [p2] to 1. */
        fun bezier(p1: Float, p2: Float, t: Float): Float {
            val u = 1 - t
            return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t
        }
    }
}

/** Speeds up quickly and slows down gently: Compose's default, for things that move on screen. */
public val FastOutSlowInEasing: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/** Starts at full speed and slows down: for things that arrive. */
public val LinearOutSlowInEasing: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

/** Speeds up and leaves at full speed: for things that go. */
public val FastOutLinearInEasing: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

/** The same speed all the way. */
public val LinearEasing: Easing = Easing { it }

/**
 * How a value goes to its target: a [SpringSpec] or a [TweenSpec]. Covariant, so one spec without a
 * visibility threshold (a [RavenMotion]) serves every type.
 */
@Immutable
public sealed interface AnimationSpec<out T>

/**
 * A spring pulling the value to its target: [dampingRatio] 1 lands without overshooting, less
 * bounces; [stiffness] is how hard it pulls. It keeps the value's velocity when the target
 * changes, so a value that is moving goes on smoothly. The animation ends once the value is within
 * [visibilityThreshold] of the target (the type's default when null) and would move less than that
 * in a frame.
 */
@Immutable
public class SpringSpec<out T>(
    public val dampingRatio: Float = Spring.DampingRatioNoBouncy,
    public val stiffness: Float = Spring.StiffnessMedium,
    public val visibilityThreshold: T? = null,
) : AnimationSpec<T> {
    init {
        require(dampingRatio >= 0f) { "dampingRatio must not be negative, not $dampingRatio" }
        require(stiffness > 0f) { "stiffness must be positive, not $stiffness" }
    }

    override fun equals(other: Any?): Boolean =
        other is SpringSpec<*> && dampingRatio == other.dampingRatio && stiffness == other.stiffness &&
            visibilityThreshold == other.visibilityThreshold

    override fun hashCode(): Int = (dampingRatio.hashCode() * 31 + stiffness.hashCode()) * 31 + visibilityThreshold.hashCode()

    override fun toString(): String = "SpringSpec(dampingRatio=$dampingRatio, stiffness=$stiffness)"
}

/**
 * A tween from where the value is to its target in [durationMillis], after [delay] milliseconds,
 * along [easing]. It starts from rest: an initial velocity is ignored.
 */
@Immutable
public class TweenSpec<out T>(
    public val durationMillis: Int = DefaultDurationMillis,
    public val delay: Int = 0,
    public val easing: Easing = FastOutSlowInEasing,
) : AnimationSpec<T> {
    init {
        require(durationMillis >= 0 && delay >= 0) { "a tween's duration and delay must not be negative, not $durationMillis and $delay" }
    }

    override fun equals(other: Any?): Boolean =
        other is TweenSpec<*> && durationMillis == other.durationMillis && delay == other.delay && easing == other.easing

    override fun hashCode(): Int = (durationMillis * 31 + delay) * 31 + easing.hashCode()

    override fun toString(): String = "TweenSpec(durationMillis=$durationMillis, delay=$delay, easing=$easing)"
}

/** Compose's default duration, in milliseconds. */
public const val DefaultDurationMillis: Int = 300

/** A [SpringSpec]: the default spec of every animation. */
@Stable
public fun <T> spring(
    dampingRatio: Float = Spring.DampingRatioNoBouncy,
    stiffness: Float = Spring.StiffnessMedium,
    visibilityThreshold: T? = null,
): SpringSpec<T> = SpringSpec(dampingRatio, stiffness, visibilityThreshold)

/** A [TweenSpec]. */
@Stable
public fun <T> tween(
    durationMillis: Int = DefaultDurationMillis,
    delayMillis: Int = 0,
    easing: Easing = FastOutSlowInEasing,
): TweenSpec<T> = TweenSpec(durationMillis, delayMillis, easing)

/** Compose's spring constants. */
public object Spring {
    public const val StiffnessHigh: Float = 10_000f
    public const val StiffnessMedium: Float = 1500f
    public const val StiffnessMediumLow: Float = 400f
    public const val StiffnessLow: Float = 200f
    public const val StiffnessVeryLow: Float = 50f

    public const val DampingRatioHighBouncy: Float = 0.2f
    public const val DampingRatioMediumBouncy: Float = 0.5f
    public const val DampingRatioLowBouncy: Float = 0.75f
    public const val DampingRatioNoBouncy: Float = 1f

    /** How close a Float comes to its target before its spring ends. */
    public const val DefaultDisplacementThreshold: Float = 0.01f
}

/**
 * Raven's own motions as springs, so an app's values move as Raven's controls do. Each is Raven's
 * response (the period of a bounce, in seconds) and damping (`src/widget/anim.c`), with
 * `stiffness = (2π / response)²`. Kick one with a velocity:
 * `animatable.animateTo(target, RavenMotion.Bouncy, initialVelocity = v)`.
 */
public object RavenMotion {
    /** Quick and nearly without bounce: presses and small moves. */
    public val Snappy: SpringSpec<Nothing> = motion(response = 0.16f, damping = 0.90f)

    /** Unhurried, landing without bounce: what slides. */
    public val Smooth: SpringSpec<Nothing> = motion(response = 0.30f, damping = 1.0f)

    /** With a clear bounce: what pops in. */
    public val Bouncy: SpringSpec<Nothing> = motion(response = 0.38f, damping = 0.52f)

    /** Slow and bouncing several times. */
    public val Wobbly: SpringSpec<Nothing> = motion(response = 0.50f, damping = 0.32f)

    private fun motion(response: Float, damping: Float): SpringSpec<Nothing> {
        val omega = 2 * PI.toFloat() / response
        return SpringSpec(dampingRatio = damping, stiffness = omega * omega)
    }
}
