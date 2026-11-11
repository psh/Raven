@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import androidx.compose.runtime.Composable
import raven.cinterop.Rquit

/** What an [application]'s content can do to the application as a whole. */
public interface ApplicationScope {
    /** Ends the application: the frame loop stops and the host shuts down. Thread-safe. */
    public fun exitApplication()
}

private object RavenApplicationScope : ApplicationScope {
    override fun exitApplication() {
        Rquit(0)
    }
}

/**
 * Runs [content], whose `Window`s are the application's windows, until no `Window` is left or
 * [ApplicationScope.exitApplication] is called. Call it from `main()`: the main thread becomes
 * Raven's UI thread, as AppKit requires. A throwable from composition, an effect or a callback
 * ends the application, which shuts down normally and then rethrows it.
 *
 * With [followSystemAccessibility], text scale, contrast and reduced motion follow the OS.
 */
public fun application(
    followSystemAccessibility: Boolean = true,
    content: @Composable ApplicationScope.() -> Unit,
) {
    runApplication(RavenHost(followSystemAccessibility = followSystemAccessibility)) { RavenApplicationScope.content() }
}

/** One window with [content], closed by its close button: shorthand for [application] with one [Window]. */
public fun singleWindowApplication(
    title: String = "Untitled",
    width: Int = 800,
    height: Int = 600,
    followSystemAccessibility: Boolean = true,
    content: @Composable () -> Unit,
) {
    application(followSystemAccessibility) {
        Window(onCloseRequest = ::exitApplication, title = title, width = width, height = height, content = content)
    }
}

/** Ends the application, as [ApplicationScope.exitApplication] does. */
internal fun exitApplication() {
    RavenApplicationScope.exitApplication()
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
