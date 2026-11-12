@file:OptIn(InternalComposeRavenApi::class, ExperimentalNativeApi::class)

package raven.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import raven.cinterop.Rquit
import raven.compose.ui.graphics.Color
import raven.compose.ui.theme.LocalThemePlacement
import raven.compose.ui.theme.ThemePlacement
import raven.compose.ui.unit.DpSize
import kotlin.experimental.ExperimentalNativeApi

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
 * With [followSystemAccessibility], text scale, contrast and reduced motion follow the OS. With
 * [liveReload], on in debug executables, Raven reloads the theme files the app loaded when they
 * change on disk; the `RAVEN_LIVE_RELOAD` environment variable, when set, decides instead.
 */
public fun application(
    followSystemAccessibility: Boolean = true,
    liveReload: Boolean = Platform.isDebugBinary,
    content: @Composable ApplicationScope.() -> Unit,
) {
    val host = RavenHost(followSystemAccessibility = followSystemAccessibility, liveReload = liveReload)
    runApplication(host) { RavenApplicationScope.content() }
}

/**
 * One window with [content], closed by its close button: shorthand for [application] with one
 * [Window], which takes [state] and the other parameters as they are. The outermost `RavenTheme`
 * of [content] counts as application level.
 */
public fun singleWindowApplication(
    state: WindowState = WindowState(),
    title: String = "Untitled",
    customHeader: Boolean = false,
    transparent: Boolean = false,
    undecorated: Boolean = false,
    resizable: Boolean = true,
    minimumSize: DpSize? = null,
    background: Color = Color.Unspecified,
    opacity: Float = 1f,
    followSystemAccessibility: Boolean = true,
    liveReload: Boolean = Platform.isDebugBinary,
    content: @Composable () -> Unit,
) {
    application(followSystemAccessibility, liveReload) {
        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = title,
            customHeader = customHeader,
            transparent = transparent,
            undecorated = undecorated,
            resizable = resizable,
            minimumSize = minimumSize,
            background = background,
            opacity = opacity,
        ) {
            SingleWindowContent(content)
        }
    }
}

/** The content of `singleWindowApplication`'s window, whose outermost `RavenTheme` is application level. */
@Composable
internal fun SingleWindowContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalThemePlacement provides ThemePlacement.TopOfSingleWindow, content = content)
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
