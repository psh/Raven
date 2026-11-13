package raven.compose.opengl

import kotlinx.cinterop.toKString
import platform.OpenGL3.GL_COLOR_BUFFER_BIT
import platform.OpenGL3.GL_NO_ERROR
import platform.OpenGL3.GL_SCISSOR_TEST
import platform.OpenGL3.glClear
import platform.OpenGL3.glClearColor
import platform.OpenGL3.glDisable
import platform.OpenGL3.glEnable
import platform.OpenGL3.glGetError
import platform.OpenGL3.glScissor
import platform.posix.getenv
import raven.compose.foundation.layout.size
import raven.compose.ui.Modifier
import raven.compose.ui.RavenHost
import raven.compose.ui.Window
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The OpenGL module: an offscreen texture always, the viewport on a real window with `RAVEN_GPU_TESTS=1`. */
class OpenGLTest {
    private fun runHost(headless: Boolean, frames: Int, content: @androidx.compose.runtime.Composable () -> Unit = {}, block: () -> Unit = {}) {
        val host = RavenHost(headless = headless, animationTimeStep = 1.0 / 60, followSystemAccessibility = false)
        try {
            host.start { Window(onCloseRequest = {}, title = "OpenGL") { content() } }
            block()
            repeat(frames) { if (!host.frame()) return@repeat }
        } finally {
            host.close()
        }
        host.error?.let { throw it }
    }

    private fun ByteArray.pixel(texture: OpenGLTexture, x: Int, y: Int): List<Int> =
        (0 until 4).map { this[(y * texture.width + x) * 4 + it].toInt() and 0xFF }

    @Test
    fun aClearedFrameIsReadBackTopRowFirst() = runHost(headless = true, frames = 1) {
        val texture = OpenGLTexture(8, 4)
        texture.render {
            glDisable(GL_SCISSOR_TEST.toUInt())
            glClearColor(0f, 1f, 0f, 1f)
            glClear(GL_COLOR_BUFFER_BIT.toUInt())
            // GL's y goes up: the top row, y = 3, red.
            glEnable(GL_SCISSOR_TEST.toUInt())
            glScissor(0, 3, 8, 1)
            glClearColor(1f, 0f, 0f, 1f)
            glClear(GL_COLOR_BUFFER_BIT.toUInt())
            glDisable(GL_SCISSOR_TEST.toUInt())
            assertEquals(GL_NO_ERROR.toUInt(), glGetError())
        }
        assertEquals(listOf(255, 0, 0, 255), texture.pixels.pixel(texture, 0, 0), "the top row first")
        assertEquals(listOf(0, 255, 0, 255), texture.pixels.pixel(texture, 7, 3))
        assertEquals(8, texture.image.width)

        texture.resize(2, 2)
        texture.render {
            glClearColor(0f, 0f, 1f, 1f)
            glClear(GL_COLOR_BUFFER_BIT.toUInt())
        }
        assertEquals(listOf(0, 0, 255, 255), texture.pixels.pixel(texture, 1, 1))
        assertEquals(2, texture.image.width)
        texture.close()
    }

    @Test
    fun aHeadlessViewportMakesNoContext() {
        val warnings = mutableListOf<String>()
        val sink = layoutWarningSink
        layoutWarningSink = { warnings += it }
        var initialised = false
        try {
            runHost(headless = true, frames = 3, content = {
                OpenGLViewport(Modifier.size(32.dp), onInit = { initialised = true }, onDraw = { _, _ -> })
            })
        } finally {
            layoutWarningSink = sink
        }
        assertFalse(initialised)
        assertEquals(listOf("NativeViewport: Raven made no native child window here"), warnings)
    }

    @Test
    fun onAWindowTheViewportDraws() {
        if (getenv("RAVEN_GPU_TESTS")?.toKString() != "1") return
        val events = mutableListOf<String>()
        var drawnAt = 0 to 0
        runHost(headless = false, frames = 30, content = {
            OpenGLViewport(
                Modifier.size(64.dp),
                onInit = { events += "init" },
                onDraw = { width, height ->
                    drawnAt = width to height
                    glClearColor(0f, 1f, 0f, 1f)
                    glClear(GL_COLOR_BUFFER_BIT.toUInt())
                    if (glGetError() != GL_NO_ERROR.toUInt()) events += "error"
                },
                onDispose = { events += "dispose" },
            )
        })
        assertEquals(listOf("init", "dispose"), events)
        assertTrue(drawnAt.first >= 64 && drawnAt.second >= 64, "in pixels: $drawnAt")
    }
}
