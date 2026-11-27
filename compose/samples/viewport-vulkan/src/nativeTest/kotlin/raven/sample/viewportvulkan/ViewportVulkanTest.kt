@file:OptIn(InternalComposeRavenApi::class)

package raven.sample.viewportvulkan

import kotlinx.cinterop.toKString
import platform.posix.getenv
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_VIEWPORT
import raven.cinterop.Rcheckbox_is_checked
import raven.cinterop.Rwidget_get_type
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.RavenHost
import raven.compose.ui.Window
import raven.compose.ui.test.runRavenTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The shaders, the controls and the headless screen; both views drawing with `RAVEN_GPU_TESTS=1`. */
class ViewportVulkanTest {
    @Test
    fun theCExamplesSpirvIsReadFromItsHeader() {
        val shaders = readTriangleShaders()
        assertEquals(642, shaders.vertex.size, "triangle_vert_spv[642]")
        assertEquals(124, shaders.fragment.size, "triangle_frag_spv[124]")
        assertEquals(0x07230203u, shaders.vertex[0])
        assertEquals(0x07230203u, shaders.fragment[0])
    }

    @Test
    fun aMalformedArrayIsRefused() {
        assertFailsWith<IllegalStateException> { spirvArray("static const uint32_t a[3] = { 0x07230203, 0x1 };", "a") }
        assertFailsWith<IllegalArgumentException> { spirvArray("", "a") }
    }

    @Test
    fun headlessNeitherViewDrawsAndThePauseSwitchWorks() = runRavenTest {
        val state = ScreenState()
        val scenes = mutableListOf<VulkanScene?>()
        setContent { VulkanScreen(state, onScene = { scenes += it }) }
        repeat(3) { frame() }
        assertEquals(listOf<VulkanScene?>(null), scenes, "no Vulkan device headless, so no scene")
        onNodeWithTag("info").assertTextEquals(NO_VULKAN)
        assertEquals(R_WIDGET_VIEWPORT, Rwidget_get_type(onNodeWithTag("callback").fetchWidget()))
        onNodeWithTag("pause").performClick()
        waitForIdle()
        assertTrue(state.paused)
        assertEquals(R_TRUE, Rcheckbox_is_checked(onNodeWithTag("pause").fetchWidget()))
    }

    @Test
    fun onAWindowBothViewsDraw() {
        if (getenv("RAVEN_GPU_TESTS")?.toKString() != "1") return
        QueueLock.install()
        var scene: VulkanScene? = null
        val host = RavenHost(headless = false, animationTimeStep = 1.0 / 60, followSystemAccessibility = false)
        var offscreen = 0
        var pass = 0
        try {
            host.start { Window(onCloseRequest = {}, title = "Vulkan viewports") { VulkanScreen(onScene = { scene = it }) } }
            repeat(30) { if (!host.frame()) return@repeat }
            offscreen = scene?.offscreenFrames ?: 0
            pass = scene?.passFrames?.value ?: 0
        } finally {
            host.close()
        }
        host.error?.let { throw it }
        assertTrue(scene != null, "Raven's Vulkan device")
        assertTrue(offscreen > 0, "the app's image was rendered: $offscreen")
        assertTrue(pass > 0, "the callback drew in Raven's pass: $pass")
    }
}
