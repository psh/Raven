package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import raven.cinterop.RShaderLook
import raven.cinterop.R_INVALID_ID
import raven.cinterop.Rid
import raven.cinterop.Reffect_get
import raven.cinterop.R_EFFECT_AURORA
import raven.cinterop.Rviewport_get_pixel_size
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.size
import raven.compose.ui.Modifier
import raven.compose.ui.geometry.Offset
import raven.compose.ui.geometry.Size
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.Effect
import raven.compose.ui.graphics.EffectStyle
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.ui.graphics.ShaderEffect
import raven.compose.ui.graphics.drawscope.Stroke
import raven.compose.ui.graphics.painter.BitmapPainter
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.testTag
import raven.compose.ui.unit.IntSize
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.value
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Canvas, Viewport, NativeViewport and shader effects. */
class DrawingTest {
    private val defaultSink = layoutWarningSink

    @AfterTest
    fun restore() {
        effectCalls = EffectCalls()
        layoutWarningSink = defaultSink
    }

    @Test
    fun aCanvasDrawsAgainWhenStateItReadChanges() = runRavenTest {
        var color by mutableStateOf(Color.Red)
        var draws = 0
        var compositions = 0
        setContent {
            compositions++
            Canvas(Modifier.size(100.dp)) {
                draws++
                drawRect(color)
            }
        }
        val drawn = draws
        val composed = compositions
        assertTrue(drawn > 0, "drawn once shown")
        frame()
        assertEquals(drawn, draws, "nothing changed, nothing drawn")

        color = Color.Blue
        waitForIdle()
        frame()
        assertTrue(draws > drawn, "drawn again")
        assertEquals(composed, compositions, "without recomposing")
    }

    @Test
    fun aCanvasDrawsWithEveryCall() = runRavenTest {
        val drawn = mutableListOf<Size>()
        setContent {
            val painter = remember { BitmapPainter(ImageBitmap(2, 2, ByteArray(16) { -1 })) }
            Canvas(Modifier.size(120.dp, 80.dp)) {
                drawn += size
                drawRect(Color.Red, style = Stroke(2f))
                drawRoundRect(Color.Green, Offset(4f, 4f), Size(20f, 10f), cornerRadius = 3f)
                drawCircle(Color.Blue)
                drawCircle(Color.Blue, 10f, Offset(20f, 20f), Stroke(1f))
                drawLine(Color.Black, Offset.Zero, Offset(100f, 60f), 2f)
                drawCubic(Color.Black, Offset.Zero, Offset(30f, 0f), Offset(60f, 80f), Offset(120f, 80f))
                drawTriangle(Color.Red, Offset.Zero, Offset(10f, 0f), Offset(5f, 8f))
                clipRect(right = 60f) { drawText("Hello", Offset(2f, 2f), Color.Black, fontSize = 14.sp) }
                drawImage(painter, Offset(1f, 1f), Size(8f, 8f))
                drawGlass(cornerRadius = 8f, tint = Color(0x330000FF))
                drawShader(ShaderEffect.builtin(Effect.Shimmer), style = EffectStyle(color = Color.Gray))
            }
        }
        assertEquals(Size(120f, 80f), drawn.last())
    }

    @Test
    fun aViewportReportsItsSizeInPixels() = runRavenTest {
        val sizes = mutableListOf<IntSize>()
        var big by mutableStateOf(false)
        setContent {
            val image = remember { ImageBitmap(4, 4, ByteArray(64)) }
            Viewport(image, Modifier.size(if (big) 200.dp else 100.dp, 50.dp).testTag("viewport"), onSizeChanged = { sizes += it })
        }
        val pixels = memScoped {
            val w = alloc<IntVar>()
            val h = alloc<IntVar>()
            Rviewport_get_pixel_size(onNodeWithTag("viewport").fetchWidget(), w.ptr, h.ptr)
            IntSize(w.value, h.value)
        }
        assertEquals(pixels, sizes.last())
        big = true
        waitForIdle()
        frame()
        assertEquals(pixels.width * 2, sizes.last().width)
    }

    @Test
    fun aHeadlessNativeViewportWarnsAndCreatesNothing() = runRavenTest {
        val warnings = mutableListOf<String>()
        layoutWarningSink = { warnings += it }
        var created = false
        setContent { NativeViewport(Modifier.size(50.dp), onCreated = { created = true }) }
        assertEquals(false, created)
        assertEquals(listOf("NativeViewport: Raven made no native child window here"), warnings)
    }

    @Test
    fun anEffectsStyleIsReappliedOnTheSameShader() = runRavenTest {
        val calls = mutableListOf<Triple<Rid, Float, Float>>()
        effectCalls = object : EffectCalls() {
            override fun setEffect(border: Rid, shader: Rid, look: CPointer<RShaderLook>?) {
                calls += Triple(shader, look?.pointed?.color?.get(0) ?: -1f, look?.pointed?.params?.get(0) ?: -1f)
                super.setEffect(border, shader, look)
            }
        }
        var color by mutableStateOf(Color.Red)
        var effect by mutableStateOf<ShaderEffect?>(ShaderEffect.builtin(Effect.Aurora))
        setContent {
            Column {
                Surface(Modifier.size(80.dp), effect = effect, effectStyle = EffectStyle(color = color, params = listOf(2f))) {}
            }
        }
        val aurora = Reffect_get(R_EFFECT_AURORA)
        assertEquals(listOf(Triple(aurora, 1f, 2f)), calls)

        color = Color.Blue
        waitForIdle()
        assertEquals(Triple(aurora, 0f, 2f), calls.last(), "the same shader, a new look")
        assertEquals(2, calls.size)

        effect = null
        waitForIdle()
        assertEquals(Triple(R_INVALID_ID, -1f, -1f), calls.last())
    }

    @Test
    fun shadersAreMadeOncePerHost() = runRavenTest {
        val shaders = mutableListOf<Rid>()
        setContent {
            shaders += raven.compose.ui.graphics.rememberShader(ShaderEffect.builtin(Effect.Mesh))
            shaders += raven.compose.ui.graphics.rememberShader(ShaderEffect.builtin(Effect.Mesh))
            shaders += raven.compose.ui.graphics.rememberShader(null)
        }
        assertNotEquals(R_INVALID_ID, shaders[0])
        assertEquals(shaders[0], shaders[1])
        assertEquals(R_INVALID_ID, shaders[2])
        val spirv = byteArrayOf(1, 2, 3)
        assertEquals(ShaderEffect.fromBytes(spirv), ShaderEffect.fromBytes(spirv), "one array, one shader")
        assertNotEquals(ShaderEffect.fromBytes(spirv), ShaderEffect.fromBytes(spirv.copyOf()))
        assertEquals(ShaderEffect.fromFile("a.spv", animated = true), ShaderEffect.fromFile("a.spv", animated = true))
    }
}
