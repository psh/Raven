@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import androidx.compose.runtime.Composable
import raven.cinterop.Rquit

/**
 * Opens one window with [content] and runs until it is closed. Call it from `main()`: the main
 * thread becomes Raven's UI thread, as AppKit requires. A throwable from composition, an effect or
 * a callback ends the application, which shuts down normally and then rethrows it.
 *
 * With [followSystemAccessibility], text scale, contrast and reduced motion follow the OS.
 */
public fun singleWindowApplication(
    title: String = "Untitled",
    width: Int = 800,
    height: Int = 600,
    followSystemAccessibility: Boolean = true,
    content: @Composable () -> Unit,
) {
    runApplication(RavenHost(followSystemAccessibility = followSystemAccessibility)) {
        Window(onCloseRequest = ::exitApplication, title = title, width = width, height = height, content = content)
    }
}

/** Ends the application: the frame loop stops and the host shuts down. Thread-safe. */
internal fun exitApplication() {
    Rquit(0)
}

/** Starts [host] with [content], runs frames until Raven quits, closes it, then rethrows its error. */
internal fun runApplication(host: RavenHost, content: @Composable () -> Unit) {
    try {
        host.start(content)
        while (host.waitFrame()) {
            // Raven runs the frame: tasks, the Recomposer, layout and paint.
        }
    } catch (t: Throwable) {
        host.reportError(t)
    } finally {
        host.close()
    }
    host.error?.let { throw it }
}
