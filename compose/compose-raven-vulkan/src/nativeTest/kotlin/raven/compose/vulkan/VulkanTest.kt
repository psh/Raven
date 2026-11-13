package raven.compose.vulkan

import kotlinx.cinterop.toKString
import platform.posix.getenv
import raven.compose.ui.Modifier
import raven.compose.ui.RavenHost
import raven.compose.ui.Window
import raven.compose.ui.unit.dp
import raven.compose.foundation.layout.size
import kotlin.concurrent.AtomicInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Vulkan module, headless, and on a real Vulkan device when `RAVEN_GPU_TESTS=1` (it opens a
 * window). Creating an image of its own would need Vulkan bindings, which the module leaves to the
 * app, so an external image isn't shown here.
 */
class VulkanTest {
    private fun runHost(headless: Boolean, frames: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
        val host = RavenHost(headless = headless, animationTimeStep = 1.0 / 60, followSystemAccessibility = false)
        try {
            host.start { Window(onCloseRequest = {}, title = "Vulkan") { content() } }
            repeat(frames) { if (!host.frame()) return@repeat }
        } finally {
            host.close()
        }
        host.error?.let { throw it }
    }

    @Test
    fun headlessThereIsNoDeviceAndNothingIsDrawn() {
        val drawn = AtomicInt(0)
        var device: VulkanDevice? = null
        runHost(headless = true, frames = 3) {
            device = RavenVulkan.device
            VulkanViewport(Modifier.size(64.dp), realtime = true) { drawn.incrementAndGet() }
        }
        assertNull(device)
        assertEquals(0, drawn.value, "the null backend has no render thread to draw on")
        assertTrue(VulkanSetup.requiredInstanceExtensions.isEmpty() || VulkanSetup.requiredInstanceExtensions.all { it.isNotEmpty() })
    }

    @Test
    fun onAVulkanDeviceAFrameIsRecorded() {
        if (getenv("RAVEN_GPU_TESTS")?.toKString() != "1") return
        val frames = AtomicInt(0)
        val withCommandBuffer = AtomicInt(0)
        var device: VulkanDevice? = null
        runHost(headless = false, frames = 30) {
            device = RavenVulkan.device
            VulkanViewport(Modifier.size(64.dp), realtime = true) { frame ->
                frames.incrementAndGet()
                if (frame.commandBuffer != null && frame.width > 0) withCommandBuffer.incrementAndGet()
            }
        }
        assertNotNull(device)
        assertTrue(frames.value > 0, "drawn on the render thread")
        assertEquals(frames.value, withCommandBuffer.value)
    }

    @Test
    fun anExceptionOnTheRenderThreadEndsTheApp() {
        if (getenv("RAVEN_GPU_TESTS")?.toKString() != "1") return
        val failure = runCatching {
            runHost(headless = false, frames = 30) {
                VulkanViewport(Modifier.size(64.dp), realtime = true) { error("drawing failed") }
            }
        }.exceptionOrNull()
        assertEquals("drawing failed", failure?.message)
    }
}
