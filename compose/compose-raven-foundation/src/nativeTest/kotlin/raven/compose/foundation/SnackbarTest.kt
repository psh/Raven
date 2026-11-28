package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import raven.cinterop.R_FALSE
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_TOAST_ERROR
import raven.cinterop.R_TOAST_INFO
import raven.cinterop.R_TOAST_SEQUENCE
import raven.cinterop.R_TOAST_STACK
import raven.cinterop.R_TOAST_SUCCESS
import raven.cinterop.R_TOAST_WARNING
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rtoast_get_count
import raven.cinterop.Rtoast_get_queue
import raven.cinterop.Rtoast_get_text
import raven.cinterop.Rtoast_is_shown
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.padding
import raven.compose.ui.Alignment
import raven.compose.ui.Modifier
import raven.compose.ui.test.RavenTestScope
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.test.widgetRect
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** SnackbarHost and SnackbarHostState over Raven's Toast. */
class SnackbarTest {
    private lateinit var scope: CoroutineScope

    private fun RavenTestScope.host(state: SnackbarHostState, maxVisible: Int = 1) {
        setContent {
            scope = rememberCoroutineScope()
            Box(Modifier.fillMaxSize()) {
                SnackbarHost(state, Modifier.align(Alignment.BottomCenter).padding(16.dp).testTag("toast"), maxVisible)
            }
        }
    }

    private fun RavenTestScope.toast(): Rid = onNodeWithTag("toast").fetchWidget()

    private fun text(toast: Rid) = Rtoast_get_text(toast).toRavenString()

    /** Shows a snackbar from a coroutine; its result lands in [results] under [name]. */
    private fun RavenTestScope.show(
        state: SnackbarHostState,
        results: MutableMap<String, SnackbarResult>,
        name: String,
        actionLabel: String? = null,
        duration: SnackbarDuration = SnackbarDuration.Indefinite,
    ): Job = scope.launch { results[name] = state.showSnackbar(name, actionLabel, duration = duration) }.also { waitForIdle() }

    /** Draws frames until [done], for the toast's timers, which run on the clock. */
    private fun RavenTestScope.frameUntil(done: () -> Boolean) {
        val start = TimeSource.Monotonic.markNow()
        while (!done() && start.elapsedNow() < 5.seconds) frame()
        waitForIdle()
    }

    @Test
    fun aMessageTimesOutDismissed() = runRavenTest {
        val state = SnackbarHostState().apply { secondsOf = { if (it == SnackbarDuration.Short) 0.05 else 0.0 } }
        val results = mutableMapOf<String, SnackbarResult>()
        host(state)
        assertEquals(R_TOAST_SEQUENCE, Rtoast_get_queue(toast()))
        show(state, results, "Saved", duration = SnackbarDuration.Short)
        assertEquals(R_TRUE, Rtoast_is_shown(toast()))
        assertEquals("Saved", text(toast()))
        frameUntil { "Saved" in results }
        assertEquals(SnackbarResult.Dismissed, results["Saved"])
    }

    @Test
    fun theButtonGivesActionPerformed() = runRavenTest {
        val state = SnackbarHostState()
        val results = mutableMapOf<String, SnackbarResult>()
        host(state)
        show(state, results, "Deleted", actionLabel = "Undo")
        frame()
        val toast = toast()
        val rect = widgetRect(toast)
        val window = Rwidget_get_window(toast)
        Rwindow_inject_mouse_move(window, rect.x + rect.width - 20f, rect.y + rect.height / 2)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
        waitForIdle()
        assertEquals(SnackbarResult.ActionPerformed, results["Deleted"])
        assertEquals(0, Rtoast_get_count(toast))
    }

    @Test
    fun messagesWaitTheirTurnAndCancellingHidesOrUnqueues() = runRavenTest {
        val state = SnackbarHostState()
        val results = mutableMapOf<String, SnackbarResult>()
        host(state)
        val first = show(state, results, "First")
        show(state, results, "Second")
        val third = show(state, results, "Third")
        val toast = toast()
        assertEquals(3, Rtoast_get_count(toast))
        assertEquals("First", text(toast), "the others wait")

        // A waiting message cancelled leaves the queue.
        third.cancel()
        waitForIdle()
        assertEquals(2, Rtoast_get_count(toast))
        // The one shown cancelled hides, and the next comes.
        first.cancel()
        waitForIdle()
        assertEquals(1, Rtoast_get_count(toast))
        assertEquals("Second", text(toast))
        assertEquals(emptyMap(), results, "a cancelled call returns nothing")
    }

    @Test
    fun aStackShowsThreeAtOnce() = runRavenTest {
        val state = SnackbarHostState()
        val results = mutableMapOf<String, SnackbarResult>()
        host(state, maxVisible = 3)
        assertEquals(R_TOAST_STACK, Rtoast_get_queue(toast()))
        val jobs = listOf("A", "B", "C").map { show(state, results, it) }
        assertEquals(3, Rtoast_get_count(toast()))
        assertEquals("C", text(toast()), "all three shown, the newest last")
        jobs[1].cancel()
        waitForIdle()
        assertEquals(2, Rtoast_get_count(toast()))
    }

    @Test
    fun aMessageAskedForBeforeAHostWaitsForOne() = runRavenTest {
        val state = SnackbarHostState()
        val results = mutableMapOf<String, SnackbarResult>()
        var shown by mutableStateOf(false)
        setContent {
            scope = rememberCoroutineScope()
            if (shown) SnackbarHost(state, Modifier.testTag("toast"))
        }
        show(state, results, "Early")
        shown = true
        waitForIdle()
        assertEquals("Early", text(toast()))
    }

    @Test
    fun theKindsAreRavensAndTheDurationsMaterials() {
        assertEquals(listOf(R_TOAST_INFO, R_TOAST_SUCCESS, R_TOAST_WARNING, R_TOAST_ERROR), SnackbarKind.entries.map { it.raven })
        assertEquals(listOf(4.0, 10.0, 0.0), SnackbarDuration.entries.map { it.seconds })
    }
}
