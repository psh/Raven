package raven.compose.ui

import androidx.compose.runtime.Composable
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.Rwidget_get_rect
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.cinterop.RError
import raven.cinterop.RInitDesc
import raven.cinterop.RMemoryStats
import raven.cinterop.RSeverity
import raven.cinterop.RWidgetType
import raven.cinterop.R_BACKEND_NULL
import raven.cinterop.R_FALSE
import raven.cinterop.R_SEVERITY_ERROR
import raven.cinterop.R_SEVERITY_FATAL
import raven.cinterop.R_TRUE
import raven.cinterop.Rerror_set_callback
import raven.cinterop.Rid
import raven.cinterop.Rinit_ex
import raven.cinterop.Rmemory_get_stats
import raven.cinterop.Rshutdown
import raven.cinterop.Rwidget_create
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_name
import raven.cinterop.Rwidget_set_name
import raven.native.CallbackRegistry
import raven.native.toRavenString
import kotlin.test.assertEquals

// The Applier tests start Raven themselves; the host tests go through RavenHost.

private val ravenErrors = mutableListOf<String>()

/**
 * Runs [block], which starts and stops Raven. Fails if Raven reported an error (a stale handle, a
 * wrong widget type), if it still holds memory, or if a widget's reference outlived it.
 */
internal fun withRavenChecks(block: () -> Unit) {
    ravenErrors.clear()
    Rerror_set_callback(staticCFunction { _: RError, severity: RSeverity, detail: kotlinx.cinterop.CPointer<kotlinx.cinterop.ByteVar>? ->
        if (severity == R_SEVERITY_ERROR || severity == R_SEVERITY_FATAL) ravenErrors += detail?.toKString() ?: "?"
    })
    try {
        block()
    } finally {
        Rerror_set_callback(null)
    }
    assertEquals(emptyList(), ravenErrors.toList(), "errors reported by Raven")
    val held = memScoped {
        val stats = alloc<RMemoryStats>()
        Rmemory_get_stats(stats.ptr)
        stats.allocation_count
    }
    assertEquals(0uL, held, "blocks Raven still holds after Rshutdown")
    assertEquals(0, CallbackRegistry.liveCount, "widget references left after Rshutdown")
}

/** Runs [block] with Raven started headless, then stops it, with [withRavenChecks]. */
internal fun withHeadlessRaven(block: () -> Unit) = withRavenChecks {
    memScoped {
        val desc = alloc<RInitDesc>().apply {
            backend = R_BACKEND_NULL
            app_name = null
            worker_threads = -1
            disable_render_thread = R_FALSE
            disable_vsync = R_FALSE
            enable_validation = R_FALSE
            headless = R_TRUE
            ui_scale = 0f
        }
        assertEquals(R_TRUE, Rinit_ex(desc.ptr), "Rinit_ex")
    }
    try {
        block()
    } finally {
        Rshutdown()
    }
}

/** Starts a headless host with [content], runs [block] once it is idle, then closes it. */
internal fun withHost(content: @Composable () -> Unit, block: RavenHost.() -> Unit = {}) = withRavenChecks {
    val host = RavenHost(headless = true)
    try {
        host.start(content)
        host.runUntilIdle()
        host.block()
    } finally {
        host.close()
    }
    host.error?.let { throw it }
}

/** Draws frames until the host is idle. */
internal fun RavenHost.runUntilIdle() {
    repeat(100) {
        if (isIdle()) return
        check(frame()) { "Raven quit" }
    }
    error("not idle after 100 frames")
}

/** Draws one frame, which must not end the loop. */
internal fun RavenHost.drawFrame() {
    check(frame()) { "Raven quit" }
}

/** Clicks the middle of [widget] with the left button, as the user would. */
internal fun click(widget: Rid) {
    val (x, y) = memScoped {
        val x = alloc<FloatVar>()
        val y = alloc<FloatVar>()
        val width = alloc<FloatVar>()
        val height = alloc<FloatVar>()
        Rwidget_get_rect(widget, x.ptr, y.ptr, width.ptr, height.ptr)
        (x.value + width.value / 2) to (y.value + height.value / 2)
    }
    val window = Rwidget_get_window(widget)
    Rwindow_inject_mouse_move(window, x, y)
    Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
    Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
}

/** A node over a new widget named [name], so the Raven tree can be read back by names. */
internal fun node(name: String, kind: ChildKind = ChildKind.None, type: RWidgetType = typeFor(kind)): RavenNode {
    val widget = Rwidget_create(type)
    Rwidget_set_name(widget, name)
    return RavenNode(widget, kind)
}

private fun typeFor(kind: ChildKind): RWidgetType = when (kind) {
    ChildKind.None -> raven.cinterop.R_WIDGET_LABEL
    ChildKind.Single -> raven.cinterop.R_WIDGET_BORDER
    ChildKind.Many -> raven.cinterop.R_WIDGET_VBOX
}

/** The names of [widget]'s Raven children, in Raven's order. */
internal fun ravenChildren(widget: Rid): List<String> =
    List(Rwidget_get_child_count(widget)) { Rwidget_get_name(Rwidget_get_child(widget, it)).toRavenString() }

/** The Raven tree below [widget], as `name[child, child[grandchild]]`. */
internal fun ravenTree(widget: Rid): String {
    val children = List(Rwidget_get_child_count(widget)) { ravenTree(Rwidget_get_child(widget, it)) }
    val name = Rwidget_get_name(widget).toRavenString()
    return if (children.isEmpty()) name else "$name[${children.joinToString()}]"
}
