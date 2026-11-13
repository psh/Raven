package raven.compose.animation.core

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A value that animates when told to, on compose-raven's frame clock: [animateTo] a target with a
 * spec, [snapTo] one, or [stop]. Its [value] is snapshot state, so what reads it recomposes as it
 * moves. Starting an animation cancels the one running, and the new one keeps the [velocity] the
 * value had, so a moving value changes course smoothly.
 *
 * It obeys [RavenAnimation]: its time runs at [RavenAnimation.speed], and while
 * [RavenAnimation.enabled] is false an animation jumps to its target.
 */
public class Animatable<T> internal constructor(
    initialValue: T,
    private val converter: VectorConverter<T>,
    private val visibilityThreshold: T?,
) {
    private val valueState = mutableStateOf(initialValue)

    /** Where the value is now. */
    public val value: T get() = valueState.value

    private var velocityVector = FloatArray(converter.size)

    /** How fast the value is moving, per second: zero at rest. */
    public val velocity: T get() = converter.fromVector(velocityVector)

    /** Where the last animation or snap sent the value. */
    public var targetValue: T by mutableStateOf(initialValue)
        private set

    /** Whether an animation is moving the value. */
    public var isRunning: Boolean by mutableStateOf(false)
        private set

    private val defaultSpring: SpringSpec<T> = spring(visibilityThreshold = visibilityThreshold)

    /**
     * Animates the value to [targetValue] with [animationSpec], starting at [initialVelocity] (the
     * value's own [velocity] by default), and returns when it gets there. A newer animation, a
     * snap or a stop cancels it, and this throws a [CancellationException].
     */
    public suspend fun animateTo(
        targetValue: T,
        animationSpec: AnimationSpec<T> = defaultSpring,
        initialVelocity: T = velocity,
    ): Unit = animateTo(targetValue, animationSpec, converter.toVector(initialVelocity))

    internal suspend fun animateTo(targetValue: T, animationSpec: AnimationSpec<T>, initialVelocity: FloatArray): Unit = mutate {
        this.targetValue = targetValue
        if (!RavenAnimation.enabled) {
            end(targetValue)
            return@mutate
        }
        isRunning = true
        val animation = VectorAnimation(
            animationSpec,
            start = converter.toVector(value),
            target = converter.toVector(targetValue),
            velocity = initialVelocity,
            threshold = threshold(animationSpec),
        )
        val current = FloatArray(converter.size)
        val currentVelocity = FloatArray(converter.size)
        var playTimeNanos = 0L
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            if (!RavenAnimation.enabled) break
            // Time runs at the app's animation speed, as Raven's own animations do.
            playTimeNanos += ((frameNanos - lastFrameNanos) * RavenAnimation.speed.toDouble()).toLong()
            lastFrameNanos = frameNanos
            val finished = animation.at(playTimeNanos, current, currentVelocity)
            valueState.value = converter.fromVector(current)
            currentVelocity.copyInto(velocityVector)
            if (finished) break
        }
        end(targetValue)
    }

    /** Puts the value at [targetValue] at once, at rest, cancelling any animation. */
    public suspend fun snapTo(targetValue: T): Unit = mutate {
        this.targetValue = targetValue
        end(targetValue)
    }

    /** Stops the animation running, if any, leaving the value where it is, at rest. */
    public suspend fun stop(): Unit = mutate { velocityVector = FloatArray(converter.size) }

    /** The velocity as it is, with no rounding to [T]: a colour's channels can't be negative. */
    internal fun velocityVector(): FloatArray = velocityVector.copyOf()

    /** The value as read-only [State], for `animate*AsState`. */
    internal fun asState(): State<T> = valueState

    private fun end(at: T) {
        valueState.value = at
        velocityVector = FloatArray(converter.size)
    }

    private fun threshold(spec: AnimationSpec<T>): FloatArray {
        val threshold = (spec as? SpringSpec<T>)?.visibilityThreshold ?: visibilityThreshold
        return threshold?.let { converter.toVector(it) } ?: FloatArray(converter.size) { converter.defaultThreshold }
    }

    // The job of the animation, snap or stop that last started, which the next one cancels, and the
    // lock that has each wait until the one before has finished cancelling.
    private var mutator: Job? = null
    private val mutex = Mutex()

    private suspend fun mutate(block: suspend () -> Unit) = coroutineScope {
        val job = coroutineContext.job
        val previous = mutator
        mutator = job
        previous?.cancel(CancellationException("Animatable: a newer animation took over"))
        try {
            mutex.withLock { block() }
        } finally {
            if (mutator === job) {
                mutator = null
                isRunning = false
            }
        }
    }
}

/** An [Animatable] Float, at rest at [initialValue]. Its springs end within [visibilityThreshold]. */
public fun Animatable(initialValue: Float, visibilityThreshold: Float = Spring.DefaultDisplacementThreshold): Animatable<Float> =
    Animatable(initialValue, FloatConverter, visibilityThreshold)
