package raven.compose.ui

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import raven.cinterop.RError
import raven.cinterop.RInitDesc
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
import raven.cinterop.Rshutdown
import raven.cinterop.Rwidget_create
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_name
import raven.cinterop.Rwidget_set_name
import raven.native.CallbackRegistry
import raven.native.toRavenString
import kotlin.test.assertEquals

// Until RavenHost and the test harness exist, these tests start Raven themselves.

private val ravenErrors = mutableListOf<String>()

/**
 * Runs [block] with Raven started headless, then stops it. Fails if Raven reported an error (a
 * stale handle, a wrong widget type) or if a widget's reference outlived Raven.
 */
internal fun withHeadlessRaven(block: () -> Unit) {
    ravenErrors.clear()
    Rerror_set_callback(staticCFunction { _: RError, severity: RSeverity, detail: kotlinx.cinterop.CPointer<kotlinx.cinterop.ByteVar>? ->
        if (severity == R_SEVERITY_ERROR || severity == R_SEVERITY_FATAL) ravenErrors += detail?.toKString() ?: "?"
    })
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
        Rerror_set_callback(null)
    }
    assertEquals(emptyList(), ravenErrors.toList(), "errors reported by Raven")
    assertEquals(0, CallbackRegistry.liveCount, "widget references left after Rshutdown")
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
