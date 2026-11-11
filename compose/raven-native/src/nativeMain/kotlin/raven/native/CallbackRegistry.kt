package raven.native

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import raven.cinterop.Rid
import raven.cinterop.Rwidget_get_user_handle
import raven.cinterop.Rwidget_set_destroy_callback
import raven.cinterop.Rwidget_set_user_handle

/**
 * One [StableRef] per widget, to its Kotlin target (the node). The reference is the `user_data` of
 * every callback the widget has, and Raven's destroy callback disposes it once the widget is gone,
 * whether deleted directly, with its parent or window, or by `Rshutdown`.
 *
 * UI thread only, like every Raven widget call.
 */
public object CallbackRegistry {
    /** How many references are live: zero once every registered widget has been destroyed. */
    public var liveCount: Int = 0
        private set

    /**
     * Registers [target] for [widget] and returns the `user_data` to pass to its callbacks. A
     * widget is registered at most once.
     */
    public fun register(widget: Rid, target: Any): COpaquePointer {
        requireUiThread()
        check(Rwidget_get_user_handle(widget) == null) { "widget $widget is already registered" }
        val ref = StableRef.create(target).asCPointer()
        Rwidget_set_user_handle(widget, ref)
        Rwidget_set_destroy_callback(widget, disposeOnDestroy, ref)
        liveCount++
        return ref
    }

    /** The target registered for [widget], or null if it has none. */
    public fun targetOf(widget: Rid): Any? = Rwidget_get_user_handle(widget)?.asStableRef<Any>()?.get()

    // The handle Raven passes is already dead, so only the reference is touched.
    private val disposeOnDestroy = staticCFunction { _: Rid, userData: COpaquePointer? ->
        userData?.asStableRef<Any>()?.dispose()
        liveCount -= 1
    }
}

/** The target behind a callback's `user_data`, as returned by [CallbackRegistry.register]. */
public inline fun <reified T : Any> COpaquePointer.callbackTarget(): T = asStableRef<T>().get()
