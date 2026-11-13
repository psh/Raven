package raven.compose.vulkan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.pointed
import kotlinx.cinterop.staticCFunction
import raven.cinterop.RViewportDrawInfo
import raven.cinterop.R_FALSE
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_VIEWPORT
import raven.cinterop.Rid
import raven.cinterop.Rviewport_set_draw_callback
import raven.cinterop.Rviewport_set_realtime
import raven.cinterop.Rwidget_is_valid
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.rememberErrorReporter
import kotlin.concurrent.AtomicReference

/**
 * One frame of a [VulkanViewport], as Raven records it on its render thread: [commandBuffer] is the
 * `VkCommandBuffer` being recorded, inside [renderPass] (one colour attachment of [colorFormat], no
 * depth). The viewport covers [x], [y], [width] by [height] pixels of the window's framebuffer, of
 * which the part from [clipX], [clipY], [clipWidth] by [clipHeight] is shown, already the scissor.
 */
public class VulkanFrame internal constructor(
    public val commandBuffer: COpaquePointer?,
    public val renderPass: COpaquePointer?,
    public val colorFormat: UInt,
    public val x: Int,
    public val y: Int,
    public val width: Int,
    public val height: Int,
    public val clipX: Int,
    public val clipY: Int,
    public val clipWidth: Int,
    public val clipHeight: Int,
    public val framebufferWidth: Int,
    public val framebufferHeight: Int,
    public val frameIndex: Long,
)

private val VulkanViewportWidget = WidgetDescriptor("VulkanViewport", R_WIDGET_VIEWPORT)

/** What a VulkanViewport draws, read on the render thread, and where what it throws goes. */
private class VulkanDrawer(val reportError: (Throwable) -> Unit) {
    val onDraw = AtomicReference<(VulkanFrame) -> Unit>({})
    val ref: StableRef<VulkanDrawer> = StableRef.create(this)
    var widget: Rid = R_INVALID_ID
}

private val Drawer = Property<VulkanDrawer> { widget, drawer ->
    drawer.widget = widget
    Rviewport_set_draw_callback(widget, onVulkanDraw, drawer.ref.asCPointer())
}
private val Realtime = Property<Boolean> { widget, realtime -> Rviewport_set_realtime(widget, if (realtime) R_TRUE else R_FALSE) }

private val onVulkanDraw = staticCFunction { info: CPointer<RViewportDrawInfo>?, userData: COpaquePointer? ->
    val drawer = userData!!.asStableRef<VulkanDrawer>().get()
    try {
        val frame = with(info!!.pointed) {
            VulkanFrame(
                command_buffer, render_pass, color_format, x, y, width, height,
                clip_x, clip_y, clip_width, clip_height, framebuffer_width, framebuffer_height, frame_index.toLong(),
            )
        }
        drawer.onDraw.value(frame)
    } catch (t: Throwable) {
        // Nothing crosses into C: the host's error path ends the app, from this thread too.
        drawer.reportError(t)
    }
}

/**
 * A viewport [onDraw] draws with Vulkan, inside Raven's own frame: on Raven's render thread, it
 * records commands into the frame's command buffer, under the UI drawn after it. **[onDraw] runs on
 * the render thread**, so it must not write Compose state or call Raven's UI API; what it throws
 * ends the app through the host's error path. With [realtime] the window draws continuously while
 * the viewport is shown; otherwise it draws when the UI does.
 */
@Composable
public fun VulkanViewport(
    modifier: Modifier = Modifier,
    realtime: Boolean = false,
    onDraw: (VulkanFrame) -> Unit,
) {
    val reportError = rememberErrorReporter()
    val drawer = remember(reportError) { VulkanDrawer(reportError) }
    SideEffect { drawer.onDraw.value = onDraw }
    DisposableEffect(drawer) {
        onDispose {
            // The render thread can't call it any more once the callback is gone with the widget.
            if (Rwidget_is_valid(drawer.widget) == R_TRUE) Rviewport_set_draw_callback(drawer.widget, null, null)
            drawer.ref.dispose()
        }
    }
    RavenWidget(VulkanViewportWidget, modifier, {
        bind(drawer, Drawer)
        bind(realtime, Realtime)
    })
}
