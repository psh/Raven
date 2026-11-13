package raven.compose.animation.core

import raven.compose.ui.geometry.Offset
import raven.compose.ui.graphics.Color
import raven.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

// The arithmetic of the animations: each animated type is a vector of floats, and a spec moves each
// component on its own.

/** Converts a [T] to the [size] floats it animates as, and back. */
internal class VectorConverter<T>(
    val size: Int,
    val toVector: (T) -> FloatArray,
    val fromVector: (FloatArray) -> T,
    /** How close each component comes to its target before a spring ends, unless the spec says. */
    val defaultThreshold: Float,
)

internal val FloatConverter = VectorConverter(1, { floatArrayOf(it) }, { it[0] }, Spring.DefaultDisplacementThreshold)

internal val DpConverter = VectorConverter(1, { floatArrayOf(it.value) }, { Dp(it[0]) }, 0.1f)

internal val OffsetConverter = VectorConverter(2, { floatArrayOf(it.x, it.y) }, { Offset(it[0], it[1]) }, 0.5f)

/**
 * Colours move channel by channel in sRGB, where Compose moves them in Oklab, so a colour passing
 * between two hues can look duller halfway. Each channel ends within one 8-bit step.
 */
internal val ColorConverter = VectorConverter(
    4,
    { color ->
        require(color != Color.Unspecified) { "Color.Unspecified can't be animated" }
        floatArrayOf(color.red, color.green, color.blue, color.alpha)
    },
    { Color(it[0], it[1], it[2], it[3]) },
    1f / 255,
)

/**
 * One run of [spec] from [start], moving at [velocity], to [target], each a vector of the same
 * size. [at] gives the value and velocity at a play time, from the start alone, so a run is the
 * same whatever the frames it is sampled at.
 */
internal class VectorAnimation(
    private val spec: AnimationSpec<*>,
    private val start: FloatArray,
    private val target: FloatArray,
    private val velocity: FloatArray,
    private val threshold: FloatArray,
) {
    /**
     * Writes the value and velocity (per second) at [playTimeNanos] into [value] and [velocityOut],
     * and returns whether the animation has ended there. An ended animation is at its target.
     */
    fun at(playTimeNanos: Long, value: FloatArray, velocityOut: FloatArray): Boolean = when (spec) {
        is SpringSpec<*> -> springAt(spec, playTimeNanos / 1e9, value, velocityOut)
        is TweenSpec<*> -> tweenAt(spec, playTimeNanos / 1e6, value, velocityOut)
    }

    private fun springAt(spec: SpringSpec<*>, seconds: Double, value: FloatArray, velocityOut: FloatArray): Boolean {
        var settled = true
        for (i in start.indices) {
            val (x, v) = spring(spec.stiffness.toDouble(), spec.dampingRatio.toDouble(), start[i] - target[i].toDouble(), velocity[i].toDouble(), seconds)
            value[i] = (target[i] + x).toFloat()
            velocityOut[i] = v.toFloat()
            // Within the threshold, and moving less than that in a frame.
            if (abs(x) >= threshold[i] || abs(v) * FRAME_SECONDS >= threshold[i]) settled = false
        }
        if (settled) settle(value, velocityOut)
        return settled
    }

    private fun tweenAt(spec: TweenSpec<*>, millis: Double, value: FloatArray, velocityOut: FloatArray): Boolean {
        fun fraction(at: Double): Float {
            if (spec.durationMillis == 0) return if (at >= spec.delay) 1f else 0f
            return spec.easing.transform(((at - spec.delay) / spec.durationMillis).coerceIn(0.0, 1.0).toFloat())
        }
        if (millis >= spec.delay + spec.durationMillis) {
            settle(value, velocityOut)
            return true
        }
        val now = fraction(millis)
        val before = fraction(millis - 1)
        for (i in start.indices) {
            val distance = target[i] - start[i]
            value[i] = start[i] + distance * now
            // Over the last millisecond, per second.
            velocityOut[i] = distance * (now - before) * 1000f
        }
        return false
    }

    private fun settle(value: FloatArray, velocityOut: FloatArray) {
        target.copyInto(value)
        velocityOut.fill(0f)
    }

    private companion object {
        const val FRAME_SECONDS = 1.0 / 60

        /**
         * A damped spring's displacement and velocity after [t] seconds, from [x0] and [v0]: the
         * closed form, as Compose's `SpringSimulation`, for each kind of damping.
         */
        fun spring(stiffness: Double, damping: Double, x0: Double, v0: Double, t: Double): Pair<Double, Double> {
            val omega = sqrt(stiffness)
            return when {
                damping > 1 -> {
                    val root = omega * sqrt(damping * damping - 1)
                    val gammaPlus = -damping * omega + root
                    val gammaMinus = -damping * omega - root
                    val b = (gammaMinus * x0 - v0) / (gammaMinus - gammaPlus)
                    val a = x0 - b
                    val ea = exp(gammaMinus * t)
                    val eb = exp(gammaPlus * t)
                    (a * ea + b * eb) to (a * gammaMinus * ea + b * gammaPlus * eb)
                }
                damping == 1.0 -> {
                    val b = v0 + omega * x0
                    val decay = exp(-omega * t)
                    val x = (x0 + b * t) * decay
                    x to (b * decay - omega * x)
                }
                else -> {
                    val damped = omega * sqrt(1 - damping * damping)
                    val sinCoefficient = (damping * omega * x0 + v0) / damped
                    val decay = exp(-damping * omega * t)
                    val cosine = cos(damped * t)
                    val sine = sin(damped * t)
                    val x = decay * (x0 * cosine + sinCoefficient * sine)
                    x to (-damping * omega * x + decay * damped * (sinCoefficient * cosine - x0 * sine))
                }
            }
        }
    }
}
