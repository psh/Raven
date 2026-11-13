package raven.compose.ui

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.interpretObjCPointerOrNull
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.interpretCPointer
import platform.AppKit.NSView
import platform.AppKit.NSWindow

/** Searches [window]'s content view and its subviews, depth first, for the one whose layer is [layer]. */
internal actual fun cocoaViewOfLayer(window: COpaquePointer?, layer: COpaquePointer?): COpaquePointer? {
    if (window == null || layer == null) return null
    val nsWindow = interpretObjCPointerOrNull<NSWindow>(window.rawValue) ?: return null
    fun search(view: NSView): NSView? {
        if (view.layer?.objcPtr() == layer.rawValue) return view
        for (child in view.subviews) search(child as NSView)?.let { return it }
        return null
    }
    return nsWindow.contentView?.let(::search)?.let { interpretCPointer(it.objcPtr()) }
}
