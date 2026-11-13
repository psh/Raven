package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import raven.cinterop.RStretch
import raven.cinterop.R_FALSE
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_STRETCH_COVER
import raven.cinterop.R_STRETCH_FILL
import raven.cinterop.R_STRETCH_FIT
import raven.cinterop.R_STRETCH_NONE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_VIEWPORT
import raven.cinterop.Rid
import raven.cinterop.Rviewport_set_flip_y
import raven.cinterop.Rviewport_set_native
import raven.cinterop.Rviewport_set_stretch
import raven.cinterop.Rviewport_set_texture
import raven.cinterop.Rwidget_is_valid
import raven.compose.ui.Modifier
import raven.compose.ui.NativeWindowHandle
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.ViewportResized
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.canHaveNativeChild
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.ui.layout.ContentScale
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.nativeViewportHandle
import raven.compose.ui.unit.IntSize

private val ViewportWidget = WidgetDescriptor("Viewport", R_WIDGET_VIEWPORT, events = listOf(ViewportResized))

private val ContentScale.stretch: RStretch
    get() = when (this) {
        ContentScale.Fit -> R_STRETCH_FIT
        ContentScale.Crop -> R_STRETCH_COVER
        ContentScale.FillBounds -> R_STRETCH_FILL
        ContentScale.None -> R_STRETCH_NONE
    }

/** The image's texture, and the viewport as one of its viewers, so its `update` redraws it. */
private val ViewportImage = Property<ImageBitmap> { widget, image ->
    Rviewport_set_texture(widget, image.texture)
    image.addViewer(widget)
}
private val Stretch = Property<ContentScale> { widget, scale -> Rviewport_set_stretch(widget, scale.stretch) }
private val FlipY = Property<Boolean> { widget, flip -> Rviewport_set_flip_y(widget, if (flip) R_TRUE else R_FALSE) }

/**
 * Shows [image], fitted to the widget as [stretch] says (stretched to fill by default), upside down
 * with [flipY] (an image rendered by OpenGL). [onSizeChanged] gets the viewport's size in **pixels**,
 * the size to render at, as it changes. Updating the image redraws it: an app animating it updates
 * it from a `withFrameNanos` loop, so nothing redraws while it is still.
 */
@Composable
public fun Viewport(
    image: ImageBitmap,
    modifier: Modifier = Modifier,
    stretch: ContentScale = ContentScale.FillBounds,
    flipY: Boolean = false,
    onSizeChanged: ((IntSize) -> Unit)? = null,
) {
    val onResized: ((Int, Int) -> Unit)? = onSizeChanged?.let { report -> { width, height -> report(IntSize(width, height)) } }
    RavenWidget(ViewportWidget, modifier, {
        bind(image, ViewportImage)
        bind(stretch, Stretch)
        bind(flipY, FlipY)
        bind(onResized, ViewportResized)
    })
}

/** The viewport's widget, for the native child window made once it is in a window. */
private class NativeViewportState {
    var widget: Rid = R_INVALID_ID
}

private val NativeViewportOf = Property<NativeViewportState> { widget, state -> state.widget = widget }

/**
 * A native child window, for an app to render into with its own API: [onCreated] gets its handles
 * once it exists (see [NativeWindowHandle]; on macOS, the view and its `CAMetalLayer`), and
 * [onResize] its size in pixels as it changes. The child window sits above the UI and isn't clipped
 * by it. Where Raven can't make one (a headless host), [onCreated] is never called, with a warning.
 */
@Composable
public fun NativeViewport(
    modifier: Modifier = Modifier,
    onCreated: (NativeWindowHandle) -> Unit,
    onResize: (IntSize) -> Unit = {},
) {
    val state = remember { NativeViewportState() }
    val currentOnCreated by rememberUpdatedState(onCreated)
    val currentOnResize by rememberUpdatedState(onResize)
    RavenWidget(ViewportWidget, modifier, {
        bind(state, NativeViewportOf)
        bind({ width: Int, height: Int -> currentOnResize(IntSize(width, height)) }, ViewportResized)
    })
    // Raven makes the child window only for a viewport in a window: after the node is applied.
    DisposableEffect(state) {
        val made = canHaveNativeChild(state.widget) && Rviewport_set_native(state.widget, R_TRUE) == R_TRUE
        val handle = if (made) nativeViewportHandle(state.widget) else null
        if (handle == null) layoutWarningSink("NativeViewport: Raven made no native child window here") else currentOnCreated(handle)
        onDispose {
            if (made && Rwidget_is_valid(state.widget) == R_TRUE) Rviewport_set_native(state.widget, R_FALSE)
        }
    }
}
