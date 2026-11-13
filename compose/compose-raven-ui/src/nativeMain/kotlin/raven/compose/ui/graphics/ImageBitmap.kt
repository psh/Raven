@file:OptIn(InternalComposeRavenApi::class, ExperimentalNativeApi::class, ExperimentalUnsignedTypes::class)

package raven.compose.ui.graphics

import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.value
import kotlinx.coroutines.Runnable
import raven.cinterop.RFilterType
import raven.cinterop.R_FILTER_LINEAR
import raven.cinterop.R_FILTER_MIPMAP
import raven.cinterop.R_FILTER_NEAREST
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rtexture_create
import raven.cinterop.Rtexture_create_from_pixels
import raven.cinterop.Rtexture_destroy
import raven.cinterop.Rtexture_get_size
import raven.cinterop.Rtexture_set_filter
import raven.cinterop.Rtexture_update
import raven.cinterop.Rwidget_invalidate
import raven.cinterop.Rwidget_is_valid
import raven.compose.ui.HostEpoch
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.RavenDispatcher
import raven.compose.ui.theme.runningHost
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

/**
 * How a bitmap is smoothed when it is shown at another size than its own, as Compose's: the
 * texture's filter in Raven, so every `Image` showing the bitmap shows it alike.
 */
public enum class FilterQuality {
    /** No smoothing: pixel art stays sharp. */
    None,

    /** Linear: the default of bitmaps made from pixels. */
    Low,

    /** Mipmapped: an image shown much smaller than its size stays clean, for a third more GPU memory. */
    Medium,

    /** Mipmapped, as [Medium]: Raven has one mipmapped filter. */
    High,
}

/**
 * An image on the GPU: a Raven texture. Make one from pixels with `ImageBitmap(width, height,
 * rgba)`, or from an image file's bytes with [decodeToImageBitmap]. It can't be read back.
 *
 * Every call must be made while Raven runs, on its UI thread; pixels can be computed elsewhere
 * (an app's coroutines on `Dispatchers.Default`) and given to [update] there. The texture is freed
 * by [close], or once the bitmap is unreachable, by a `Cleaner`; both do nothing once the run of
 * Raven that made it has shut down, and using it then throws.
 */
public class ImageBitmap internal constructor(texture: Rid, filterQuality: FilterQuality) : AutoCloseable {
    private val handle = TextureHandle(texture, HostEpoch.current, runningHost("ImageBitmap").dispatcher)

    // Frees the texture on the UI thread once the bitmap is unreachable. It holds the handle only.
    @Suppress("unused")
    private val cleaner = createCleaner(handle) { it.destroyLater() }

    // The widgets showing the bitmap, which Raven doesn't redraw for a texture update.
    private val viewers = LinkedHashSet<Rid>()

    /** The width in pixels; [update] can change it. */
    public var width: Int = 0
        private set

    /** The height in pixels; [update] can change it. */
    public var height: Int = 0
        private set

    init {
        readSize()
    }

    /** How the bitmap is smoothed when it is scaled: `Rtexture_set_filter`. */
    public var filterQuality: FilterQuality = filterQuality
        set(value) {
            textureCalls.setFilter(texture, value.toRaven())
            field = value
        }

    /** The Raven texture, which only the run of Raven that made it can use. */
    @InternalComposeRavenApi
    public val texture: Rid
        get() {
            HostEpoch.checkCurrent(handle.epoch)
            check(!handle.destroyed) { "the ImageBitmap is closed" }
            return handle.texture
        }

    /**
     * Replaces the pixels with [rgba], `width * height * 4` bytes of straight RGBA, row by row
     * from the top. The size may change. Every widget showing the bitmap is laid out and drawn
     * again, since Raven doesn't redraw for a texture update.
     */
    public fun update(width: Int, height: Int, rgba: ByteArray) {
        requirePixels(width, height, rgba)
        Rtexture_update(texture, rgba.asUByteArray().refTo(0), width, height)
        this.width = width
        this.height = height
        viewers.retainAll { Rwidget_is_valid(it) == R_TRUE }
        viewers.forEach { textureCalls.invalidate(it) }
    }

    /**
     * The texture changed by other means than [update] (a Vulkan image view replaced): its size is
     * read again, and every widget showing it is laid out and drawn again.
     */
    @InternalComposeRavenApi
    public fun textureChanged() {
        readSize()
        viewers.retainAll { Rwidget_is_valid(it) == R_TRUE }
        viewers.forEach { textureCalls.invalidate(it) }
    }

    /**
     * Records that [widget] shows the bitmap, so [update] lays it out and draws it again. A widget
     * destroyed since is forgotten, and one that shows another bitmap since is only redrawn.
     */
    @InternalComposeRavenApi
    public fun addViewer(widget: Rid) {
        viewers.retainAll { Rwidget_is_valid(it) == R_TRUE }
        viewers += widget
    }

    /** Frees the texture now. It does nothing a second time, or once its run of Raven has shut down. */
    override fun close() {
        handle.destroy()
    }

    private fun readSize() = memScoped {
        val w = alloc<IntVar>()
        val h = alloc<IntVar>()
        Rtexture_get_size(handle.texture, w.ptr, h.ptr)
        width = w.value
        height = h.value
    }

    /** For the constructors other modules add, such as `compose-raven-vulkan`'s `fromVulkanImageView`. */
    public companion object
}

/**
 * A bitmap of [width] by [height] pixels from [rgba], `width * height * 4` bytes of straight RGBA,
 * row by row from the top, smoothed with [filterQuality] when it is scaled.
 */
public fun ImageBitmap(width: Int, height: Int, rgba: ByteArray, filterQuality: FilterQuality = FilterQuality.Low): ImageBitmap {
    requirePixels(width, height, rgba)
    runningHost("ImageBitmap")
    val texture = Rtexture_create_from_pixels(rgba.asUByteArray().refTo(0), width, height)
    check(texture != R_INVALID_ID) { "Raven made no texture of $width x $height pixels" }
    if (filterQuality != FilterQuality.Low) textureCalls.setFilter(texture, filterQuality.toRaven())
    return ImageBitmap(texture, filterQuality)
}

/**
 * A bitmap over [texture], which another module made (`compose-raven-vulkan`'s image views): it
 * frees the texture as any bitmap does.
 */
@InternalComposeRavenApi
public fun imageBitmapOf(texture: Rid, filterQuality: FilterQuality = FilterQuality.Low): ImageBitmap {
    runningHost("ImageBitmap")
    return ImageBitmap(texture, filterQuality)
}

/**
 * Decodes the bytes of an image file (PNG, JPEG, BMP, TGA, GIF, PSD or SVG) into a bitmap, on the
 * calling thread. It is mipmapped, as Raven makes textures from files, so its [filterQuality][ImageBitmap.filterQuality]
 * is [FilterQuality.Medium]. Throws [IllegalArgumentException] for bytes Raven can't decode.
 */
public fun ByteArray.decodeToImageBitmap(): ImageBitmap {
    runningHost("decodeToImageBitmap")
    require(isNotEmpty()) { "no bytes to decode" }
    val texture = Rtexture_create(refTo(0), size.toULong())
    require(texture != R_INVALID_ID) { "the bytes are no image Raven can decode" }
    return ImageBitmap(texture, FilterQuality.Medium)
}

private fun requirePixels(width: Int, height: Int, rgba: ByteArray) {
    require(width > 0 && height > 0) { "a bitmap is at least 1 x 1 pixels, not $width x $height" }
    require(rgba.size == width * height * 4) { "$width x $height pixels are ${width * height * 4} bytes of RGBA, not ${rgba.size}" }
}

/**
 * A bitmap's texture and the run of Raven it belongs to, which is all its `Cleaner` holds. Its
 * calls run on the UI thread: a `Cleaner` dispatches there, and the host's dispatcher drops the
 * dispatch once the host has closed.
 */
private class TextureHandle(val texture: Rid, val epoch: Int, private val dispatcher: RavenDispatcher) {
    var destroyed = false
        private set

    fun destroy() {
        if (destroyed || epoch != HostEpoch.current) return
        destroyed = true
        Rtexture_destroy(texture)
    }

    fun destroyLater() = dispatcher.dispatch(EmptyCoroutineContext, Runnable { destroy() })
}

internal fun FilterQuality.toRaven(): RFilterType = when (this) {
    FilterQuality.None -> R_FILTER_NEAREST
    FilterQuality.Low -> R_FILTER_LINEAR
    FilterQuality.Medium, FilterQuality.High -> R_FILTER_MIPMAP
}

/** The texture calls Raven has no getter for: the tests replace [textureCalls] to see them. */
internal open class TextureCalls {
    open fun setFilter(texture: Rid, filter: RFilterType) = Rtexture_set_filter(texture, filter)

    open fun invalidate(widget: Rid) = Rwidget_invalidate(widget)
}

internal var textureCalls: TextureCalls = TextureCalls()
