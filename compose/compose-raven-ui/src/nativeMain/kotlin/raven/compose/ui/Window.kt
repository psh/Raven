package raven.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import raven.cinterop.R_FALSE
import raven.cinterop.R_WIDGET_OVERLAY
import raven.cinterop.R_WINDOW_DEFAULT
import raven.cinterop.Rid
import raven.cinterop.Rwidget_create
import raven.cinterop.Rwindow_create
import raven.cinterop.Rwindow_destroy
import raven.cinterop.Rwindow_set_close_callback
import raven.cinterop.Rwindow_set_content

/**
 * A Raven window with [content] in its own composition. It emits no node into its parent.
 *
 * Closing is controlled: Raven's close always refuses and calls [onCloseRequest], and the window
 * is destroyed when it leaves the composition.
 */
@InternalComposeRavenApi
@Composable
public fun Window(
    onCloseRequest: () -> Unit,
    title: String = "Untitled",
    width: Int = 800,
    height: Int = 600,
    content: @Composable () -> Unit,
) {
    val host = LocalRavenHost.current
    val parent = rememberCompositionContext()
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
    val currentContent by rememberUpdatedState(content)
    DisposableEffect(Unit) {
        val window = RavenWindow(host, parent, title, width, height) { currentOnCloseRequest() }
        window.setContent { currentContent() }
        onDispose { window.dispose() }
    }
}

/** A Raven window, its fixed Overlay root, and the composition that fills it. */
@OptIn(InternalComposeRavenApi::class)
internal class RavenWindow(
    private val host: RavenHost,
    parent: CompositionContext,
    title: String,
    width: Int,
    height: Int,
    private val onCloseRequest: () -> Unit,
) {
    val window: Rid = Rwindow_create(title, width, height, R_WINDOW_DEFAULT)
    val root = RavenNode(Rwidget_create(R_WIDGET_OVERLAY), ChildKind.Many)
    private val composition = Composition(RavenApplier(root, host::onChangesApplied), parent)
    private val selfRef = StableRef.create(this)

    init {
        // The window owns the root from now on, and deletes it with itself.
        Rwindow_set_content(window, root.widget)
        Rwindow_set_close_callback(window, onClose, selfRef.asCPointer())
    }

    fun setContent(content: @Composable () -> Unit) = composition.setContent(content)

    /** Disposes the composition before the window, so no node holds a widget Raven deleted. */
    fun dispose() {
        composition.dispose()
        Rwindow_destroy(window)
        selfRef.dispose()
    }

    fun requestClose() {
        try {
            onCloseRequest()
        } catch (t: Throwable) {
            host.reportError(t)
        }
    }
}

private val onClose = staticCFunction { _: Rid, userData: COpaquePointer? ->
    userData!!.asStableRef<RavenWindow>().get().requestClose()
    R_FALSE
}
