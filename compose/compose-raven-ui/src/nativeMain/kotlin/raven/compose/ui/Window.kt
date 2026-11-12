@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import raven.cinterop.RBackdrop
import raven.cinterop.RNativeWindow
import raven.cinterop.R_BACKDROP_ACRYLIC
import raven.cinterop.R_BACKDROP_BLUR
import raven.cinterop.R_BACKDROP_MICA
import raven.cinterop.R_BACKDROP_NONE
import raven.cinterop.R_BACKDROP_TRANSPARENT
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_OVERLAY
import raven.cinterop.R_WINDOW_BORDERLESS
import raven.cinterop.R_WINDOW_CUSTOM_HEADER
import raven.cinterop.R_WINDOW_DEFAULT
import raven.cinterop.R_WINDOW_HIDDEN
import raven.cinterop.R_WINDOW_MAXIMIZED
import raven.cinterop.R_WINDOW_NO_RESIZE
import raven.cinterop.R_WINDOW_TRANSPARENT
import raven.cinterop.Rid
import raven.cinterop.Rwidget_create
import raven.cinterop.Rwindow_create
import raven.cinterop.Rwindow_destroy
import raven.cinterop.Rwindow_get_backdrop
import raven.cinterop.Rwindow_get_native_handle
import raven.cinterop.Rwindow_get_position
import raven.cinterop.Rwindow_get_size
import raven.cinterop.Rwindow_is_maximized
import raven.cinterop.Rwindow_is_minimized
import raven.cinterop.Rwindow_maximize
import raven.cinterop.Rwindow_minimize
import raven.cinterop.Rwindow_restore
import raven.cinterop.Rwindow_set_backdrop
import raven.cinterop.Rwindow_set_clear_color
import raven.cinterop.Rwindow_set_close_callback
import raven.cinterop.Rwindow_set_content
import raven.cinterop.Rwindow_set_min_size
import raven.cinterop.Rwindow_set_opacity
import raven.cinterop.Rwindow_set_position
import raven.cinterop.Rwindow_set_resize_callback
import raven.cinterop.Rwindow_set_size
import raven.cinterop.Rwindow_set_title
import raven.cinterop.Rwindow_show
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.isSpecified
import raven.compose.ui.graphics.toRaven
import raven.compose.ui.platform.LocalWindowInfo
import raven.compose.ui.platform.WindowInfo
import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.isSpecified
import kotlin.math.roundToInt

/**
 * A window of the [application], with [content] in its own composition. It emits no node into its
 * parent.
 *
 * Closing is controlled: the close button calls [onCloseRequest] and leaves the window open, and
 * the window closes when it leaves the composition.
 *
 * [state] holds the window's size, position and placement, and follows what the user does to the
 * window. [title], [minimumSize] (`null` keeps Raven's), [background] (`Unspecified` keeps the
 * theme's) and [opacity] (0 to 1, the frame included) follow the composition. [customHeader] (the
 * content draws the title bar, with a `HeaderBar`), [transparent] (what is behind the window shows
 * where nothing opaque is drawn: see [WindowBackdrop]), [undecorated] (no frame) and [resizable]
 * are fixed when the window opens, and a change logs a warning.
 */
@Composable
public fun Window(
    onCloseRequest: () -> Unit,
    state: WindowState = rememberWindowState(),
    title: String = "Untitled",
    customHeader: Boolean = false,
    transparent: Boolean = false,
    undecorated: Boolean = false,
    resizable: Boolean = true,
    minimumSize: DpSize? = null,
    background: Color = Color.Unspecified,
    opacity: Float = 1f,
    content: @Composable () -> Unit,
) {
    val host = LocalRavenHost.current
    val parent = rememberCompositionContext()
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
    val currentContent by rememberUpdatedState(content)
    // Read here, the state recomposes the Window when the app or the user changes it.
    val settings = WindowSettings(
        title, WindowFlags(customHeader, transparent, undecorated, resizable), minimumSize, background, opacity,
        state.size, state.position, state.placement, state.isMinimized,
    )
    val opened = remember { OpenedWindow() }
    DisposableEffect(Unit) {
        val window = RavenWindow(host, parent, state, settings) { currentOnCloseRequest() }
        window.setContent { currentContent() }
        opened.window = window
        onDispose {
            opened.window = null
            window.dispose()
        }
    }
    SideEffect { opened.window?.update(state, settings) }
}

/** What a [Window] composed: its parameters, and the values of its state it read. */
internal data class WindowSettings(
    val title: String,
    val flags: WindowFlags,
    val minimumSize: DpSize?,
    val background: Color,
    val opacity: Float,
    val size: DpSize,
    val position: WindowPosition,
    val placement: WindowPlacement,
    val isMinimized: Boolean,
)

/** The parameters Raven takes only when it creates a window. */
internal data class WindowFlags(
    val customHeader: Boolean = false,
    val transparent: Boolean = false,
    val undecorated: Boolean = false,
    val resizable: Boolean = true,
) {
    /** Raven's flags for these, and for a window that opens [maximized]. */
    fun toRaven(maximized: Boolean): UInt {
        var flags = R_WINDOW_DEFAULT
        if (customHeader) flags = flags or R_WINDOW_CUSTOM_HEADER
        if (transparent) flags = flags or R_WINDOW_TRANSPARENT
        if (undecorated) flags = flags or R_WINDOW_BORDERLESS
        if (!resizable) flags = flags or R_WINDOW_NO_RESIZE
        if (maximized) flags = flags or R_WINDOW_MAXIMIZED
        return flags
    }

    /** The names of the parameters that differ in [other]. */
    fun changedIn(other: WindowFlags): List<String> = listOfNotNull(
        "customHeader".takeIf { customHeader != other.customHeader },
        "transparent".takeIf { transparent != other.transparent },
        "undecorated".takeIf { undecorated != other.undecorated },
        "resizable".takeIf { resizable != other.resizable },
    )
}

private class OpenedWindow {
    var window: RavenWindow? = null
}

/** The window a composable is in, for [WindowBackdrop]. */
internal val LocalRavenWindow = staticCompositionLocalOf<RavenWindow> { error("put it in a Window") }

/**
 * A Raven window, its fixed Overlay root, and the composition that fills it. Raven reports
 * resizes, but no moves and no change of placement, so the host has the window [followRaven]
 * once a frame.
 */
internal class RavenWindow(
    private val host: RavenHost,
    parent: CompositionContext,
    private var state: WindowState,
    settings: WindowSettings,
    private val onCloseRequest: () -> Unit,
) {
    private val flags = settings.flags

    // Created hidden, so it shows at its position and minimum size.
    val window: Rid = windowCalls.create(
        settings.title,
        settings.size.width.toUnits(DEFAULT_WIDTH),
        settings.size.height.toUnits(DEFAULT_HEIGHT),
        flags.toRaven(maximized = settings.placement == WindowPlacement.Maximized) or R_WINDOW_HIDDEN,
    )
    val root = RavenNode(Rwidget_create(R_WIDGET_OVERLAY), ChildKind.Many)
    private val composition = Composition(RavenApplier(root, host::onChangesApplied), parent)
    private val selfRef = StableRef.create(this)
    private val info = object : WindowInfo {
        override val isTransparent: Boolean = memScoped {
            val native = alloc<RNativeWindow>()
            // False for a headless window, which still fills in `transparent`.
            Rwindow_get_native_handle(window, native.ptr)
            native.transparent == R_TRUE
        }
    }

    // What the window last gave Raven, or Raven's defaults.
    private var title = settings.title
    private var composedFlags = flags
    private var minimumSize: DpSize? = null
    private var background = Color.Unspecified
    private var opacity = 1f
    private var opacityRefused = false

    // What Raven has: a state that differs from these was written by the app.
    private var ravenSize = readSize()
    private var ravenPosition = readPosition()
    private var ravenPlacement = readPlacement()

    init {
        // The window owns the root from now on, and deletes it with itself.
        Rwindow_set_content(window, root.widget)
        Rwindow_set_close_callback(window, onClose, selfRef.asCPointer())
        Rwindow_set_resize_callback(window, onResize, selfRef.asCPointer())
        host.windows += this
        update(state, settings)
        Rwindow_show(window)
        // The state takes what the window opened with in place of a default position or size.
        if (!state.position.isSpecified) state.position = ravenPosition.toPosition()
        if (!state.size.width.isSpecified || !state.size.height.isSpecified) state.size = ravenSize.toDpSize()
    }

    fun setContent(content: @Composable () -> Unit) = composition.setContent {
        CompositionLocalProvider(LocalRavenWindow provides this, LocalWindowInfo provides info, content = content)
    }

    /** Gives Raven what changed in [settings], and follows [state] from now on. */
    fun update(state: WindowState, settings: WindowSettings) {
        this.state = state
        if (settings.title != title) {
            title = settings.title
            Rwindow_set_title(window, title)
        }
        if (settings.flags != composedFlags) {
            composedFlags = settings.flags
            val changed = flags.changedIn(settings.flags)
            if (changed.isNotEmpty()) warn("Window: ${changed.joinToString()} can't change once the window is open, so the change is ignored")
        }
        if (settings.minimumSize != minimumSize) {
            minimumSize = settings.minimumSize
            windowCalls.setMinSize(window, minimumSize?.width.toUnits(0), minimumSize?.height.toUnits(0))
        }
        if (settings.background != background) {
            if (settings.background.isSpecified) {
                windowCalls.setClearColor(window, settings.background)
            } else {
                warn("Window: Raven can't give a window its theme's background back, so it keeps $background")
            }
            background = settings.background
        }
        if (settings.opacity != opacity) {
            opacity = settings.opacity
            if (!windowCalls.setOpacity(window, opacity) && !opacityRefused) {
                opacityRefused = true
                warn("Window: the window system refused opacity $opacity, so the window stays opaque")
            }
        }
        updateState(settings)
    }

    private fun updateState(settings: WindowSettings) {
        val size = LogicalSize(settings.size.width.toUnits(ravenSize.width), settings.size.height.toUnits(ravenSize.height))
        if (size != ravenSize) {
            ravenSize = size
            Rwindow_set_size(window, size.width, size.height)
        }
        val position = settings.position
        if (position is WindowPosition.Absolute) {
            val point = LogicalPoint(position.x.toUnits(ravenPosition.x), position.y.toUnits(ravenPosition.y))
            if (point != ravenPosition) {
                ravenPosition = point
                Rwindow_set_position(window, point.x, point.y)
            }
        }
        val placement = when {
            settings.isMinimized -> RavenPlacement.Minimized
            settings.placement == WindowPlacement.Maximized -> RavenPlacement.Maximized
            else -> RavenPlacement.Floating
        }
        if (placement != ravenPlacement) {
            ravenPlacement = placement
            when (placement) {
                RavenPlacement.Floating -> Rwindow_restore(window)
                RavenPlacement.Maximized -> Rwindow_maximize(window)
                RavenPlacement.Minimized -> Rwindow_minimize(window)
            }
        }
    }

    /** Writes the user's moves and changes of placement since the last frame into the state. */
    fun followRaven() {
        val position = readPosition()
        if (position != ravenPosition) {
            ravenPosition = position
            state.position = position.toPosition()
        }
        val placement = readPlacement()
        if (placement != ravenPlacement) {
            ravenPlacement = placement
            // A minimised window keeps the placement it comes back to.
            state.isMinimized = placement == RavenPlacement.Minimized
            if (placement == RavenPlacement.Maximized) state.placement = WindowPlacement.Maximized
            if (placement == RavenPlacement.Floating) state.placement = WindowPlacement.Floating
        }
    }

    /**
     * Asks Raven for [backdrop], and returns the backdrop in effect. A window that isn't
     * transparent keeps none, and Raven isn't asked.
     */
    fun setBackdrop(backdrop: Backdrop): Backdrop {
        if (!flags.transparent) {
            if (backdrop != Backdrop.None) warn("WindowBackdrop: the window isn't transparent, so Backdrop.$backdrop is ignored")
            return Backdrop.None
        }
        if (!windowCalls.setBackdrop(window, backdrop.toRaven())) warn("WindowBackdrop: the window system refused Backdrop.$backdrop")
        return Rwindow_get_backdrop(window).toBackdrop()
    }

    /** Disposes the composition before the window, so no node holds a widget Raven deleted. */
    fun dispose() {
        host.windows -= this
        composition.dispose()
        Rwindow_destroy(window)
        selfRef.dispose()
    }

    fun requestClose() {
        try {
            onCloseRequest()
        } catch (t: Throwable) {
            host.reportError(t)
        }
    }

    fun onResized(width: Int, height: Int) {
        // A minimised window can be resized to nothing (Win32): its state keeps the size it comes back to.
        if (width <= 0 || height <= 0) return
        try {
            ravenSize = LogicalSize(width, height)
            state.size = ravenSize.toDpSize()
        } catch (t: Throwable) {
            host.reportError(t)
        }
    }

    private fun readSize(): LogicalSize = memScoped {
        val width = alloc<IntVar>()
        val height = alloc<IntVar>()
        Rwindow_get_size(window, width.ptr, height.ptr)
        LogicalSize(width.value, height.value)
    }

    private fun readPosition(): LogicalPoint = memScoped {
        val x = alloc<IntVar>()
        val y = alloc<IntVar>()
        Rwindow_get_position(window, x.ptr, y.ptr)
        LogicalPoint(x.value, y.value)
    }

    private fun readPlacement(): RavenPlacement = when {
        Rwindow_is_minimized(window) == R_TRUE -> RavenPlacement.Minimized
        Rwindow_is_maximized(window) == R_TRUE -> RavenPlacement.Maximized
        else -> RavenPlacement.Floating
    }
}

/**
 * The window calls whose effect a headless Raven doesn't show, or that only a real window system
 * refuses: the tests replace [windowCalls] to see what a window asks for, or to refuse it.
 */
internal open class WindowCalls {
    open fun create(title: String, width: Int, height: Int, flags: UInt): Rid = Rwindow_create(title, width, height, flags)

    open fun setMinSize(window: Rid, width: Int, height: Int) = Rwindow_set_min_size(window, width, height)

    open fun setClearColor(window: Rid, color: Color) = color.toRaven { r, g, b, a -> Rwindow_set_clear_color(window, r, g, b, a) }

    open fun setOpacity(window: Rid, opacity: Float): Boolean = Rwindow_set_opacity(window, opacity) == R_TRUE

    open fun setBackdrop(window: Rid, backdrop: RBackdrop): Boolean = Rwindow_set_backdrop(window, backdrop) == R_TRUE
}

internal var windowCalls: WindowCalls = WindowCalls()

/** A window warning, given each time: through the layout warnings' sink. */
private fun warn(warning: String) = layoutWarningSink(warning)

private enum class RavenPlacement { Floating, Maximized, Minimized }

private data class LogicalSize(val width: Int, val height: Int) {
    fun toDpSize() = DpSize(width.dp, height.dp)
}

private data class LogicalPoint(val x: Int, val y: Int) {
    fun toPosition() = WindowPosition(x.dp, y.dp)
}

/** Rounded to whole logical units; [unspecified] for a null or unspecified length. */
private fun Dp?.toUnits(unspecified: Int): Int = if (this != null && isSpecified) value.roundToInt() else unspecified

private const val DEFAULT_WIDTH = 800
private const val DEFAULT_HEIGHT = 600

private fun Backdrop.toRaven(): RBackdrop = when (this) {
    Backdrop.None -> R_BACKDROP_NONE
    Backdrop.Transparent -> R_BACKDROP_TRANSPARENT
    Backdrop.Blur -> R_BACKDROP_BLUR
    Backdrop.Acrylic -> R_BACKDROP_ACRYLIC
    Backdrop.Mica -> R_BACKDROP_MICA
}

private fun RBackdrop.toBackdrop(): Backdrop = when (this) {
    R_BACKDROP_TRANSPARENT -> Backdrop.Transparent
    R_BACKDROP_BLUR -> Backdrop.Blur
    R_BACKDROP_ACRYLIC -> Backdrop.Acrylic
    R_BACKDROP_MICA -> Backdrop.Mica
    else -> Backdrop.None
}

private val onClose = staticCFunction { _: Rid, userData: COpaquePointer? ->
    userData!!.asStableRef<RavenWindow>().get().requestClose()
    R_FALSE
}

private val onResize = staticCFunction { _: Rid, width: Int, height: Int, userData: COpaquePointer? ->
    userData!!.asStableRef<RavenWindow>().get().onResized(width, height)
}
