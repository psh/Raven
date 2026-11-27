@file:OptIn(InternalComposeRavenApi::class)

package raven.sample.viewportopengl

import raven.cinterop.R_TRUE
import raven.cinterop.Rcheckbox_is_checked
import raven.cinterop.Rslider_get_value
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.test.runRavenTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The controls, the headless viewport, and the cube's matrices. */
class ViewportOpenGLTest {
    private val defaultSink = layoutWarningSink

    @AfterTest
    fun restore() {
        layoutWarningSink = defaultSink
    }

    @Test
    fun theControlsSetTheScenesState() = runRavenTest {
        val state = SceneState()
        setContent { ViewportScreen(state) }
        assertEquals(1f, Rslider_get_value(onNodeWithTag("speed").fetchWidget()), "the C example's Value=\"1\"")
        onNodeWithTag("wireframe").performClick()
        waitForIdle()
        assertTrue(state.wireframe)
        assertEquals(R_TRUE, Rcheckbox_is_checked(onNodeWithTag("wireframe").fetchWidget()))
        state.speed = 3f
        waitForIdle()
        assertEquals(3f, Rslider_get_value(onNodeWithTag("speed").fetchWidget()))
    }

    @Test
    fun headlessTheViewportWarnsAndTheLabelSaysSo() {
        val warnings = mutableListOf<String>()
        layoutWarningSink = { warnings += it }
        runRavenTest {
            val state = SceneState()
            setContent { ViewportScreen(state) }
            frame()
            assertEquals(null, state.renderer, "no context, so no onInit")
            onNodeWithTag("info").assertTextEquals(NO_CONTEXT)
        }
        assertEquals(listOf("NativeViewport: Raven made no native child window here"), warnings)
    }

    @Test
    fun theTurningAngleAdvancesWithTheSpeed() = runRavenTest {
        val state = SceneState()
        setContent { ViewportScreen(state) }
        repeat(10) { frame() }
        assertTrue(state.angle > 0f, "turning at speed 1: ${state.angle}")
        state.speed = 0f
        waitForIdle()
        val stopped = state.angle
        repeat(10) { frame() }
        assertEquals(stopped, state.angle, "at speed 0 it stays")
    }

    @Test
    fun theTransformIsTheCExamplesFrustumTranslationAndRotation() {
        // At angle 0 the front face's centre (0, 0, 1) is 4 units ahead: the middle of the view.
        val m = cubeTransform(200, 100, 0f)
        val clipX = m[8] * 1f + m[12]
        val clipW = m[11] * 1f + m[15]
        assertEquals(0f, clipX / clipW, 1e-5f)
        assertEquals(4f, clipW, 1e-5f, "w is the distance")
        // glFrustum(-0.1 * aspect, 0.1 * aspect, ...): x scaled by 2n / (r - l) = 0.4 / 0.4.
        assertEquals(1f, m[0], 1e-5f)
        assertEquals(2f, m[5], 1e-5f)
        assertEquals((CUBE_TRIANGLE_VERTICES + CUBE_EDGE_VERTICES) * 6, cubeVertices().size)
    }
}
