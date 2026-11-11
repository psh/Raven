package raven.compose.foundation

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import raven.cinterop.RError
import raven.cinterop.RMemoryStats
import raven.cinterop.RSeverity
import raven.cinterop.R_SEVERITY_ERROR
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_SEVERITY_FATAL
import raven.cinterop.R_TRUE
import raven.cinterop.R_FALSE
import raven.cinterop.R_WIDGET_CHECKBOX
import raven.cinterop.R_WIDGET_LABEL
import raven.cinterop.Rcheckbox_is_checked
import raven.cinterop.Rid
import raven.cinterop.Rlabel_get_text
import raven.cinterop.Rwidget_get_accessible_name
import raven.cinterop.Rwidget_get_rect
import raven.cinterop.Rwidget_get_type
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.cinterop.Rerror_set_callback
import raven.cinterop.Rmemory_get_stats
import raven.compose.ui.RavenHost
import raven.compose.ui.RavenNode
import raven.compose.ui.Window
import raven.native.CallbackRegistry
import raven.native.toRavenString
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Runs [block] against a fresh headless Raven, with a fixed animation time step, then shuts it
 * down. The test fails if Raven reported an error, if the host recorded one, or if anything leaked:
 * a block Raven still holds, or a widget reference still alive.
 */
internal fun runRavenTest(block: RavenTestScope.() -> Unit) {
    ravenErrors.clear()
    Rerror_set_callback(collectRavenError)
    val host = RavenHost(headless = true, animationTimeStep = ANIMATION_TIME_STEP)
    try {
        try {
            RavenTestScope(host).block()
        } finally {
            host.close()
        }
        host.error?.let { throw it }
    } finally {
        Rerror_set_callback(null)
    }
    assertEquals(emptyList(), ravenErrors.toList(), "errors reported by Raven")
    assertEquals(0uL, ravenBlocksHeld(), "blocks Raven still holds after Rshutdown")
    assertEquals(0, CallbackRegistry.liveCount, "widget references left after Rshutdown")
}

internal class RavenTestScope(val host: RavenHost) {
    /** Hosts [content] in a single window, then waits until it is idle. */
    fun setContent(content: @Composable () -> Unit) {
        host.start { Window(onCloseRequest = {}, title = "Test", content = content) }
        waitForIdle()
    }

    /**
     * Draws frames until no recomposition, snapshot apply or dispatched task is pending. Frame
     * awaiters don't count, so a running animation never stalls it.
     */
    fun waitForIdle() {
        repeat(MAX_IDLE_FRAMES) {
            if (host.isIdle()) return
            frame()
        }
        fail("not idle after $MAX_IDLE_FRAMES frames")
    }

    /** Draws one frame. Throws what ended the host, if a failure asked Raven to quit. */
    fun frame() {
        if (!host.frame()) throw host.error ?: AssertionError("Raven quit")
    }

    /** The Text showing [text], or a part of it with [substring]. */
    fun onNodeWithText(text: String, substring: Boolean = false): NodeInteraction =
        NodeInteraction(this, "text \"$text\"") { node ->
            val shown = labelText(node) ?: return@NodeInteraction false
            if (substring) text in shown else shown == text
        }

    /** The node tagged [tag] with `Modifier.testTag`. */
    fun onNodeWithTag(tag: String): NodeInteraction = NodeInteraction(this, "tag \"$tag\"") { it.testTag == tag }

    /**
     * The widget whose accessible name is [description]. Raven names a widget after its text when
     * nothing else names it, so Texts are left out: [onNodeWithText] finds them.
     */
    fun onNodeWithContentDescription(description: String, substring: Boolean = false): NodeInteraction =
        NodeInteraction(this, "content description \"$description\"") { node ->
            if (labelText(node) != null) return@NodeInteraction false
            val name = Rwidget_get_accessible_name(node.widget).toRavenString()
            if (substring) description in name else name == description
        }

    /** The nodes of every open window that [matches] accepts, content nodes left out. */
    internal fun findNodes(matches: (RavenNode) -> Boolean): List<RavenNode> {
        val found = mutableListOf<RavenNode>()
        fun visit(node: RavenNode) {
            if (!node.isContentNode && matches(node)) found += node
            node.children.forEach(::visit)
        }
        host.windowRoots.forEach(::visit)
        return found
    }

    private fun labelText(node: RavenNode): String? =
        if (Rwidget_get_type(node.widget) == R_WIDGET_LABEL) Rlabel_get_text(node.widget).toRavenString() else null
}

/** One node a finder selects. Every use waits for the UI to be idle, then finds it again. */
internal class NodeInteraction(
    private val scope: RavenTestScope,
    private val selector: String,
    private val matches: (RavenNode) -> Boolean,
) {
    /** The one node that matches; fails if there is none, or more than one. */
    fun fetchNode(): RavenNode {
        scope.waitForIdle()
        val found = scope.findNodes(matches)
        if (found.isEmpty()) fail("no node with $selector")
        if (found.size > 1) fail("${found.size} nodes with $selector, expected one")
        return found.single()
    }

    fun fetchWidget(): Rid = fetchNode().widget

    fun assertExists(): NodeInteraction = apply { fetchNode() }

    fun assertDoesNotExist(): NodeInteraction = apply {
        scope.waitForIdle()
        val found = scope.findNodes(matches)
        if (found.isNotEmpty()) fail("${found.size} nodes with $selector, expected none")
    }

    fun assertIsChecked(): NodeInteraction = apply { assertChecked(true) }

    fun assertIsNotChecked(): NodeInteraction = apply { assertChecked(false) }

    private fun assertChecked(expected: Boolean) {
        val widget = fetchWidget()
        if (Rwidget_get_type(widget) != R_WIDGET_CHECKBOX) fail("the node with $selector can't be checked")
        val checked = Rcheckbox_is_checked(widget) == R_TRUE
        if (checked != expected) fail("the node with $selector is ${if (checked) "checked" else "not checked"}")
    }

    /** Presses and releases the left button at the centre of the node's widget, as the user would. */
    fun performClick(): NodeInteraction = apply {
        val widget = fetchWidget()
        val (x, y) = memScoped {
            val values = List(4) { alloc<FloatVar>() }
            Rwidget_get_rect(widget, values[0].ptr, values[1].ptr, values[2].ptr, values[3].ptr)
            (values[0].value + values[2].value / 2) to (values[1].value + values[3].value / 2)
        }
        val window = Rwidget_get_window(widget)
        Rwindow_inject_mouse_move(window, x, y)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
    }
}

/** One frame at 60 Hz. */
private const val ANIMATION_TIME_STEP = 1.0 / 60

private const val MAX_IDLE_FRAMES = 100

private val ravenErrors = mutableListOf<String>()

private val collectRavenError = staticCFunction { _: RError, severity: RSeverity, detail: CPointer<ByteVar>? ->
    if (severity == R_SEVERITY_ERROR || severity == R_SEVERITY_FATAL) ravenErrors += detail?.toKString() ?: "?"
}

private fun ravenBlocksHeld(): ULong = memScoped {
    val stats = alloc<RMemoryStats>()
    Rmemory_get_stats(stats.ptr)
    stats.allocation_count
}
