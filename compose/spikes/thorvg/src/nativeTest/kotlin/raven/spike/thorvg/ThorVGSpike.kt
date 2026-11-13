package raven.spike.thorvg

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.free
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import raven.cinterop.R_INVALID_ID
import raven.cinterop.Rtexture_create_from_pixels
import raven.cinterop.Rtexture_destroy
import raven.cinterop.Rtexture_get_size
import raven.cinterop.Rtexture_update
import raven.compose.foundation.Text
import raven.compose.foundation.runRavenTest
import thorvg.cinterop.TVG_COLORSPACE_ABGR8888S
import thorvg.cinterop.TVG_ENGINE_OPTION_DEFAULT
import thorvg.cinterop.TVG_RESULT_SUCCESS
import thorvg.cinterop.tvg_animation_del
import thorvg.cinterop.tvg_animation_get_picture
import thorvg.cinterop.tvg_animation_get_total_frame
import thorvg.cinterop.tvg_animation_new
import thorvg.cinterop.tvg_animation_set_frame
import thorvg.cinterop.tvg_canvas_add
import thorvg.cinterop.tvg_canvas_destroy
import thorvg.cinterop.tvg_canvas_draw
import thorvg.cinterop.tvg_canvas_sync
import thorvg.cinterop.tvg_canvas_update
import thorvg.cinterop.tvg_engine_init
import thorvg.cinterop.tvg_engine_term
import thorvg.cinterop.tvg_picture_load_data
import thorvg.cinterop.tvg_picture_set_size
import thorvg.cinterop.tvg_swcanvas_create
import thorvg.cinterop.tvg_swcanvas_set_target
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val SIZE = 256

/** The spike's dot.json, inline: an orange dot sliding from x = 48 to 208 over 60 frames. */
private val DOT = """
{"v":"5.7.4","fr":30,"ip":0,"op":60,"w":256,"h":256,"nm":"dot","ddd":0,"assets":[],
 "layers":[{"ddd":0,"ind":1,"ty":4,"nm":"dot","sr":1,"ip":0,"op":60,"st":0,"bm":0,"ao":0,
  "ks":{"o":{"a":0,"k":100},"r":{"a":0,"k":0},"a":{"a":0,"k":[0,0,0]},"s":{"a":0,"k":[100,100,100]},
   "p":{"a":1,"k":[{"t":0,"s":[48,128,0],"o":{"x":[0.33],"y":[0]},"i":{"x":[0.67],"y":[1]}},{"t":59,"s":[208,128,0]}]}},
  "shapes":[{"ty":"gr","nm":"g","it":[
   {"ty":"el","nm":"e","d":1,"p":{"a":0,"k":[0,0]},"s":{"a":0,"k":[72,72]}},
   {"ty":"fl","nm":"f","c":{"a":0,"k":[1,0.45,0,1]},"o":{"a":0,"k":100},"r":1},
   {"ty":"tr","p":{"a":0,"k":[0,0]},"a":{"a":0,"k":[0,0]},"s":{"a":0,"k":[100,100]},"r":{"a":0,"k":0},"o":{"a":0,"k":100}}]}]}]}
""".trimIndent()

/** A Lottie player over ThorVG's CPU canvas, rendering into a straight-RGBA buffer it owns. */
private class LottiePlayer(json: String, val width: Int, val height: Int) : AutoCloseable {
    /** Native memory, since ThorVG keeps the pointer for as long as the canvas lives. */
    val pixels: CPointer<UIntVar> = nativeHeap.allocArray(width * height)
    private val canvas = tvg_swcanvas_create(TVG_ENGINE_OPTION_DEFAULT)!!
    private val animation = tvg_animation_new()!!
    val totalFrames: Float

    init {
        check(tvg_swcanvas_set_target(canvas, pixels, width.toUInt(), width.toUInt(), height.toUInt(), TVG_COLORSPACE_ABGR8888S) == TVG_RESULT_SUCCESS)
        val picture = tvg_animation_get_picture(animation)!!
        val size = json.encodeToByteArray().size.toUInt()
        check(tvg_picture_load_data(picture, json, size, "lottie+json", null, true) == TVG_RESULT_SUCCESS) { "not a Lottie" }
        tvg_picture_set_size(picture, width.toFloat(), height.toFloat())
        tvg_canvas_add(canvas, picture)
        totalFrames = memScoped {
            val total = alloc<FloatVar>()
            tvg_animation_get_total_frame(animation, total.ptr)
            total.value
        }
    }

    fun render(frame: Float) {
        tvg_animation_set_frame(animation, frame)
        tvg_canvas_update(canvas)
        tvg_canvas_draw(canvas, true)
        tvg_canvas_sync(canvas)
    }

    /** Red, green, blue and alpha of a pixel, as the bytes Raven's textures take. */
    fun rgba(x: Int, y: Int): List<Int> {
        val p = pixels[y * width + x]
        return listOf((p and 0xFFu).toInt(), (p shr 8 and 0xFFu).toInt(), (p shr 16 and 0xFFu).toInt(), (p shr 24).toInt())
    }

    override fun close() {
        tvg_animation_del(animation)
        tvg_canvas_destroy(canvas)
        nativeHeap.free(pixels)
    }
}

class ThorVGSpike {
    @BeforeTest
    fun start() {
        assertEquals(TVG_RESULT_SUCCESS, tvg_engine_init(0u))
    }

    @AfterTest
    fun stop() {
        tvg_engine_term()
    }

    @Test
    fun aLottieRendersFrameByFrameIntoAKotlinBuffer() {
        LottiePlayer(DOT, SIZE, SIZE).use { player ->
            assertEquals(60f, player.totalFrames)
            player.render(0f)
            assertEquals(listOf(255, 115, 0, 255), player.rgba(48, 128), "the dot at its start")
            assertEquals(0, player.rgba(208, 128)[3], "nothing where it ends yet")
            assertEquals(0, player.rgba(0, 0)[3], "a transparent background")
            player.render(59f)
            assertEquals(listOf(255, 115, 0, 255), player.rgba(208, 128), "the dot at its end")
            assertEquals(0, player.rgba(48, 128)[3], "nothing left at its start")
        }
    }

    /** The frame goes to a Raven texture as it is, inside compose-raven's host. */
    @Test
    fun aFrameBecomesARavenTexture() = runRavenTest {
        setContent { Text("ThorVG") }
        LottiePlayer(DOT, SIZE, SIZE).use { player ->
            player.render(30f)
            val texture = Rtexture_create_from_pixels(player.pixels.reinterpret(), SIZE, SIZE)
            check(texture != R_INVALID_ID)
            player.render(59f)
            Rtexture_update(texture, player.pixels.reinterpret(), SIZE, SIZE)
            memScoped {
                val width = alloc<IntVar>()
                val height = alloc<IntVar>()
                Rtexture_get_size(texture, width.ptr, height.ptr)
                assertEquals(SIZE to SIZE, width.value to height.value)
            }
            Rtexture_destroy(texture)
        }
    }
}
