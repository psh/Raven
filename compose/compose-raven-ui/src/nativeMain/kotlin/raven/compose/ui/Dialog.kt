@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import raven.cinterop.R_FALSE
import raven.cinterop.R_ROLE_DIALOG
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_OVERLAY
import raven.cinterop.Rdialog_close
import raven.cinterop.Rdialog_open
import raven.cinterop.Rdialog_set_dismiss_callback
import raven.cinterop.Rid
import raven.cinterop.Rwidget_create
import raven.cinterop.Rwidget_is_valid
import raven.cinterop.Rwidget_set_accessible_role
import raven.cinterop.Rwindow_is_active
import kotlin.coroutines.resume

/** How a [Dialog] behaves. Raven's dialog is a modal sheet: a click outside it does nothing. */
@Immutable
public data class DialogProperties(
    /** Whether Escape calls `onDismissRequest`; false, Escape does nothing. */
    val dismissOnBackPress: Boolean = true,
)

/**
 * A modal sheet over its window showing [content], which draws its own background (a `Surface`,
 * say). Escape calls [onDismissRequest] (unless [properties] say otherwise) and the dialog stays:
 * the app closes it by leaving it out of the composition. Its content has a composition of its
 * own, under the window's, so it sees the window's CompositionLocals.
 */
@Composable
public fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val host = LocalRavenHost.current
    val window = LocalRavenWindow.current
    val parent = rememberCompositionContext()
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentProperties by rememberUpdatedState(properties)
    val currentContent by rememberUpdatedState(content)
    DisposableEffect(host, window) {
        val dialog = RavenDialog(host, window.window, parent) {
            if (currentProperties.dismissOnBackPress) currentOnDismissRequest()
        }
        dialog.open { currentContent() }
        onDispose { dialog.close() }
    }
}

/**
 * A dialog's composition, rooted in the panel given to `Rdialog_open`, which Raven owns from then
 * on and deletes as the dialog closes: the composition is disposed first, then `Rdialog_close`.
 */
internal class RavenDialog(
    private val host: RavenHost,
    private val window: Rid,
    parent: CompositionContext,
    private val dismiss: () -> Unit,
) {
    val root = RavenNode(Rwidget_create(R_WIDGET_OVERLAY), ChildKind.Many)
    private val composition = Composition(RavenApplier(root, host::onChangesApplied), parent)
    private val selfRef = StableRef.create(this)

    // Raven closed the dialog itself (its window went), so its panel is gone.
    private var closedByRaven = false

    init {
        Rwidget_set_accessible_role(root.widget, R_ROLE_DIALOG)
    }

    fun open(content: @Composable () -> Unit) {
        composition.setContent(content)
        Rdialog_open(window, root.widget, onDialogClosed, selfRef.asCPointer())
        Rdialog_set_dismiss_callback(root.widget, onDialogDismiss, selfRef.asCPointer())
        host.dialogs += this
    }

    fun close() {
        host.dialogs -= this
        composition.dispose()
        if (!closedByRaven && Rwidget_is_valid(root.widget) == R_TRUE) Rdialog_close(root.widget, 0)
        selfRef.dispose()
    }

    fun onClosed() {
        closedByRaven = true
    }

    /** Escape: the app decides, so Raven keeps the dialog. */
    fun onDismiss(): Int {
        try {
            dismiss()
        } catch (t: Throwable) {
            host.reportError(t)
        }
        return R_FALSE
    }
}

private val onDialogClosed = staticCFunction { _: Rid, _: Int, userData: COpaquePointer? ->
    userData!!.asStableRef<RavenDialog>().get().onClosed()
}

private val onDialogDismiss = staticCFunction { _: Rid, userData: COpaquePointer? ->
    userData!!.asStableRef<RavenDialog>().get().onDismiss()
}

/** A dialog a suspend function shows over a window, until it is answered. */
internal class WindowDialog(val content: @Composable () -> Unit) {
    var continuation: CancellableContinuation<*>? = null
}

/** The dialogs [RavenWindow.dialogRequests] holds, composed after the window's content. */
@Composable
internal fun WindowDialogs(requests: List<WindowDialog>) {
    for (request in requests) key(request) { request.content() }
}

/**
 * Shows [content] over the active window (else the first) until it calls its `answer`, then
 * resumes with the answer, the dialog gone. It runs on the running host's UI thread, and
 * cancelling it takes the dialog away. Fails when no host runs or it has no window.
 */
@InternalComposeRavenApi
public suspend fun <T> awaitWindowDialog(content: @Composable (answer: (T) -> Unit) -> Unit): T {
    val host = RavenHost.running ?: error("no Raven application is running")
    return withContext(host.dispatcher) {
        val window = host.windows.firstOrNull { Rwindow_is_active(it.window) == R_TRUE } ?: host.windows.firstOrNull()
            ?: error("no window to show the dialog over")
        suspendCancellableCoroutine { continuation ->
            lateinit var request: WindowDialog
            request = WindowDialog {
                content { answer -> if (window.dialogRequests.remove(request)) continuation.resume(answer) }
            }
            request.continuation = continuation
            window.dialogRequests += request
            continuation.invokeOnCancellation {
                host.dispatcher.dispatch(kotlin.coroutines.EmptyCoroutineContext, Runnable { window.dialogRequests.remove(request) })
            }
        }
    }
}
