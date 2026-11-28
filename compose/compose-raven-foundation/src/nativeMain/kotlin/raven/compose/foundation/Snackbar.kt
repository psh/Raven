package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import raven.cinterop.RToastKind
import raven.cinterop.R_TOAST_ERROR
import raven.cinterop.R_TOAST_INFO
import raven.cinterop.R_TOAST_SEQUENCE
import raven.cinterop.R_TOAST_STACK
import raven.cinterop.R_TOAST_SUCCESS
import raven.cinterop.R_TOAST_WARNING
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_TOAST
import raven.cinterop.Rid
import raven.cinterop.Rtoast_hide_message
import raven.cinterop.Rtoast_set_action_text
import raven.cinterop.Rtoast_set_max_visible
import raven.cinterop.Rtoast_set_queue
import raven.cinterop.Rtoast_show_message
import raven.cinterop.Rwidget_is_valid
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.ToastMessageEnded
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.WidgetFollower
import raven.compose.ui.bind
import kotlin.coroutines.resume

private val ToastWidget = WidgetDescriptor("SnackbarHost", R_WIDGET_TOAST, events = listOf(ToastMessageEnded))

/** How a snackbar's message is marked, as Raven colours it: a dot of the kind's colour before the text. */
public enum class SnackbarKind(internal val raven: RToastKind) {
    Info(R_TOAST_INFO),
    Success(R_TOAST_SUCCESS),
    Warning(R_TOAST_WARNING),
    Error(R_TOAST_ERROR),
}

/** How long a snackbar stays: [Short] 4 s, [Long] 10 s, [Indefinite] until its button or a cancel. */
public enum class SnackbarDuration(internal val seconds: Double) {
    Short(4.0),
    Long(10.0),
    Indefinite(0.0),
}

/** How a snackbar ended: [ActionPerformed] by its button, [Dismissed] timed out or cleared. */
public enum class SnackbarResult {
    Dismissed,
    ActionPerformed,
}

/**
 * The messages of a [SnackbarHost], shown by [showSnackbar], one call a message. Remember it with
 * `remember { SnackbarHostState() }`. A message asked for while no host shows the state waits for
 * one.
 */
@Stable
public class SnackbarHostState {
    private class Request(
        val message: String,
        val actionLabel: String?,
        val kind: SnackbarKind,
        val duration: SnackbarDuration,
        val continuation: CancellableContinuation<SnackbarResult>,
    ) {
        /** Its id in the toast once shown or queued there; 0 before. */
        var id = 0
    }

    private val requests = mutableListOf<Request>()
    private var toast: Rid? = null

    /** The seconds a duration stays; the tests make them short. */
    internal var secondsOf: (SnackbarDuration) -> Double = { it.seconds }

    /**
     * Shows [message], with a button [actionLabel] when given, marked by [kind], for [duration], and
     * returns how it ended. Messages wait their turn, or stack, as the host says. Cancelling the
     * call hides the message, or takes it out of the queue while it waits.
     */
    public suspend fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        kind: SnackbarKind = SnackbarKind.Info,
        duration: SnackbarDuration = if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite,
    ): SnackbarResult = suspendCancellableCoroutine { continuation ->
        val request = Request(message, actionLabel, kind, duration, continuation)
        requests += request
        toast?.let { show(it, request) }
        continuation.invokeOnCancellation { cancel(request) }
    }

    private fun show(toast: Rid, request: Request) {
        // The button goes with the next message; each keeps its own.
        Rtoast_set_action_text(toast, request.actionLabel.orEmpty())
        request.id = Rtoast_show_message(toast, request.message, request.kind.raven, secondsOf(request.duration))
    }

    private fun cancel(request: Request) {
        if (!requests.remove(request)) return
        val toast = toast?.takeIf { Rwidget_is_valid(it) == R_TRUE }
        if (toast != null && request.id != 0) Rtoast_hide_message(toast, request.id)
    }

    /** The message [id] went, by its button or not. */
    internal fun ended(id: Int, byAction: Boolean) {
        val request = requests.firstOrNull { it.id == id } ?: return
        requests.remove(request)
        if (request.continuation.isActive) {
            request.continuation.resume(if (byAction) SnackbarResult.ActionPerformed else SnackbarResult.Dismissed)
        }
    }

    internal fun attach(widget: Rid): Boolean {
        if (toast != null && toast != widget) {
            warnSecondScrollable("SnackbarHostState")
            return false
        }
        toast = widget
        // What waited for a host is shown now, in the order asked.
        requests.forEach { show(widget, it) }
        return true
    }

    internal fun detach(widget: Rid) {
        if (toast != widget) return
        toast = null
        // The toast's messages went with it: they are shown again by the next host.
        requests.forEach { it.id = 0 }
    }
}

/** Points a [SnackbarHostState] at its toast while the toast's node holds it. */
private class SnackbarFollower : WidgetFollower {
    private var widget: Rid? = null
    private var attached: SnackbarHostState? = null

    var state: SnackbarHostState? = null
        set(value) {
            if (value === field) return
            widget?.let { toast ->
                attached?.detach(toast)
                attached = value?.takeIf { it.attach(toast) }
            }
            field = value
        }

    override fun attach(widget: Rid) {
        this.widget = widget
        attached = state?.takeIf { it.attach(widget) }
    }

    override fun follow(widget: Rid) = Unit

    override fun detach(widget: Rid) {
        attached?.detach(widget)
        attached = null
        this.widget = null
    }
}

private val MaxVisible = Property<Int> { widget, count ->
    if (count > 1) {
        Rtoast_set_queue(widget, R_TOAST_STACK)
        Rtoast_set_max_visible(widget, count)
    } else {
        Rtoast_set_queue(widget, R_TOAST_SEQUENCE)
    }
}

/**
 * Where the messages of [hostState] appear, over Raven's Toast: place it where they should, as
 * `Box { content; SnackbarHost(state, Modifier.align(Alignment.BottomCenter).padding(16.dp)) }`.
 * With [maxVisible] 1 the messages wait their turn, as Material's do; above 1 they stack, the
 * newest at the bottom, so a host at the bottom grows upwards. Hidden, it takes no click.
 */
@Composable
public fun SnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier, maxVisible: Int = 1) {
    require(maxVisible >= 1) { "maxVisible is at least 1, not $maxVisible" }
    val follower = remember { SnackbarFollower() }
    RavenWidget(ToastWidget, modifier, {
        // The queue before the state, which may show what waited as it attaches.
        bind(maxVisible, MaxVisible)
        bind(follower)
        set(hostState) { follower.state = it }
        bind({ id: Int, byAction: Boolean -> hostState.ended(id, byAction) }, ToastMessageEnded)
    })
}
