package raven.compose.animation

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Rget_animation_speed
import raven.cinterop.Rget_animations_enabled
import raven.cinterop.Rset_animation_speed
import raven.cinterop.Rset_animations_enabled
import raven.compose.animation.core.Animatable
import raven.compose.animation.core.LinearEasing
import raven.compose.animation.core.RavenAnimation
import raven.compose.animation.core.RavenMotion
import raven.compose.animation.core.animateColorAsState
import raven.compose.animation.core.animateDpAsState
import raven.compose.animation.core.animateFloatAsState
import raven.compose.animation.core.animateOffsetAsState
import raven.compose.animation.core.spring
import raven.compose.animation.core.tween
import raven.compose.foundation.RavenTestScope
import raven.compose.foundation.runRavenTest
import raven.compose.ui.geometry.Offset
import raven.compose.ui.graphics.Color
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** compose-raven's animations on the test's clock, which advances one fixed time step a frame. */
class AnimationTest {
    private lateinit var scope: CoroutineScope

    /** Hosts nothing but a coroutine scope, for animations launched by the test. */
    private fun RavenTestScope.setScopeContent() = setContent { scope = rememberCoroutineScope() }

    @Test
    fun aTweenAsStateTakesItsDurationOnTheClock() = runRavenTest {
        var target by mutableStateOf(0f)
        lateinit var state: State<Float>
        val finished = mutableListOf<Float>()
        setContent {
            state = animateFloatAsState(target, tween(100, easing = LinearEasing), finishedListener = { finished += it })
        }
        assertEquals(0f, state.value, "the first target, without animating")

        target = 100f
        waitForIdle()
        val start = mainClock.currentTime
        mainClock.advanceTimeBy(50)
        // Two frames from the start, one of them before the animation took its first step.
        assertTrue(state.value in 15f..60f, "halfway, not ${state.value}")
        while (state.value < 100f) {
            mainClock.advanceTimeByFrame()
            assertTrue(mainClock.currentTime - start < 200, "not ended after 200 ms")
        }
        assertTrue(mainClock.currentTime - start >= 100)
        waitForIdle()
        assertEquals(listOf(100f), finished)
    }

    @Test
    fun aSpringSettlesAndANewTargetChangesCourse() = runRavenTest {
        var target by mutableStateOf(0f)
        lateinit var state: State<Float>
        setContent { state = animateFloatAsState(target, RavenMotion.Bouncy) }
        target = 1f
        waitForIdle()
        var highest = 0f
        repeat(10) {
            mainClock.advanceTimeByFrame()
            highest = maxOf(highest, state.value)
        }
        // Moving up, it is sent back down: it changes course rather than jumping.
        target = 0f
        mainClock.advanceTimeByFrame()
        val turned = state.value
        assertTrue(turned > 0f && turned < 1.5f, "it goes on from where it was, not $turned")
        mainClock.advanceTimeBy(3000)
        assertEquals(0f, state.value)
        assertTrue(highest > 0.5f)
    }

    @Test
    fun theOtherTypesAnimateToo() = runRavenTest {
        var on by mutableStateOf(false)
        lateinit var dp: State<raven.compose.ui.unit.Dp>
        lateinit var color: State<Color>
        lateinit var offset: State<Offset>
        setContent {
            dp = animateDpAsState(if (on) 100.dp else 0.dp, tween(100, easing = LinearEasing))
            color = animateColorAsState(if (on) Color.White else Color.Black, tween(100, easing = LinearEasing))
            offset = animateOffsetAsState(if (on) Offset(10f, -10f) else Offset.Zero, spring())
        }
        on = true
        waitForIdle()
        mainClock.advanceTimeBy(50)
        assertTrue(dp.value.value in 10f..90f, "${dp.value}")
        assertTrue(color.value.red in 0.1f..0.9f && color.value.red == color.value.blue, "${color.value}")
        assertTrue(offset.value.x > 0f && offset.value.y < 0f, "${offset.value}")
        mainClock.advanceTimeBy(2000)
        assertEquals(100.dp, dp.value)
        assertEquals(Color.White, color.value)
        assertEquals(Offset(10f, -10f), offset.value)
    }

    @Test
    fun anAnimatableIsKickedAndANewerAnimationCancelsTheOlder() = runRavenTest {
        val animatable = Animatable(0f)
        setScopeContent()
        var cancelled = false
        scope.launch {
            try {
                animatable.animateTo(0f, RavenMotion.Bouncy, initialVelocity = 20f)
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw e
            }
        }
        mainClock.advanceTimeBy(50)
        assertTrue(animatable.isRunning)
        assertTrue(animatable.value > 0f, "kicked up, at ${animatable.value}")
        assertTrue(animatable.velocity != 0f)

        scope.launch { animatable.animateTo(-1f, tween(50)) }
        mainClock.advanceTimeBy(200)
        assertTrue(cancelled)
        assertEquals(-1f, animatable.value)
        assertEquals(-1f, animatable.targetValue)
        assertFalse(animatable.isRunning)
        assertEquals(0f, animatable.velocity)

        scope.launch { animatable.animateTo(5f, tween(1000)) }
        mainClock.advanceTimeBy(100)
        scope.launch { animatable.stop() }
        mainClock.advanceTimeByFrame()
        val stopped = animatable.value
        assertTrue(stopped > -1f && stopped < 5f)
        mainClock.advanceTimeBy(100)
        assertEquals(stopped, animatable.value)
        assertFalse(animatable.isRunning)

        scope.launch { animatable.snapTo(3f) }
        mainClock.advanceTimeByFrame()
        assertEquals(3f, animatable.value)
    }

    @Test
    fun theSpeedScalesTheirTimeAndRavens() = runRavenTest {
        val animatable = Animatable(0f)
        setScopeContent()
        assertEquals(1f, RavenAnimation.speed, "Raven's, as the host started")
        RavenAnimation.speed = 0.5f
        assertEquals(0.5f, Rget_animation_speed())

        scope.launch { animatable.animateTo(1f, tween(100, easing = LinearEasing)) }
        mainClock.advanceTimeBy(150)
        assertTrue(animatable.value in 0.4f..0.9f, "half speed: ${animatable.value} after 150 ms")
        mainClock.advanceTimeBy(100)
        assertEquals(1f, animatable.value)
    }

    @Test
    fun disabledAnimationsJumpToTheirTargets() = runRavenTest {
        var target by mutableStateOf(0f)
        lateinit var state: State<Float>
        setContent { state = animateFloatAsState(target, tween(1000)) }
        RavenAnimation.enabled = false
        assertEquals(R_FALSE, Rget_animations_enabled())
        target = 1f
        waitForIdle()
        mainClock.advanceTimeByFrame()
        assertEquals(1f, state.value)

        // Turned off mid-way, the animation ends at its target.
        RavenAnimation.enabled = true
        target = 2f
        waitForIdle()
        mainClock.advanceTimeBy(100)
        assertTrue(state.value < 2f)
        RavenAnimation.enabled = false
        mainClock.advanceTimeByFrame()
        assertEquals(2f, state.value)
    }

    @Test
    fun theSettingsFollowRaven() = runRavenTest {
        setScopeContent()
        Rset_animation_speed(0.25f)
        Rset_animations_enabled(R_FALSE)
        mainClock.advanceTimeByFrame()
        assertEquals(0.25f, RavenAnimation.speed)
        assertFalse(RavenAnimation.enabled)
        Rset_animations_enabled(R_TRUE)
        mainClock.advanceTimeByFrame()
        assertTrue(RavenAnimation.enabled)
    }
}
