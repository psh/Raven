@file:OptIn(InternalComposeRavenApi::class)

package raven.sample.viewportopengl

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.OpenGL3.GL_NO_ERROR
import platform.OpenGL3.GL_RGBA
import platform.OpenGL3.GL_UNSIGNED_BYTE
import platform.OpenGL3.glGetError
import platform.OpenGL3.glReadPixels
import platform.posix.getenv
import raven.compose.opengl.OpenGLTexture
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.RavenHost
import raven.compose.ui.Window
import raven.compose.ui.test.runRavenTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The cube drawn in OpenGL 3.2 core: offscreen always, in the viewport on a window with `RAVEN_GPU_TESTS=1`. */
class SceneTest {
    /** Draws the scene into a [size]-pixel square and reads it back, bottom row first, as GL does. */
    private fun drawn(size: Int, angle: Float, wireframe: Boolean): ByteArray {
        val rgba = ByteArray(size * size * 4)
        val texture = OpenGLTexture(size, size)
        val scene = checkNotNull(glScene())
        texture.render {
            scene.init()
            scene.draw(size, size, angle, wireframe)
            rgba.usePinned { glReadPixels(0, 0, size, size, GL_RGBA.toUInt(), GL_UNSIGNED_BYTE.toUInt(), it.addressOf(0)) }
            assertEquals(GL_NO_ERROR.toUInt(), glGetError())
            scene.dispose()
        }
        texture.close()
        return rgba
    }

    private fun ByteArray.rgb(size: Int, x: Int, y: Int) = (0 until 3).map { this[(y * size + x) * 4 + it].toInt() and 0xFF }

    private fun assertColour(expected: FloatArray, actual: List<Int>, what: String) {
        val wanted = expected.map { (it * 255 + 0.5f).toInt() }
        assertTrue(wanted.zip(actual).all { (w, a) -> abs(w - a) <= 2 }, "$what: $wanted, not $actual")
    }

    @Test
    fun theFrontFaceFillsTheMiddleOverTheBackground() = runRavenTest {
        setContent {}
        val pixels = drawn(64, angle = 0f, wireframe = false)
        assertColour(faceColors[0], pixels.rgb(64, 32, 32), "the front face, the C example's first colour")
        assertColour(clearColor, pixels.rgb(64, 1, 1), "the background")
    }

    @Test
    fun inWireframeTheMiddleIsTheBackground() = runRavenTest {
        setContent {}
        val pixels = drawn(64, angle = 0f, wireframe = true)
        assertColour(clearColor, pixels.rgb(64, 32, 32), "only the quads' edges are drawn, no diagonal")
        assertTrue((0 until 64).any { x -> pixels.rgb(64, x, 32) != pixels.rgb(64, 32, 32) }, "an edge crosses the middle row")
    }

    @Test
    fun turnedTheMiddleShowsAnotherFace() = runRavenTest {
        setContent {}
        val front = drawn(64, angle = 0f, wireframe = false).rgb(64, 32, 32)
        val turned = drawn(64, angle = 2.25f, wireframe = false).rgb(64, 32, 32)
        assertNotEquals(front, turned, "90 degrees about mostly y")
    }

    @Test
    fun onAWindowTheViewportGivesItsRenderer() {
        if (getenv("RAVEN_GPU_TESTS")?.toKString() != "1") return
        val state = SceneState()
        val host = RavenHost(headless = false, animationTimeStep = 1.0 / 60, followSystemAccessibility = false)
        try {
            host.start { Window(onCloseRequest = {}, title = "OpenGL viewport") { ViewportScreen(state) } }
            repeat(30) { if (!host.frame()) return@repeat }
        } finally {
            host.close()
        }
        host.error?.let { throw it }
        assertTrue(!state.renderer.isNullOrEmpty(), "GL_RENDERER: ${state.renderer}")
        assertTrue(state.angle > 0f)
    }
}
