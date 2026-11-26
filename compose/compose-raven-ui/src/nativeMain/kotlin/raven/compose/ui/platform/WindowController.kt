package raven.compose.ui.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What a window's content can ask of its window, beside what its `WindowState` holds. Compose
 * Desktop does these through AWT's window; compose-raven has none, so they are here, as
 * [LocalWindowInfo] is for what the content can know.
 */
public interface WindowController {
    /**
     * Brings the window in front of the others with the keyboard focus, and makes the application
     * the active one (`Rwindow_focus`): for a window the user just asked for, such as one opened
     * from a tray icon. A window composed with `visible = false` stays hidden, and nothing happens.
     */
    public fun toFront()

    /**
     * Asks the user to look at the window when it isn't the active one
     * (`Rwindow_request_attention`): its task bar button flashes on Windows, its Dock icon bounces
     * on macOS, it is marked urgent on Linux. [critical] keeps asking until the user comes to the
     * window; otherwise it asks a few times. Nothing happens when the window is the active one.
     * Returns false when the window system can't ask.
     */
    public fun requestAttention(critical: Boolean = false): Boolean
}

/** The [WindowController] of the window the content is in. */
public val LocalWindowController: ProvidableCompositionLocal<WindowController> = staticCompositionLocalOf { error("no Window") }
