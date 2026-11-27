package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.getenv
import raven.cinterop.R_INVALID_ID
import raven.cinterop.Rfile_read
import raven.cinterop.Rid
import raven.cinterop.Rimage_decode
import raven.cinterop.Rmemory_free
import raven.cinterop.Rpixels_free
import raven.cinterop.Rtexture_get_size
import raven.cinterop.Rtexture_is_valid
import raven.cinterop.Rtexture_wait
import raven.cinterop.Rwidget_get_rect
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_capture
import raven.cinterop.Rwindow_get_scale
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.size
import raven.compose.ui.Modifier
import raven.compose.ui.RavenHost
import raven.compose.ui.Window
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.ColorFilter
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.ui.graphics.painter.BitmapPainter
import raven.compose.ui.graphics.painter.TextureRegion
import raven.compose.ui.res.painterResource
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.test.widgetRect
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Icon, and Google's icons drawn as masks. */
class IconTest {
    private val previousCalls = imageCalls

    @AfterTest
    fun restore() {
        imageCalls = previousCalls
    }

    /** The image widgets' textures and tints, which Raven has no getters for. */
    private class RecordingCalls : ImageCalls() {
        val textures = mutableMapOf<Rid, Rid>()
        val tints = mutableListOf<ColorFilter?>()

        override fun setTexture(image: Rid, texture: Rid, region: TextureRegion) {
            textures[image] = texture
            super.setTexture(image, texture, region)
        }

        override fun setTint(image: Rid, filter: ColorFilter?) {
            tints += filter
            super.setTint(image, filter)
        }
    }

    private fun recording(): RecordingCalls = RecordingCalls().also { imageCalls = it }

    @Test
    fun everyGoogleFixtureHasCoverageWhereTheIconIsAndNoneOutside() = runRavenTest {
        setContent {}
        val fixtures = googleFixtures()
        assertEquals(11, fixtures.size, "Symbols in three styles, filled and not, search, two-tone, and three downloads: $fixtures")
        for (path in fixtures) {
            val alpha = alphaOf(path)
            val covered = alpha.count { it > 0 }.toFloat() / alpha.size
            assertTrue(covered in 0.05f..0.9f, "$path covers ${covered * 100}% of its box")
            assertEquals(0, alpha[0], "$path: nothing in the top-left corner")
        }
    }

    @Test
    fun theTwoToneLayerIsLighterAndTheGreyDownloadIsFullCoverage() = runRavenTest {
        setContent {}
        val twoTone = alphaOf("$GOOGLE/icons-twotone/star_24px.svg")
        assertTrue(twoTone.any { it in 70..85 }, "the .3 layer")
        assertTrue(twoTone.any { it == 255 }, "the solid outline")
        // fill="#e3e3e3": the colour goes, the coverage stays whole, where a multiplied tint gives 89%.
        assertTrue(alphaOf("$GOOGLE/downloaded/favorite.svg").any { it == 255 })
    }

    @Test
    fun anIconDrawsAResourcesMaskBesideItsColourTexture() = runRavenTest {
        val calls = recording()
        var colour = R_INVALID_ID
        setContent {
            val painter = painterResource("$GOOGLE/symbols-rounded/star_fill1_24px.svg")
            colour = painter.texture
            Row {
                Image(painter, null, Modifier.testTag("image"))
                Icon(painter, "Favourite", Modifier.testTag("icon"))
            }
        }
        val icon = onNodeWithTag("icon").fetchWidget()
        val mask = assertNotNull(calls.textures[icon])
        assertNotEquals(colour, mask, "the mask, not the colour texture")
        assertEquals(colour, calls.textures[onNodeWithTag("image").fetchWidget()])
        Rtexture_wait(mask)
        assertTrue(Rtexture_is_valid(mask) != 0)
        val size = memScoped {
            val w = alloc<IntVar>()
            val h = alloc<IntVar>()
            Rtexture_get_size(mask, w.ptr, h.ptr)
            w.value to h.value
        }
        assertEquals(48 to 48, size, "the SVG's 24 units at Raven's first scale of 2")
    }

    @Test
    fun theTintIsTheTextColourUnlessGiven() = runRavenTest {
        val calls = recording()
        var tint by mutableStateOf(Color.Unspecified)
        setContent { Icon(painterResource("$GOOGLE/symbols-outlined/search_24px.svg"), null, tint = tint) }
        assertEquals(Color.Unspecified, calls.tints.last()?.color, "the text colour where it sits")
        tint = Color.Red
        waitForIdle()
        assertEquals(Color.Red, calls.tints.last()?.color)
    }

    @Test
    fun anIconIs24DpUnlessTheModifierSays() = runRavenTest {
        setContent {
            Row {
                Icon(painterResource("$GOOGLE/downloaded/home.svg"), null, Modifier.testTag("small"))
                Icon(painterResource("$GOOGLE/downloaded/home.svg"), null, Modifier.size(48.dp).testTag("large"))
            }
        }
        assertEquals(24f to 24f, widgetRect(onNodeWithTag("small").fetchWidget()).let { it.width to it.height })
        assertEquals(48f to 48f, widgetRect(onNodeWithTag("large").fetchWidget()).let { it.width to it.height })
    }

    @Test
    fun anotherPainterIsMultiplied() = runRavenTest {
        val calls = recording()
        var bitmap: ImageBitmap? = null
        setContent {
            val image = ImageBitmap(2, 2, ByteArray(16) { -1 })
            bitmap = image
            Icon(BitmapPainter(image), null, Modifier.testTag("icon"))
        }
        assertEquals(assertNotNull(bitmap).texture, calls.textures[onNodeWithTag("icon").fetchWidget()])
    }

    @Test
    fun onAWindowEachFixtureShowsTheTintsColour() {
        if (getenv("RAVEN_GPU_TESTS")?.toKString() != "1") return
        val calls = recording()
        val filledStar = "$GOOGLE/symbols-rounded/star_fill1_24px.svg"
        val greyHeart = "$GOOGLE/downloaded/favorite.svg"
        val twoTone = "$GOOGLE/icons-twotone/star_24px.svg"
        val red = Color(1f, 0f, 0f)
        val host = RavenHost(headless = false, animationTimeStep = 1.0 / 60, followSystemAccessibility = false)
        var shots: Map<String, Triple<Int, Int, Int>> = emptyMap()
        var background = Triple(0, 0, 0)
        try {
            host.start {
                Window(onCloseRequest = {}, title = "Icons") {
                    Row {
                        for (path in listOf(filledStar, greyHeart, twoTone)) Icon(painterResource(path), null, Modifier.size(96.dp), tint = red)
                    }
                }
            }
            repeat(60) { if (!host.frame()) return@repeat }
            val images = calls.textures.keys.toList()
            assertEquals(3, images.size)
            val window = Rwidget_get_window(images[0])
            memScoped {
                val w = alloc<IntVar>()
                val h = alloc<IntVar>()
                val pixels = assertNotNull(Rwindow_capture(window, w.ptr, h.ptr), "a capture")
                val scale = Rwindow_get_scale(window)
                fun at(image: Rid, fx: Float, fy: Float): Triple<Int, Int, Int> {
                    val x = alloc<kotlinx.cinterop.FloatVar>()
                    val y = alloc<kotlinx.cinterop.FloatVar>()
                    val width = alloc<kotlinx.cinterop.FloatVar>()
                    val height = alloc<kotlinx.cinterop.FloatVar>()
                    Rwidget_get_rect(image, x.ptr, y.ptr, width.ptr, height.ptr)
                    val px = ((x.value + width.value * fx) * scale).toInt()
                    val py = ((y.value + height.value * fy) * scale).toInt()
                    val i = (py * w.value + px) * 4
                    return Triple(pixels[i].toInt() and 0xFF, pixels[i + 1].toInt() and 0xFF, pixels[i + 2].toInt() and 0xFF)
                }
                // The reddest pixel of the outlined heart: where its stroke covers a whole pixel.
                fun reddest(image: Rid): Triple<Int, Int, Int> =
                    (1 until 48).flatMap { i -> (1 until 48).map { j -> at(image, i / 48f, j / 48f) } }.maxBy { it.first - it.second }
                // The middle of the filled star and of the two-tone one; the background beside the first.
                shots = mapOf(
                    filledStar to at(images[0], 0.5f, 0.5f),
                    greyHeart to reddest(images[1]),
                    twoTone to at(images[2], 0.5f, 0.55f),
                )
                background = at(images[0], 0.02f, 0.02f)
                Rpixels_free(pixels)
            }
        } finally {
            host.close()
        }
        host.error?.let { throw it }
        assertEquals(Triple(255, 0, 0), shots[filledStar], "exactly the tint, not multiplied")
        assertEquals(Triple(255, 0, 0), shots[greyHeart], "the grey download: the tint, not 89% of it")
        val (r, g, b) = assertNotNull(shots[twoTone])
        val expected = Triple(
            (255 * 0.3f + background.first * 0.7f).toInt(),
            (background.second * 0.7f).toInt(),
            (background.third * 0.7f).toInt(),
        )
        assertTrue(
            abs(r - expected.first) <= 12 && abs(g - expected.second) <= 12 && abs(b - expected.third) <= 12,
            "the two-tone layer: the tint at .3 over $background, $expected, not ${Triple(r, g, b)}",
        )
    }

    private companion object {
        val GOOGLE = "$TEST_RESOURCES/icons/google"

        /** Every SVG under icons/google. */
        fun googleFixtures(): List<String> = listOf(
            "symbols-outlined/star_24px.svg", "symbols-outlined/star_fill1_24px.svg", "symbols-outlined/search_24px.svg",
            "symbols-rounded/star_24px.svg", "symbols-rounded/star_fill1_24px.svg",
            "symbols-sharp/star_24px.svg", "symbols-sharp/star_fill1_24px.svg",
            "icons-twotone/star_24px.svg",
            "downloaded/home.svg", "downloaded/star.svg", "downloaded/favorite.svg",
        ).map { "$GOOGLE/$it" }

        /** The alpha of each pixel of the image at [path], as Raven decodes it: what a mask keeps. */
        fun alphaOf(path: String): IntArray = memScoped {
            val size = alloc<ULongVar>()
            val data = assertNotNull(Rfile_read(path, size.ptr), "can't read $path")
            try {
                val width = alloc<IntVar>()
                val height = alloc<IntVar>()
                val pixels = assertNotNull(Rimage_decode(data, size.value, width.ptr, height.ptr), "Raven can't decode $path")
                try {
                    val rgba = pixels.readBytes(width.value * height.value * 4)
                    IntArray(width.value * height.value) { rgba[it * 4 + 3].toInt() and 0xFF }
                } finally {
                    Rpixels_free(pixels)
                }
            } finally {
                Rmemory_free(data)
            }
        }
    }
}
