package raven.compose.opengl

import raven.compose.ui.graphics.ImageBitmap

/**
 * A framebuffer of [width] by [height] pixels that [render] draws into with OpenGL, in a context of
 * its own, read back into [image] after each drawing: GL under the UI, shown with `Viewport` or
 * `Image`. Reading back costs a copy through the CPU each time. Make it, and use it, while Raven
 * runs, on its UI thread; [close] frees the framebuffer and the image.
 */
public class OpenGLTexture(width: Int, height: Int) : AutoCloseable {
    init {
        require(width > 0 && height > 0) { "a texture is at least 1 x 1 pixels, not $width x $height" }
    }

    private val target: OffscreenTarget =
        offscreenTarget(width, height) ?: throw UnsupportedOperationException("OpenGLTexture: no GL context can be made here")

    public var width: Int = width
        private set
    public var height: Int = height
        private set

    // The pixels read back last, row by row from the top.
    internal var pixels = ByteArray(width * height * 4)
        private set

    /** The bitmap the drawing is read back into. */
    public val image: ImageBitmap = ImageBitmap(width, height, pixels)

    /** Draws with [draw], the context current and the framebuffer bound, then shows it in [image]. */
    public fun render(draw: () -> Unit) {
        target.begin()
        draw()
        target.read(pixels)
        image.update(width, height, pixels)
    }

    /** Gives the framebuffer a new size; [render] draws at it from then on. */
    public fun resize(width: Int, height: Int) {
        require(width > 0 && height > 0) { "a texture is at least 1 x 1 pixels, not $width x $height" }
        target.resize(width, height)
        this.width = width
        this.height = height
        pixels = ByteArray(width * height * 4)
    }

    override fun close() {
        target.close()
        image.close()
    }
}
