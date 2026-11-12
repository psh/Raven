package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.RBlurStyle
import raven.cinterop.R_BLUR_FROSTED
import raven.cinterop.R_BLUR_GLASS
import raven.cinterop.R_BLUR_NATIVE
import raven.cinterop.R_BLUR_PLAIN
import raven.cinterop.R_COLOR_PANEL
import raven.cinterop.R_MATERIAL_FROSTED_GLASS
import raven.cinterop.R_MATERIAL_GLASS
import raven.cinterop.R_MATERIAL_SOLID
import raven.cinterop.R_MATERIAL_TINTED_GLASS
import raven.cinterop.R_METRIC_BLUR_RADIUS
import raven.cinterop.R_WIDGET_BACKGROUND_BLUR
import raven.cinterop.R_WIDGET_OVERLAY
import raven.cinterop.Rbackground_blur_get_radius
import raven.cinterop.Rbackground_blur_get_style
import raven.cinterop.Rid
import raven.cinterop.Rtheme_get_material
import raven.cinterop.Rtheme_get_metric
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_color
import raven.cinterop.Rwidget_get_material
import raven.cinterop.Rwidget_get_type
import raven.compose.foundation.layout.padding
import raven.compose.ui.Modifier
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.Material
import raven.compose.ui.graphics.RectangleShape
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.graphics.Shape
import raven.compose.ui.graphics.ravenColor
import raven.compose.ui.material
import raven.compose.ui.testTag
import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Modifier.material and BackgroundBlur, read back from Raven, or recorded where Raven has no getter. */
class GlassTest {
    @Test
    fun aMaterialFollowsItsModifierAndReturnsToTheThemesWhenUnset() = runRavenTest {
        var material by mutableStateOf<Material?>(null)
        setContent {
            Text("Glass", material?.let { Modifier.material(it) } ?: Modifier)
        }
        val text = onNodeWithText("Glass").fetchWidget()
        val themes = Rtheme_get_material()
        assertEquals(themes, Rwidget_get_material(text))

        for ((value, raven) in listOf(
            Material.Glass to R_MATERIAL_GLASS,
            Material.FrostedGlass to R_MATERIAL_FROSTED_GLASS,
            Material.TintedGlass to R_MATERIAL_TINTED_GLASS,
            Material.Solid to R_MATERIAL_SOLID,
        )) {
            material = value
            waitForIdle()
            assertEquals(raven, Rwidget_get_material(text), "$value")
        }

        material = Material.Default
        waitForIdle()
        assertEquals(themes, Rwidget_get_material(text))

        material = Material.TintedGlass
        waitForIdle()
        material = null
        waitForIdle()
        assertEquals(themes, Rwidget_get_material(text))
    }

    @Test
    fun aSurfacesColourTintsItsGlass() = runRavenTest {
        val tint = Color(0x403B82F6)
        setContent {
            Surface(Modifier.material(Material.FrostedGlass).testTag("surface"), color = tint) {
                Text("Card")
            }
        }
        val surface = onNodeWithTag("surface").fetchWidget()
        assertEquals(R_MATERIAL_FROSTED_GLASS, Rwidget_get_material(surface))
        assertEquals(tint, ravenColor { r, g, b, a -> Rwidget_get_color(surface, R_COLOR_PANEL, r, g, b, a) })
        // The material is the outer widget's, not its content node's.
        assertEquals(Rtheme_get_material(), Rwidget_get_material(Rwidget_get_child(surface, 0)))
    }

    @Test
    fun aBackgroundBlurFollowsItsParametersAndReturnsToRavensWhenUnset() {
        val calls = mutableMapOf<String, Any?>()
        val target = backgroundBlurTarget
        backgroundBlurTarget = { widget -> RecordingBlurTarget(target(widget), calls) }
        try {
            runRavenTest {
                var style by mutableStateOf(BlurStyle.Frosted)
                var shape by mutableStateOf<Shape>(RectangleShape)
                var tint by mutableStateOf(Color.Unspecified)
                var radius by mutableStateOf(Dp.Unspecified)
                var saturation by mutableStateOf<Float?>(null)
                var grain by mutableStateOf<Float?>(null)
                var elevation by mutableStateOf(0.dp)
                setContent {
                    BackgroundBlur(
                        Modifier.testTag("blur"),
                        style = style,
                        shape = shape,
                        tint = tint,
                        radius = radius,
                        saturation = saturation,
                        grain = grain,
                        shadowElevation = elevation,
                    ) { Text("Over the blur") }
                }
                val blur = onNodeWithTag("blur").fetchWidget()
                val defaults = mapOf<String, Any?>(
                    "style" to R_BLUR_FROSTED,
                    "cornerRadius" to listOf(0f, 0f, 0f, 0f),
                    "tint" to null,
                    "radius" to -1f,
                    "saturation" to -1f,
                    "grain" to -1f,
                    "shadow" to 0f,
                )
                assertEquals(defaults, calls)
                assertEquals(R_BLUR_FROSTED, Rbackground_blur_get_style(blur))
                assertEquals(Rtheme_get_metric(R_METRIC_BLUR_RADIUS), Rbackground_blur_get_radius(blur))

                style = BlurStyle.Glass
                shape = RoundedCornerShape(1.dp, 2.dp, 3.dp, 4.dp)
                tint = Color(0x80FF0000)
                radius = 24.dp
                saturation = 1.5f
                grain = 0.3f
                elevation = 8.dp
                waitForIdle()
                assertEquals(
                    mapOf<String, Any?>(
                        "style" to R_BLUR_GLASS,
                        "cornerRadius" to listOf(1f, 2f, 3f, 4f),
                        "tint" to Color(0x80FF0000),
                        "radius" to 24f,
                        "saturation" to 1.5f,
                        "grain" to 0.3f,
                        "shadow" to 8f,
                    ),
                    calls,
                )
                assertEquals(R_BLUR_GLASS, Rbackground_blur_get_style(blur))
                assertEquals(24f, Rbackground_blur_get_radius(blur))

                style = BlurStyle.Frosted
                shape = RectangleShape
                tint = Color.Unspecified
                radius = Dp.Unspecified
                saturation = null
                grain = null
                elevation = 0.dp
                waitForIdle()
                assertEquals(defaults, calls)
                assertEquals(Rtheme_get_metric(R_METRIC_BLUR_RADIUS), Rbackground_blur_get_radius(blur))
            }
        } finally {
            backgroundBlurTarget = target
        }
    }

    @Test
    fun everyBlurStyleReachesRaven() = runRavenTest {
        var style by mutableStateOf(BlurStyle.Plain)
        setContent { BackgroundBlur(Modifier.testTag("blur"), style = style) { Text("Over the blur") } }
        val blur = onNodeWithTag("blur").fetchWidget()
        for ((value, raven) in listOf(
            BlurStyle.Plain to R_BLUR_PLAIN,
            BlurStyle.Frosted to R_BLUR_FROSTED,
            BlurStyle.Glass to R_BLUR_GLASS,
            BlurStyle.Native to R_BLUR_NATIVE,
        )) {
            style = value
            waitForIdle()
            assertEquals(raven, Rbackground_blur_get_style(blur), "$value")
        }
    }

    @Test
    fun aBackgroundBlursContentGoesInABoxPaddedByItsOwnModifier() = runRavenTest {
        setContent {
            BackgroundBlur(Modifier.testTag("blur")) {
                Text("First", Modifier.padding(8.dp).testTag("text"))
                Text("Second")
            }
        }
        val blur = onNodeWithTag("blur").fetchWidget()
        assertEquals(R_WIDGET_BACKGROUND_BLUR, Rwidget_get_type(blur))
        assertEquals(1, Rwidget_get_child_count(blur))
        val box: Rid = Rwidget_get_child(blur, 0)
        assertEquals(R_WIDGET_OVERLAY, Rwidget_get_type(box))
        assertEquals(2, Rwidget_get_child_count(box))
        val outer = widgetRect(blur)
        val text = widgetRect(onNodeWithTag("text").fetchWidget())
        assertEquals(outer.x + 8f, text.x)
        assertEquals(outer.y + 8f, text.y)
    }
}

/** Records the last value of each of [target]'s setters in [calls], by name, and passes it on. */
private class RecordingBlurTarget(private val target: BlurTarget, private val calls: MutableMap<String, Any?>) : BlurTarget {
    override fun style(style: RBlurStyle) {
        calls["style"] = style
        target.style(style)
    }

    override fun radius(radius: Float) {
        calls["radius"] = radius
        target.radius(radius)
    }

    override fun tint(tint: Color?) {
        calls["tint"] = tint
        target.tint(tint)
    }

    override fun saturation(saturation: Float) {
        calls["saturation"] = saturation
        target.saturation(saturation)
    }

    override fun grain(grain: Float) {
        calls["grain"] = grain
        target.grain(grain)
    }

    override fun cornerRadius(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float) {
        calls["cornerRadius"] = listOf(topLeft, topRight, bottomRight, bottomLeft)
        target.cornerRadius(topLeft, topRight, bottomRight, bottomLeft)
    }

    override fun shadow(blur: Float) {
        calls["shadow"] = blur
        target.shadow(blur)
    }
}
