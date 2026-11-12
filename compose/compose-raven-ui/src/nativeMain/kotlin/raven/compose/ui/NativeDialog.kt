package raven.compose.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rwindow_is_active

/**
 * Runs [call], one of Raven's calls that wait for the user while running frames themselves
 * (`Rmessage_box`, `Rfile_dialog_*`), as a block of its own on the running host's
 * [RavenDispatcher], then resumes with its result. [call] gets the window to show its dialog over:
 * the active window, else `R_INVALID_ID`, for which Raven takes its first visible window.
 *
 * Compose recomposes in the frames [call] runs, and a second call made there waits inside the
 * first: calls that wait together return in reverse order. A cancellation is seen only once
 * [call] has returned. Fails when no host runs.
 */
@InternalComposeRavenApi
public suspend fun <T> awaitNativeDialog(call: (window: Rid) -> T): T {
    val host = RavenHost.running ?: error("no Raven application is running")
    return withContext(host.dispatcher) {
        // A block of its own, even for a caller resumed undispatched inside a callback or an apply.
        yield()
        val window = host.windows.firstOrNull { Rwindow_is_active(it.window) == R_TRUE }?.window ?: R_INVALID_ID
        call(window)
    }
}
