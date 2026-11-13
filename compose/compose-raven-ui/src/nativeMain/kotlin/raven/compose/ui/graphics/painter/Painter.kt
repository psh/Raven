@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui.graphics.painter

import kotlinx.cinterop.IntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value
import raven.cinterop.Rfile_read
import raven.cinterop.Rid
import raven.cinterop.Rimage_decode
import raven.cinterop.Rmemory_free
import raven.cinterop.Rpixels_free
import raven.compose.ui.HostEpoch
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.ui.unit.IntOffset
import raven.compose.ui.unit.IntSize

/**
 * Something an `Image`, a menu item or a window shows: a part of a Raven texture. Raven draws no
 * vectors, so every painter is an image: a [BitmapPainter], or one from `painterResource`.
 */
public abstract class Painter internal constructor() {
    /** The texture, which only the run of Raven that made it can use. */
    @InternalComposeRavenApi
    public abstract val texture: Rid

    /** The part of [texture] shown. */
    @InternalComposeRavenApi
    public abstract val region: TextureRegion

    /** Records that [widget] shows this painter, so a bitmap's update redraws it. */
    @InternalComposeRavenApi
    public open fun addViewer(widget: Rid) {}

    /** The pixels of the whole image, for a window icon; null when they can't be read back. */
    internal open fun readPixels(): Pixels? = null
}

/** A part of a texture in Raven's UV coordinates, 0 to 1 from the top left. */
@InternalComposeRavenApi
public data class TextureRegion(val minU: Float, val minV: Float, val maxU: Float, val maxV: Float) {
    public companion object {
        public val Whole: TextureRegion = TextureRegion(0f, 0f, 1f, 1f)
    }
}

/** Straight RGBA pixels, row by row from the top. */
internal class Pixels(val width: Int, val height: Int, val rgba: ByteArray)

/**
 * Shows [image], or the part of it [srcSize] pixels large at [srcOffset], for a sprite sheet or an
 * icon atlas. The part is measured in the bitmap's pixels when the painter is made; a painter of
 * the whole bitmap shows the whole of it even after an `update` resizes it.
 */
public class BitmapPainter(
    public val image: ImageBitmap,
    public val srcOffset: IntOffset = IntOffset.Zero,
    public val srcSize: IntSize = IntSize(image.width, image.height),
) : Painter() {
    private val shownRegion = if (srcOffset == IntOffset.Zero && srcSize == IntSize(image.width, image.height)) {
        TextureRegion.Whole
    } else {
        require(srcOffset.x >= 0 && srcOffset.y >= 0 && srcSize.width > 0 && srcSize.height > 0) {
            "the part shown starts inside the image and has a size: $srcOffset, $srcSize"
        }
        require(srcOffset.x + srcSize.width <= image.width && srcOffset.y + srcSize.height <= image.height) {
            "the part shown, $srcSize at $srcOffset, ends outside the image of ${image.width} x ${image.height}"
        }
        TextureRegion(
            srcOffset.x.toFloat() / image.width,
            srcOffset.y.toFloat() / image.height,
            (srcOffset.x + srcSize.width).toFloat() / image.width,
            (srcOffset.y + srcSize.height).toFloat() / image.height,
        )
    }

    @InternalComposeRavenApi
    override val texture: Rid get() = image.texture

    @InternalComposeRavenApi
    override val region: TextureRegion get() = shownRegion

    @InternalComposeRavenApi
    override fun addViewer(widget: Rid): Unit = image.addViewer(widget)

    override fun equals(other: Any?): Boolean =
        other is BitmapPainter && image === other.image && srcOffset == other.srcOffset && srcSize == other.srcSize

    override fun hashCode(): Int = (image.hashCode() * 31 + srcOffset.hashCode()) * 31 + srcSize.hashCode()

    override fun toString(): String = "BitmapPainter(${image.width} x ${image.height}, $srcOffset, $srcSize)"
}

/**
 * An image file's texture, decoded on a worker: what `painterResource` caches per host. [path] is
 * resolved already, and the texture belongs to the run of Raven of [epoch].
 */
internal class ResourcePainter(val path: String, private val ravenTexture: Rid, private val epoch: Int) : Painter() {
    override val texture: Rid
        get() {
            HostEpoch.checkCurrent(epoch)
            return ravenTexture
        }

    override val region: TextureRegion get() = TextureRegion.Whole

    /** The file read and decoded again, since a texture can't be read back. */
    override fun readPixels(): Pixels? = memScoped {
        val size = alloc<ULongVar>()
        val data = Rfile_read(path, size.ptr) ?: return null
        try {
            val width = alloc<IntVar>()
            val height = alloc<IntVar>()
            val pixels = Rimage_decode(data, size.value, width.ptr, height.ptr) ?: return null
            try {
                Pixels(width.value, height.value, pixels.readBytes(width.value * height.value * 4))
            } finally {
                Rpixels_free(pixels)
            }
        } finally {
            Rmemory_free(data)
        }
    }

    override fun toString(): String = "painterResource($path)"
}
