package raven.compose.ui.graphics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.mkdtemp
import platform.posix.remove
import platform.posix.rmdir
import kotlinx.cinterop.cstr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import raven.cinterop.RFilterType
import raven.cinterop.R_FILTER_LINEAR
import raven.cinterop.R_FILTER_MIPMAP
import raven.cinterop.R_FILTER_NEAREST
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rtexture_get_size
import raven.cinterop.Rtexture_is_valid
import raven.cinterop.Rtexture_wait
import raven.cinterop.R_WIDGET_IMAGE
import raven.cinterop.Rwidget_create
import raven.cinterop.Rwidget_destroy
import raven.compose.ui.Window
import raven.compose.ui.WindowCalls
import raven.compose.ui.graphics.painter.BitmapPainter
import raven.compose.ui.graphics.painter.Painter
import raven.compose.ui.graphics.painter.Pixels
import raven.compose.ui.graphics.painter.ResourcePainter
import raven.compose.ui.graphics.painter.TextureRegion
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.res.painterResource
import raven.compose.ui.res.registerResource
import raven.compose.ui.res.resolveResource
import raven.compose.ui.runUntilIdle
import raven.compose.ui.takeRavenErrors
import raven.compose.ui.unit.IntOffset
import raven.compose.ui.unit.IntSize
import raven.compose.ui.windowCalls
import raven.compose.ui.withHost
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ImageBitmapTest {
    @Test
    fun aBitmapFromPixelsHasTheirSize() = withHost({}) {
        val bitmap = ImageBitmap(3, 2, pixels(3, 2))
        assertEquals(3, bitmap.width)
        assertEquals(2, bitmap.height)
        assertEquals(3 to 2, textureSize(bitmap.texture))
        assertEquals(FilterQuality.Low, bitmap.filterQuality)
        bitmap.close()
    }

    @Test
    fun pixelsMustFillTheBitmap() = withHost({}) {
        assertFailsWith<IllegalArgumentException> { ImageBitmap(2, 2, ByteArray(15)) }
        assertFailsWith<IllegalArgumentException> { ImageBitmap(0, 2, ByteArray(0)) }
    }

    @Test
    fun anImageFilesBytesDecode() = withHost({}) {
        val bitmap = tgaFile(4, 3, pixels(4, 3)).decodeToImageBitmap()
        assertEquals(4, bitmap.width)
        assertEquals(3, bitmap.height)
        assertEquals(FilterQuality.Medium, bitmap.filterQuality)
        bitmap.close()
    }

    @Test
    fun bytesThatAreNoImageDontDecode() = withHost({}) {
        assertFailsWith<IllegalArgumentException> { byteArrayOf(1, 2, 3, 4).decodeToImageBitmap() }
        takeRavenErrors()
    }

    @Test
    fun anUpdateMayResizeTheBitmap() = withHost({}) {
        val bitmap = ImageBitmap(2, 2, pixels(2, 2))
        bitmap.update(5, 4, pixels(5, 4))
        assertEquals(5, bitmap.width)
        assertEquals(4, bitmap.height)
        assertEquals(5 to 4, textureSize(bitmap.texture))
        assertFailsWith<IllegalArgumentException> { bitmap.update(5, 4, pixels(2, 2)) }
        bitmap.close()
    }

    @Test
    fun theFilterQualityIsTheTexturesFilter() = withTextureCalls { filters ->
        withHost({}) {
            val pixelArt = ImageBitmap(2, 2, pixels(2, 2), FilterQuality.None)
            val smooth = ImageBitmap(2, 2, pixels(2, 2))
            assertEquals(listOf(pixelArt.texture to R_FILTER_NEAREST), filters.toList())
            smooth.filterQuality = FilterQuality.High
            pixelArt.filterQuality = FilterQuality.Low
            smooth.filterQuality = FilterQuality.Medium
            assertEquals(
                listOf(pixelArt.texture to R_FILTER_NEAREST, smooth.texture to R_FILTER_MIPMAP, pixelArt.texture to R_FILTER_LINEAR, smooth.texture to R_FILTER_MIPMAP),
                filters.toList(),
            )
            assertEquals(FilterQuality.Medium, smooth.filterQuality)
        }
    }

    @Test
    fun aClosedBitmapIsFreedOnce() = withHost({}) {
        val bitmap = ImageBitmap(2, 2, pixels(2, 2))
        val texture = bitmap.texture
        bitmap.close()
        assertTrue(Rtexture_is_valid(texture) != R_TRUE)
        bitmap.close()
        assertFailsWith<IllegalStateException> { bitmap.texture }
    }

    @Test
    fun aBitmapFromAnEarlierRavenDoesNothingWhenClosed() {
        var old: ImageBitmap? = null
        withHost({}) { old = ImageBitmap(2, 2, pixels(2, 2)) }
        val bitmap = assertNotNull(old)
        // Shut down: closing does nothing, and using it throws.
        bitmap.close()
        assertFailsWith<IllegalStateException> { bitmap.texture }
        assertFailsWith<IllegalStateException> { bitmap.update(2, 2, pixels(2, 2)) }
        withHost({}) {
            // The new Raven's first texture has the old one's handle; closing the old bitmap leaves it alone.
            val fresh = ImageBitmap(2, 2, pixels(2, 2))
            bitmap.close()
            assertEquals(R_TRUE, Rtexture_is_valid(fresh.texture))
            fresh.close()
        }
    }

    @Test
    fun anUpdateRedrawsTheWidgetsShowingTheBitmap() {
        val invalidated = mutableListOf<Rid>()
        val previous = textureCalls
        textureCalls = object : TextureCalls() {
            override fun invalidate(widget: Rid) {
                invalidated += widget
                super.invalidate(widget)
            }
        }
        try {
            withHost({}) {
                val bitmap = ImageBitmap(2, 2, pixels(2, 2))
                val shown = Rwidget_create(R_WIDGET_IMAGE)
                val gone = Rwidget_create(R_WIDGET_IMAGE)
                BitmapPainter(bitmap).addViewer(shown)
                bitmap.addViewer(gone)
                bitmap.update(3, 3, pixels(3, 3))
                assertEquals(listOf(shown, gone), invalidated.toList())
                // A destroyed widget is forgotten.
                Rwidget_destroy(gone)
                invalidated.clear()
                bitmap.update(2, 2, pixels(2, 2))
                assertEquals(listOf(shown), invalidated.toList())
                Rwidget_destroy(shown)
                bitmap.close()
            }
        } finally {
            textureCalls = previous
        }
    }

    @Test
    fun aPainterShowsAPartOfABitmap() = withHost({}) {
        val atlas = ImageBitmap(8, 4, pixels(8, 4))
        assertEquals(TextureRegion.Whole, BitmapPainter(atlas).region)
        assertEquals(TextureRegion(0.5f, 0.25f, 1f, 0.75f), BitmapPainter(atlas, IntOffset(4, 1), IntSize(4, 2)).region)
        assertEquals(BitmapPainter(atlas, IntOffset(4, 1), IntSize(4, 2)), BitmapPainter(atlas, IntOffset(4, 1), IntSize(4, 2)))
        assertFailsWith<IllegalArgumentException> { BitmapPainter(atlas, IntOffset(6, 0), IntSize(4, 4)) }
        // A painter of the whole bitmap still shows all of it once it grows.
        val whole = BitmapPainter(atlas)
        atlas.update(16, 16, pixels(16, 16))
        assertEquals(TextureRegion.Whole, whole.region)
        atlas.close()
    }

    @Test
    fun aRegisteredResourceIsFoundBeforeTheResourceRoot() {
        val bytes = tgaFile(3, 5, pixels(3, 5))
        registerResource("lane-c/embedded.tga", bytes)
        var first: Painter? = null
        var second: Painter? = null
        withHost({
            Window(onCloseRequest = {}) {
                first = painterResource("lane-c/embedded.tga")
                second = painterResource("lane-c/embedded.tga")
            }
        }, resourceRoot = "/nowhere") {
            assertEquals("lane-c/embedded.tga", resolveResource("lane-c/embedded.tga"))
            assertEquals("/nowhere/other.png", resolveResource("other.png"))
            assertEquals("/abs/other.png", resolveResource("/abs/other.png"))
            val painter = assertNotNull(first)
            assertSame(painter, second, "painterResource caches per host and path")
            Rtexture_wait(painter.texture)
            assertEquals(3 to 5, textureSize(painter.texture))
        }
    }

    @Test
    fun relativeResourcesResolveInTheResourceRoot() = withTempDirectory { dir ->
        val rgba = pixels(2, 3)
        writeFile("$dir/pic.tga", tgaFile(2, 3, rgba))
        var painter: Painter? = null
        withHost({ Window(onCloseRequest = {}) { painter = painterResource("pic.tga") } }, resourceRoot = dir) {
            val texture = assertNotNull(painter).texture
            Rtexture_wait(texture)
            assertEquals(2 to 3, textureSize(texture))
            val read = assertNotNull((painter as ResourcePainter).readPixels())
            assertContentEquals(rgba, read.rgba)
        }
        remove("$dir/pic.tga")
    }

    @Test
    fun aWindowsIconIsItsImageFilesPixels() {
        val rgba = pixels(2, 2)
        registerResource("lane-c/icon.tga", tgaFile(2, 2, rgba))
        val icons = mutableListOf<IntSize?>()
        val warnings = mutableListOf<String>()
        val calls = object : WindowCalls() {
            override fun setIcon(window: Rid, icon: Pixels?) {
                icons += icon?.let { IntSize(it.width, it.height) }
                if (icon != null) assertContentEquals(rgba, icon.rgba)
                super.setIcon(window, icon)
            }
        }
        var useIcon by mutableStateOf(true)
        var bitmapIcon by mutableStateOf(false)
        val previous = windowCalls
        val sink = layoutWarningSink
        windowCalls = calls
        layoutWarningSink = { warnings += it }
        try {
            withHost({
                val icon = when {
                    bitmapIcon -> BitmapPainter(ImageBitmap(1, 1, pixels(1, 1)))
                    useIcon -> painterResource("lane-c/icon.tga")
                    else -> null
                }
                Window(onCloseRequest = {}, icon = icon) {}
            }) {
                assertEquals(listOf<IntSize?>(IntSize(2, 2)), icons.toList())
                useIcon = false
                runUntilIdle()
                assertEquals(listOf(IntSize(2, 2), null), icons.toList())
                bitmapIcon = true
                runUntilIdle()
                assertEquals(2, icons.size)
                assertTrue(warnings.single().startsWith("Window: a window icon is read from its image file"), warnings.toString())
            }
        } finally {
            windowCalls = previous
            layoutWarningSink = sink
        }
    }
}

/** Distinct straight RGBA pixels, so a mix-up shows. */
internal fun pixels(width: Int, height: Int): ByteArray = ByteArray(width * height * 4) { (it * 7 + 1).toByte() }

/** An uncompressed 32-bit TGA file of [rgba], top row first: an image file Raven decodes, written without a library. */
internal fun tgaFile(width: Int, height: Int, rgba: ByteArray): ByteArray {
    val header = byteArrayOf(
        0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        (width and 0xFF).toByte(), (width shr 8).toByte(), (height and 0xFF).toByte(), (height shr 8).toByte(),
        32, 0x28, // 8 bits of alpha, rows from the top
    )
    val bgra = ByteArray(rgba.size) { i ->
        val pixel = i - i % 4
        when (i % 4) {
            0 -> rgba[pixel + 2]
            2 -> rgba[pixel]
            else -> rgba[i]
        }
    }
    return header + bgra
}

private fun textureSize(texture: Rid): Pair<Int, Int> = memScoped {
    val width = alloc<IntVar>()
    val height = alloc<IntVar>()
    Rtexture_get_size(texture, width.ptr, height.ptr)
    width.value to height.value
}

private fun withTextureCalls(block: (filters: MutableList<Pair<Rid, RFilterType>>) -> Unit) {
    val filters = mutableListOf<Pair<Rid, RFilterType>>()
    val previous = textureCalls
    textureCalls = object : TextureCalls() {
        override fun setFilter(texture: Rid, filter: RFilterType) {
            filters += texture to filter
            super.setFilter(texture, filter)
        }
    }
    try {
        block(filters)
    } finally {
        textureCalls = previous
    }
}

private fun withTempDirectory(block: (String) -> Unit) {
    val dir = memScoped { checkNotNull(mkdtemp("/tmp/compose-raven-images-XXXXXX".cstr.getPointer(this))) { "no temporary directory" }.toKString() }
    try {
        block(dir)
    } finally {
        rmdir(dir)
    }
}

private fun writeFile(path: String, bytes: ByteArray) {
    val file = fopen(path, "wb")!!
    fwrite(bytes.refTo(0), 1u, bytes.size.toULong(), file)
    fclose(file)
}
