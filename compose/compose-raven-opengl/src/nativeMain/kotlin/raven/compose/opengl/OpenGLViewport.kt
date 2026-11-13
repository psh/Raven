package raven.compose.opengl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.channels.Channel
import raven.compose.foundation.NativeViewport
import raven.compose.ui.Modifier
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.unit.IntSize

/** A viewport's context, its size in pixels, and whether it must draw again. */
private class GlViewportState {
    var context: GlContext? = null
    var size = IntSize(0, 0)
    val dirty = Channel<Unit>(Channel.CONFLATED)
    val observer = SnapshotStateObserver { it() }
    private val invalidate: (Unit) -> Unit = { dirty.trySend(Unit) }

    fun draw(onDraw: (Int, Int) -> Unit) {
        val context = context ?: return
        if (size.width <= 0 || size.height <= 0) return
        context.makeCurrent()
        observer.observeReads(Unit, invalidate) { onDraw(size.width, size.height) }
        context.flush()
    }
}

/**
 * A viewport drawn with OpenGL, on the UI thread, in a context the module makes on a native child
 * window (`NativeViewport`), so it sits above the UI and isn't clipped by it. [onInit] runs once the
 * context exists and is current; [onDraw] gets the size in pixels and draws, and [onDispose] runs,
 * the context current, as the viewport leaves. [onDraw] runs again when state it read changes, or
 * when the viewport is resized, and every frame with [realtime]. The app brings its own GL bindings.
 * Where no context can be made (a headless host, Linux for now), nothing runs, with a warning.
 */
@Composable
public fun OpenGLViewport(
    modifier: Modifier = Modifier,
    realtime: Boolean = false,
    onInit: () -> Unit = {},
    onDraw: (width: Int, height: Int) -> Unit,
    onDispose: () -> Unit = {},
) {
    val state = remember { GlViewportState() }
    val currentOnInit by rememberUpdatedState(onInit)
    val currentOnDraw by rememberUpdatedState(onDraw)
    val currentOnDispose by rememberUpdatedState(onDispose)
    NativeViewport(
        modifier,
        onCreated = { handle ->
            val context = glContextOn(handle)
            if (context == null) {
                layoutWarningSink("OpenGLViewport: no GL context could be made on the child window")
            } else {
                state.context = context
                context.makeCurrent()
                currentOnInit()
                state.dirty.trySend(Unit)
            }
        },
        onResize = { size ->
            state.size = size
            state.context?.update()
            state.dirty.trySend(Unit)
        },
    )
    DisposableEffect(state) {
        state.observer.start()
        onDispose {
            state.observer.stop()
            state.observer.clear()
            state.context?.let { context ->
                context.makeCurrent()
                currentOnDispose()
                context.close()
            }
            state.context = null
        }
    }
    // A new onDraw, its captured values changed, draws again too.
    LaunchedEffect(onDraw) { state.dirty.trySend(Unit) }
    LaunchedEffect(state, realtime) {
        while (true) {
            if (!realtime) state.dirty.receive()
            withFrameNanos { }
            state.draw(currentOnDraw)
        }
    }
}
