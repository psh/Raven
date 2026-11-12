package raven.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** What the window system shows behind a transparent window. */
public enum class Backdrop {
    /** Opaque: the window's background, or the theme's wallpaper. */
    None,

    /** The screen behind the window, as it is. */
    Transparent,

    /** The screen behind the window, blurred. */
    Blur,

    /** Blurred, tinted and grained (Windows' acrylic). */
    Acrylic,

    /** A soft tint taken from the desktop's wallpaper (Windows 11's mica). */
    Mica,
}

/**
 * Shows [backdrop] behind the window this is in, which must be `Window(transparent = true)`, until
 * it leaves the composition and the window goes back to [Backdrop.None]. Returns the backdrop in
 * effect, which the window system may have replaced with another (a warning says when it refused):
 * [Backdrop.None] until the first is applied, then its readers recompose.
 */
@Composable
public fun WindowBackdrop(backdrop: Backdrop): Backdrop {
    val window = LocalRavenWindow.current
    val applied = remember(window) { AppliedBackdrop(window) }
    SideEffect { applied.apply(backdrop) }
    return applied.obtained
}

private class AppliedBackdrop(private val window: RavenWindow) : RememberObserver {
    var obtained by mutableStateOf(Backdrop.None)
        private set
    private var asked: Backdrop? = null

    fun apply(backdrop: Backdrop) {
        if (backdrop == asked) return
        asked = backdrop
        obtained = window.setBackdrop(backdrop)
    }

    override fun onRemembered() = Unit

    // The window's composition is disposed before the window, so Raven still has it.
    override fun onForgotten() {
        if (asked != null && asked != Backdrop.None) window.setBackdrop(Backdrop.None)
    }

    override fun onAbandoned() = Unit
}
