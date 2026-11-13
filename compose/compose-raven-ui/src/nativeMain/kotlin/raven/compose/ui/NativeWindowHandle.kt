package raven.compose.ui

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCPointer
import raven.cinterop.RNativeWindow
import raven.cinterop.RWindowSystem
import raven.cinterop.R_TRUE
import raven.cinterop.R_WINDOW_SYSTEM_COCOA
import raven.cinterop.R_WINDOW_SYSTEM_NONE
import raven.cinterop.R_WINDOW_SYSTEM_WAYLAND
import raven.cinterop.R_WINDOW_SYSTEM_WIN32
import raven.cinterop.R_WINDOW_SYSTEM_X11
import raven.cinterop.Rid
import raven.cinterop.Rviewport_get_native_handle
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_get_native_handle

/** The window system a [NativeWindowHandle] belongs to. */
public enum class WindowSystem { Win32, X11, Wayland, Cocoa, Other }

/**
 * A native child window's handles, as Raven gives them, for an app to render into with its own API:
 * - Win32: [window] is the `HWND`.
 * - X11: [display] is the `Display*`, [window] the `Window`, [visual] the visual.
 * - Wayland: [display] is the `wl_display*`, [window] the `wl_surface*`.
 * - Cocoa: [display] is the `NSWindow*`, [window] the child's `CAMetalLayer*` (Raven reports the
 *   layer), and [view] the child's `NSView*`, found in the window's view tree by that layer: Metal
 *   draws into the layer, and an OpenGL context attaches to the view.
 */
public data class NativeWindowHandle(
    val system: WindowSystem,
    val display: COpaquePointer?,
    val window: Long,
    val visual: Long,
    val view: COpaquePointer? = null,
)

private fun RWindowSystem.toWindowSystem(): WindowSystem = when (this) {
    R_WINDOW_SYSTEM_WIN32 -> WindowSystem.Win32
    R_WINDOW_SYSTEM_X11 -> WindowSystem.X11
    R_WINDOW_SYSTEM_WAYLAND -> WindowSystem.Wayland
    R_WINDOW_SYSTEM_COCOA -> WindowSystem.Cocoa
    else -> WindowSystem.Other
}

/** The handles of [viewport]'s native child window, which it has; null when Raven gives none. */
@InternalComposeRavenApi
public fun nativeViewportHandle(viewport: Rid): NativeWindowHandle? = memScoped {
    val native = alloc<RNativeWindow>()
    if (Rviewport_get_native_handle(viewport, native.ptr) != R_TRUE) return null
    val system = native.system.toWindowSystem()
    val view = if (system == WindowSystem.Cocoa) {
        val top = alloc<RNativeWindow>()
        if (Rwindow_get_native_handle(Rwidget_get_window(viewport), top.ptr) == R_TRUE) {
            cocoaViewOfLayer(top.display, native.window.toLong().toCPointer())
        } else {
            null
        }
    } else {
        null
    }
    NativeWindowHandle(system, native.display, native.window.toLong(), native.visual.toLong(), view)
}

/**
 * Whether [viewport]'s window is one of a window system, which can have native child windows; a
 * headless host's windows aren't.
 */
@InternalComposeRavenApi
public fun canHaveNativeChild(viewport: Rid): Boolean = memScoped {
    val native = alloc<RNativeWindow>()
    Rwindow_get_native_handle(Rwidget_get_window(viewport), native.ptr) == R_TRUE && native.system != R_WINDOW_SYSTEM_NONE
}

/** The view under [window]'s content view whose layer is [layer]: null off Cocoa, or when there is none. */
internal expect fun cocoaViewOfLayer(window: COpaquePointer?, layer: COpaquePointer?): COpaquePointer?
