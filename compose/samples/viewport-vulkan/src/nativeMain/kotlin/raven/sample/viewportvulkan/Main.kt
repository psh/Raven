package raven.sample.viewportvulkan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import raven.compose.foundation.Button
import raven.compose.foundation.Switch
import raven.compose.foundation.Text
import raven.compose.foundation.Viewport
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.HorizontalSplitter
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.padding
import raven.compose.ui.Alignment
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.alpha
import raven.compose.ui.application
import raven.compose.ui.rememberWindowState
import raven.compose.ui.testTag
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp
import raven.compose.vulkan.RavenVulkan
import raven.compose.vulkan.VulkanViewport

// A port of Raven's examples/viewport_vulkan: a Vulkan scene shown inside the UI in the two ways
// raven_viewport.h offers, on Raven's own device (RavenVulkan.device).
//
//   left   Viewport(ImageBitmap.fromVulkanImageView(...))   the app renders into its own image, and
//                                                           Raven samples it as a texture, no copy
//   right  VulkanViewport(onDraw)                           Raven calls the app while it records its
//                                                           frame, on its render thread
//
// Raven renders on its own thread and a VkQueue can't be used by two threads at once, so the app
// and Raven take one lock around every use of the queue (QueueLock), installed before Raven starts.
// The Vulkan calls come from a cinterop local to the sample (compose-raven-vulkan ships no Vulkan
// bindings), resolved on Raven's device rather than linked (VulkanFunctions). The shaders are the C
// example's SPIR-V, read from its shaders_spv.h.

fun main() {
    QueueLock.install()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(size = DpSize(960.dp, 540.dp)),
            title = "Raven - Vulkan viewports",
        ) { VulkanScreen() }
    }
}

/** What the controls set. */
class ScreenState {
    var paused by mutableStateOf(false)
}

/** The C example's info label. */
const val INFO: String = "Both viewports are clipped, resized and covered by widgets like any other widget."

/** Shown in place of the views where Raven isn't rendering with Vulkan (a headless host). */
const val NO_VULKAN: String = "Raven isn't rendering with Vulkan here: there is no device to draw with."

@Composable
fun VulkanScreen(state: ScreenState = remember { ScreenState() }, onScene: (VulkanScene?) -> Unit = {}) {
    // Raven's device, while Raven runs on Vulkan; none in a headless host.
    val scene = remember { RavenVulkan.device?.let { VulkanScene(it, readTriangleShaders()) } }
    // First made, so last gone: after the viewports, before Raven's device.
    DisposableEffect(scene) {
        onScene(scene)
        onDispose { scene?.close() }
    }
    // The C example's frame callback: the angle moves unless paused, and the image is rendered.
    if (scene != null) {
        LaunchedEffect(scene, state.paused) {
            var last = withFrameNanos { it }
            scene.renderOffscreen()
            if (state.paused) return@LaunchedEffect
            while (true) {
                val now = withFrameNanos { it }
                scene.advance((now - last) / 1e9f)
                scene.renderOffscreen()
                last = now
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(state.paused, { state.paused = it }, Modifier.testTag("pause"))
            Text("Pause", Modifier.align(Alignment.CenterVertically))
            Text(if (scene == null) NO_VULKAN else INFO, Modifier.align(Alignment.CenterVertically).alpha(0.7f).testTag("info"))
        }
        HorizontalSplitter(Modifier.weight(1f).fillMaxWidth()) {
            Pane("Texture: offscreen image of the application") {
                if (scene != null) {
                    Viewport(scene.image, Modifier.fillMaxSize().testTag("texture"), onSizeChanged = { scene.resize(it.width, it.height) })
                }
            }
            Pane("Draw callback: drawn in the render pass of the window") {
                // Realtime while it turns: Raven draws the window every frame, as the C example's
                // Realtime="true", which its Pause switch turns off.
                VulkanViewport(Modifier.fillMaxSize().testTag("callback"), realtime = !state.paused) { frame -> scene?.drawInPass(frame) }
            }
        }
    }
}

/** One side: its caption over the view, and a button over the scene. */
@Composable
private fun Pane(caption: String, view: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(caption)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            view()
            Button(onClick = {}, Modifier.align(Alignment.BottomEnd).padding(12.dp)) { Text("UI over the scene") }
        }
    }
}
