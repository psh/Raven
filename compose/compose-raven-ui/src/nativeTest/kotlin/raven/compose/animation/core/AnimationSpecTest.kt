package raven.compose.animation.core

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The specs and their arithmetic, with no Raven and no frames. */
class AnimationSpecTest {
    /** Samples [spec] from 0 to 1 at rest, each 1/60 s, until it ends; the values, the last at its end. */
    private fun run(spec: AnimationSpec<Float>, from: Float = 0f, to: Float = 1f, velocity: Float = 0f): List<Float> {
        val animation = VectorAnimation(spec, floatArrayOf(from), floatArrayOf(to), floatArrayOf(velocity), floatArrayOf(0.001f))
        val value = FloatArray(1)
        val velocityOut = FloatArray(1)
        val values = mutableListOf<Float>()
        for (frame in 0..600) {
            val finished = animation.at(frame * 1_000_000_000L / 60, value, velocityOut)
            values += value[0]
            if (finished) return values
        }
        error("not ended after 10 s")
    }

    @Test
    fun aSpringStartsWhereTheValueIsAndEndsAtItsTarget() {
        val values = run(spring())
        assertEquals(0f, values.first())
        assertEquals(1f, values.last())
        // Critically damped: it never overshoots.
        assertTrue(values.all { it in 0f..1f })
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun aBouncySpringOvershootsAndSettles() {
        val values = run(RavenMotion.Bouncy)
        assertTrue(values.max() > 1.05f)
        assertEquals(1f, values.last())
    }

    @Test
    fun anOverdampedSpringCreepsInWithoutOvershooting() {
        val values = run(spring(dampingRatio = 2f, stiffness = Spring.StiffnessHigh))
        assertTrue(values.all { it in 0f..1f })
        assertEquals(1f, values.last())
    }

    @Test
    fun anInitialVelocityKicksTheSpring() {
        // At its target already, it moves the way it is kicked, then comes back.
        val values = run(RavenMotion.Snappy, from = 0f, to = 0f, velocity = 10f)
        assertTrue(values[1] > 0f)
        assertEquals(0f, values.last())
    }

    @Test
    fun theRavenMotionsAreRavensResponseAndDamping() {
        fun stiffness(response: Float) = (2 * PI.toFloat() / response).let { it * it }
        assertEquals(stiffness(0.16f), RavenMotion.Snappy.stiffness)
        assertEquals(0.90f, RavenMotion.Snappy.dampingRatio)
        assertEquals(stiffness(0.30f), RavenMotion.Smooth.stiffness)
        assertEquals(1f, RavenMotion.Smooth.dampingRatio)
        assertEquals(stiffness(0.38f), RavenMotion.Bouncy.stiffness)
        assertEquals(0.52f, RavenMotion.Bouncy.dampingRatio)
        assertEquals(stiffness(0.50f), RavenMotion.Wobbly.stiffness)
        assertEquals(0.32f, RavenMotion.Wobbly.dampingRatio)
    }

    @Test
    fun aTweenFollowsItsEasingForItsDurationAfterItsDelay() {
        // 100 ms after 50 ms of delay, linear: frames are 1/60 s apart.
        val values = run(tween(durationMillis = 100, delayMillis = 50, easing = LinearEasing))
        assertEquals(listOf(0f, 0f, 0f), values.take(3))
        assertEquals((4000f / 60 - 50) / 100, values[4], 1e-4f)
        assertEquals(1f, values.last())
        assertEquals(10, values.size, "ended at the first frame after 150 ms")
        assertEquals(1, run(tween(durationMillis = 0)).size)
    }

    @Test
    fun theStandardEasingsStartAtZeroAndEndAtOne() {
        for (easing in listOf(FastOutSlowInEasing, LinearOutSlowInEasing, FastOutLinearInEasing, LinearEasing)) {
            assertEquals(0f, easing.transform(0f))
            assertEquals(1f, easing.transform(1f))
            val samples = (0..20).map { easing.transform(it / 20f) }
            assertTrue(samples.zipWithNext().all { (a, b) -> b >= a }, "$easing grows")
        }
        // Fast out: ahead of linear halfway; fast in: behind.
        assertTrue(FastOutSlowInEasing.transform(0.5f) > 0.5f)
        assertTrue(FastOutLinearInEasing.transform(0.5f) < 0.5f)
        assertEquals(0.5f, CubicBezierEasing(0.25f, 0.25f, 0.75f, 0.75f).transform(0.5f), 1e-4f)
    }

    @Test
    fun badSpecsAreRefused() {
        assertFailsWith<IllegalArgumentException> { spring<Float>(stiffness = 0f) }
        assertFailsWith<IllegalArgumentException> { spring<Float>(dampingRatio = -1f) }
        assertFailsWith<IllegalArgumentException> { tween<Float>(durationMillis = -1) }
        assertFailsWith<IllegalArgumentException> { CubicBezierEasing(-0.1f, 0f, 1f, 1f) }
        assertFalse(spring<Float>() == spring<Float>(dampingRatio = 0.5f))
        assertEquals(tween<Float>(100), tween<Float>(100))
    }
}
