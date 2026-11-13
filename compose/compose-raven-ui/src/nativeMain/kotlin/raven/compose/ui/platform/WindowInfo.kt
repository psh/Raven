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

    /**
     * The window's last frame, as Raven measured it. A plain call, not snapshot state, since the
     * numbers change every frame: sample it from a loop of your own. All zeros before the first frame.
     */
    public fun frameStats(): FrameStats
}

/**
 * The timings and counters of one frame of a window: how long Raven took to lay it out, paint it
 * and render it, in milliseconds, and how much it drew. [frameIndex] counts the window's frames, from 0.
 */
public data class FrameStats(
    val layoutMs: Double,
    val paintMs: Double,
    val renderMs: Double,
    val widgetCount: Int,
    val primitiveCount: Int,
    val drawCallCount: Int,
    /** Times the frame was copied and blurred to be seen through: 0 without glass. */
    val backdropCount: Int,
    val paintJobCount: Int,
    val frameIndex: Long,
)

/** The [WindowInfo] of the window the content is in. */
public val LocalWindowInfo: ProvidableCompositionLocal<WindowInfo> = staticCompositionLocalOf { error("no Window") }
