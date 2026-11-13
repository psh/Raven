package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import raven.cinterop.RStretch
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_ROLE_IMAGE
import raven.cinterop.R_ROLE_NONE
import raven.cinterop.R_STRETCH_COVER
import raven.cinterop.R_STRETCH_FILL
import raven.cinterop.R_STRETCH_FIT
import raven.cinterop.R_STRETCH_NONE
import raven.cinterop.Rid
import raven.cinterop.Rtexture_wait
import raven.cinterop.Rwidget_get_accessible_role
import raven.compose.foundation.layout.height
import raven.compose.foundation.layout.width
import raven.compose.ui.Modifier
import raven.compose.ui.clip
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.ColorFilter
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.graphics.painter.BitmapPainter
import raven.compose.ui.graphics.painter.Painter
import raven.compose.ui.graphics.painter.TextureRegion
import raven.compose.ui.layout.ContentScale
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.res.painterResource
import raven.compose.ui.res.registerResource
import raven.compose.ui.testTag
import raven.compose.ui.unit.IntOffset
import raven.compose.ui.unit.IntSize
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImageTest {
    @Test
    fun anImageIsAsLargeAsItsBitmapAndFollowsItsUpdates() = runRavenTest {
        var bitmap: ImageBitmap? = null
        setContent {
            val image = remember { ImageBitmap(6, 4, pixels(6, 4)) }
            bitmap = image
            Image(image, contentDescription = null, Modifier.testTag("image"))
        }
        val widget = onNodeWithTag("image").fetchWidget()
        assertEquals(6f to 4f, widgetRect(widget).let { it.width to it.height })
        // Raven doesn't redraw for a texture update: the bitmap lays its widgets out again.
        assertNotNull(bitmap).update(10, 12, pixels(10, 12))
        frame()
        assertEquals(10f to 12f, widgetRect(widget).let { it.width to it.height })
    }

    @Test
    fun aPainterShowsItsPartOfTheBitmap() = withImageCalls { calls ->
        runRavenTest {
            var part by mutableStateOf(true)
            setContent {
                val atlas = remember { ImageBitmap(8, 8, pixels(8, 8)) }
                val painter = if (part) BitmapPainter(atlas, IntOffset(0, 4), IntSize(4, 4)) else BitmapPainter(atlas)
                Image(painter, contentDescription = null, Modifier.testTag("image"))
            }
            val widget = onNodeWithTag("image").fetchWidget()
            assertEquals(TextureRegion(0f, 0.5f, 0.5f, 1f), calls.regions.single())
            assertEquals(4f to 4f, widgetRect(widget).let { it.width to it.height })
            part = false
            waitForIdle()
            assertEquals(TextureRegion.Whole, calls.regions.last())
            assertEquals(8f to 8f, widgetRect(widget).let { it.width to it.height })
        }
    }

    @Test
    fun contentScaleIsTheStretch() = withImageCalls { calls ->
        runRavenTest {
            var scale by mutableStateOf(ContentScale.Fit)
            setContent {
                val bitmap = remember { ImageBitmap(2, 2, pixels(2, 2)) }
                Image(bitmap, contentDescription = null, Modifier.width(40.dp).height(20.dp), contentScale = scale)
            }
            for (next in listOf(ContentScale.Crop, ContentScale.FillBounds, ContentScale.None)) {
                scale = next
                waitForIdle()
            }
            assertEquals(listOf(R_STRETCH_FIT, R_STRETCH_COVER, R_STRETCH_FILL, R_STRETCH_NONE), calls.stretches.toList())
        }
    }

    @Test
    fun aColourFilterTintsTheImageWithAColourOrTheTextColour() = withImageCalls { calls ->
        runRavenTest {
            var filter by mutableStateOf<ColorFilter?>(ColorFilter.tint(Color.Red))
            setContent {
                val bitmap = remember { ImageBitmap(2, 2, pixels(2, 2)) }
                Image(bitmap, contentDescription = null, colorFilter = filter)
            }
            filter = ColorFilter.textColor()
            waitForIdle()
            filter = null
            waitForIdle()
            assertEquals(listOf(ColorFilter.tint(Color.Red), ColorFilter.textColor(), null), calls.tints.toList())
        }
    }

    @Test
    fun aRoundedClipRoundsTheImagesCorners() = withImageCalls { calls ->
        val warnings = mutableListOf<String>()
        val sink = layoutWarningSink
        layoutWarningSink = { warnings += it }
        try {
            runRavenTest {
                var shape by mutableStateOf(RoundedCornerShape(6.dp))
                setContent {
                    val bitmap = remember { ImageBitmap(2, 2, pixels(2, 2)) }
                    Image(bitmap, contentDescription = null, Modifier.clip(shape))
                }
                shape = RoundedCornerShape(4.dp, 8.dp, 8.dp, 8.dp)
                waitForIdle()
                assertEquals(listOf(6f, 4f), calls.radii.toList())
                assertTrue(warnings.single().startsWith("Image: Raven rounds every corner alike"), warnings.toString())
            }
        } finally {
            layoutWarningSink = sink
        }
    }

    @Test
    fun aDescribedImageIsAnImageAndAnUndescribedOneDecoration() = runRavenTest {
        setContent {
            val bitmap = remember { ImageBitmap(2, 2, pixels(2, 2)) }
            Image(bitmap, contentDescription = "Album cover")
            Image(bitmap, contentDescription = null, Modifier.testTag("decoration"))
        }
        assertEquals(R_ROLE_IMAGE, Rwidget_get_accessible_role(onNodeWithContentDescription("Album cover").fetchWidget()))
        assertEquals(R_ROLE_NONE, Rwidget_get_accessible_role(onNodeWithTag("decoration").fetchWidget()))
    }

    @Test
    fun aResourceImageShowsOnceDecoded() = runRavenTest {
        registerResource("lane-c/cover.tga", tgaFile(5, 7, pixels(5, 7)))
        var painter: Painter? = null
        setContent {
            painter = painterResource("lane-c/cover.tga")
            Image(painter!!, contentDescription = null, Modifier.testTag("cover"))
        }
        Rtexture_wait(assertNotNull(painter).texture)
        val widget = onNodeWithTag("cover").fetchWidget()
        // Raven lays an image out again once its texture is decoded.
        repeat(3) { frame() }
        assertEquals(5f to 7f, widgetRect(widget).let { it.width to it.height })
    }

    @Test
    fun aMenuItemShowsItsIcon() {
        val icons = mutableListOf<Pair<Boolean, TextureRegion>>()
        val previous = menuCalls
        menuCalls = object : MenuCalls() {
            override fun setIcon(item: Rid, texture: Rid, region: TextureRegion) {
                icons += (texture != R_INVALID_ID) to region
                super.setIcon(item, texture, region)
            }
        }
        try {
            runRavenTest {
                var withIcon by mutableStateOf(true)
                setContent {
                    val atlas = remember { ImageBitmap(4, 2, pixels(4, 2)) }
                    MenuBar {
                        Menu("File") {
                            Item("Open", onClick = {}, icon = if (withIcon) BitmapPainter(atlas, IntOffset(2, 0), IntSize(2, 2)) else null)
                        }
                    }
                }
                withIcon = false
                waitForIdle()
                assertEquals(listOf(true to TextureRegion(0.5f, 0f, 1f, 1f), false to TextureRegion.Whole), icons.toList())
            }
        } finally {
            menuCalls = previous
        }
    }
}

/** What images ask of Raven, which has no getters for it. */
private class RecordingImageCalls : ImageCalls() {
    val regions = mutableListOf<TextureRegion>()
    val stretches = mutableListOf<RStretch>()
    val tints = mutableListOf<ColorFilter?>()
    val radii = mutableListOf<Float>()

    override fun setTexture(image: Rid, texture: Rid, region: TextureRegion) {
        regions += region
        super.setTexture(image, texture, region)
    }

    override fun setStretch(image: Rid, stretch: RStretch) {
        stretches += stretch
        super.setStretch(image, stretch)
    }

    override fun setTint(image: Rid, filter: ColorFilter?) {
        tints += filter
        super.setTint(image, filter)
    }

    override fun setCornerRadius(image: Rid, radius: Float) {
        // The first, square, is every image's.
        if (radius != 0f) radii += radius
        super.setCornerRadius(image, radius)
    }
}

private fun withImageCalls(block: (RecordingImageCalls) -> Unit) {
    val calls = RecordingImageCalls()
    val previous = imageCalls
    imageCalls = calls
    try {
        block(calls)
    } finally {
        imageCalls = previous
    }
}

/** Distinct straight RGBA pixels. */
private fun pixels(width: Int, height: Int): ByteArray = ByteArray(width * height * 4) { (it * 7 + 1).toByte() }

/** An uncompressed 32-bit TGA file of [rgba], top row first, which Raven decodes. */
internal fun tgaFile(width: Int, height: Int, rgba: ByteArray): ByteArray {
    val header = byteArrayOf(
        0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        (width and 0xFF).toByte(), (width shr 8).toByte(), (height and 0xFF).toByte(), (height shr 8).toByte(),
        32, 0x28,
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
