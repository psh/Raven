package raven.sample.viewportopengl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import raven.compose.foundation.Checkbox
import raven.compose.foundation.Slider
import raven.compose.foundation.Text
import raven.compose.foundation.VerticalDivider
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.fillMaxHeight
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.padding
import raven.compose.foundation.layout.width
import raven.compose.opengl.OpenGLViewport
import raven.compose.ui.Alignment
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.alpha
import raven.compose.ui.application
import raven.compose.ui.rememberWindowState
import raven.compose.ui.testTag
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp

// A port of Raven's examples/viewport_opengl: an OpenGL scene in a Raven window, on the native child
// window of a viewport, so it sits above the UI and isn't clipped by it.
//
//   OpenGLViewport(onInit, onDraw, onDispose)   the C example's Rviewport_set_native, its context on
//                                               the child window, and its frame callback
//
// The C example draws its cube with OpenGL 1.1 (glBegin, glFrustum) on a WGL or GLX context, and has
// no macOS branch. compose-raven-opengl makes an OpenGL 3.2 core context, where those calls are gone,
// so the port draws the same cube, from the same angle and in the same colours, with a vertex and a
// fragment shader and a vertex buffer (CubeScene). It runs on macOS: Linux waits for GLX there.
//
// The cube turns as the C example's does, by the speed each second, but the angle is state the
// frame loop advances, so the viewport draws again when it, or the wireframe switch, changes.

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(size = DpSize(900.dp, 540.dp)),
        title = "Raven - OpenGL viewport",
    ) { ViewportScreen() }
}

/** What the controls set and the scene shows. */
class SceneState {
    var speed by mutableFloatStateOf(1f)
    var wireframe by mutableStateOf(false)

    /** In seconds of turning at speed 1: the C example's `angle`. */
    var angle by mutableFloatStateOf(0f)

    /** `GL_RENDERER`, once a context is made. */
    var renderer by mutableStateOf<String?>(null)
}

/** The C example's message where no context can be made on the viewport. */
const val NO_CONTEXT: String = "Cannot create an OpenGL context on the viewport."

@Composable
fun ViewportScreen(state: SceneState = remember { SceneState() }) {
    // The angle moves while the speed isn't 0: frames are asked for only then.
    LaunchedEffect(state.speed == 0f) {
        if (state.speed == 0f) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            state.angle += (now - last) / 1e9f * state.speed
            last = now
        }
    }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(240.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("OpenGL in a native viewport", fontSize = 16.sp)
            Text(
                "The cube is drawn by an OpenGL context created on a child window that Raven keeps on the viewport widget.",
                Modifier.alpha(0.7f), softWrap = true,
            )
            Text("Speed")
            Slider(state.speed, { state.speed = it }, Modifier.fillMaxWidth().testTag("speed"), valueRange = 0f..4f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(state.wireframe, { state.wireframe = it }, Modifier.testTag("wireframe"))
                Text("Wireframe", Modifier.align(Alignment.CenterVertically))
            }
            // Until the context is made, and for good where it can't be, the C example's message.
            Text(state.renderer ?: NO_CONTEXT, Modifier.alpha(0.7f).testTag("info"), softWrap = true)
        }
        VerticalDivider()
        val scene = remember { glScene() }
        OpenGLViewport(
            Modifier.weight(1f).fillMaxHeight().testTag("scene"),
            onInit = { state.renderer = scene?.init() },
            onDraw = { width, height -> scene?.draw(width, height, state.angle, state.wireframe) },
            onDispose = { scene?.dispose() },
        )
    }
}

/** The cube, drawn in the current GL context. */
interface Scene {
    /** Makes the shaders and the buffer; gives `GL_RENDERER`. */
    fun init(): String

    fun draw(width: Int, height: Int, angle: Float, wireframe: Boolean)

    fun dispose()
}

/** The cube in OpenGL 3.2 core; null where the sample has no GL bindings (Linux, for now). */
expect fun glScene(): Scene?
