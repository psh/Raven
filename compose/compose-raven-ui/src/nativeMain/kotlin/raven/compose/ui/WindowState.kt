package raven.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp

/** How a window sits on the screen when it isn't minimised. */
public enum class WindowPlacement {
    /** At its own size and position. */
    Floating,

    /** Filling the screen's work area. */
    Maximized,
}

/** Where a window is on the screen, in logical units from its top left corner. */
public sealed class WindowPosition {
    public abstract val x: Dp
    public abstract val y: Dp

    /** False for [PlatformDefault]. */
    public abstract val isSpecified: Boolean

    /** Where the window system puts a new window. Once the window is open, its real position replaces it. */
    public data object PlatformDefault : WindowPosition() {
        override val x: Dp get() = Dp.Unspecified
        override val y: Dp get() = Dp.Unspecified
        override val isSpecified: Boolean get() = false
    }

    /** At [x], [y], rounded to whole logical units for Raven. */
    public data class Absolute(override val x: Dp, override val y: Dp) : WindowPosition() {
        override val isSpecified: Boolean get() = true
    }
}

/** The position [x], [y]: [WindowPosition.Absolute]. */
public fun WindowPosition(x: Dp, y: Dp): WindowPosition = WindowPosition.Absolute(x, y)

/**
 * A window's size, position and placement, as snapshot state. A write moves, resizes, maximises or
 * minimises the window, and what the user does to the window is written back, so a composable that
 * reads the state follows it.
 */
@Stable
public interface WindowState {
    /** Floating or maximised. A minimised window keeps the placement it comes back to. */
    public var placement: WindowPlacement

    /** Whether the window is minimised. */
    public var isMinimized: Boolean

    /** Where the window is. [WindowPosition.PlatformDefault] becomes the real position once it opens. */
    public var position: WindowPosition

    /**
     * The size of the window's content, its frame left out, as Raven sizes windows. An unspecified
     * width or height keeps the window's own (800 by 600 for a new window), until the window is
     * resized.
     */
    public var size: DpSize
}

/** A [WindowState] outside composition, for [singleWindowApplication]. */
public fun WindowState(
    placement: WindowPlacement = WindowPlacement.Floating,
    isMinimized: Boolean = false,
    position: WindowPosition = WindowPosition.PlatformDefault,
    size: DpSize = DpSize(800.dp, 600.dp),
): WindowState = WindowStateImpl(placement, isMinimized, position, size)

/** A [WindowState] remembered across recompositions, for a [Window]. */
@Composable
public fun rememberWindowState(
    placement: WindowPlacement = WindowPlacement.Floating,
    isMinimized: Boolean = false,
    position: WindowPosition = WindowPosition.PlatformDefault,
    size: DpSize = DpSize(800.dp, 600.dp),
): WindowState = remember { WindowState(placement, isMinimized, position, size) }

private class WindowStateImpl(
    placement: WindowPlacement,
    isMinimized: Boolean,
    position: WindowPosition,
    size: DpSize,
) : WindowState {
    override var placement by mutableStateOf(placement)
    override var isMinimized by mutableStateOf(isMinimized)
    override var position by mutableStateOf(position)
    override var size by mutableStateOf(size)
}
