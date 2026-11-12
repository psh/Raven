package raven.compose.ui.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/** What a window's content can know about its window. */
public interface WindowInfo {
    /**
     * Whether the window system really gave a see-through window. `Window(transparent = true)`
     * asks for one, but doesn't guarantee it.
     */
    public val isTransparent: Boolean
}

/** The [WindowInfo] of the window the content is in. */
public val LocalWindowInfo: ProvidableCompositionLocal<WindowInfo> = staticCompositionLocalOf { error("no Window") }
