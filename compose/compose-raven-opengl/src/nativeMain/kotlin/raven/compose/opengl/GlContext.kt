package raven.compose.opengl

import raven.compose.ui.NativeWindowHandle

/** A GL context of the platform's: NSOpenGLContext on macOS. */
internal interface GlContext : AutoCloseable {
    fun makeCurrent()

    /** Follows a change of its view's size. */
    fun update()

    /** Presents what was drawn. */
    fun flush()
}

/** A context drawing into [handle]'s view; null where it can't be made, or off macOS for now. */
internal expect fun glContextOn(handle: NativeWindowHandle): GlContext?

/** A framebuffer of [width] by [height] pixels in a context of its own, read back as RGBA. */
internal interface OffscreenTarget : AutoCloseable {
    /** Makes its context current and binds its framebuffer, to draw into. */
    fun begin()

    /** Reads what was drawn into [rgba], row by row from the top. */
    fun read(rgba: ByteArray)

    fun resize(width: Int, height: Int)
}

/** An offscreen target; null where no GL context can be made, or off macOS for now. */
internal expect fun offscreenTarget(width: Int, height: Int): OffscreenTarget?
