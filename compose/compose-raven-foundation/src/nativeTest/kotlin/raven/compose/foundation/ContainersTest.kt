package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import raven.cinterop.R_COLOR_BORDER
import raven.cinterop.R_COLOR_PANEL
import raven.cinterop.R_TRUE
import raven.cinterop.Rborder_get_corner_radius
import raven.cinterop.Rborder_get_outline
import raven.cinterop.Rborder_get_shadow
import raven.cinterop.Rexpander_get_title
import raven.cinterop.Rexpander_is_expanded
import raven.cinterop.Rid
import raven.cinterop.Rwidget_get_color
import raven.compose.foundation.layout.padding
import raven.compose.ui.Modifier
import raven.compose.ui.graphics.BorderStroke
import raven.compose.ui.graphics.CircleShape
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.RectangleShape
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.graphics.Shape
import raven.compose.ui.graphics.ravenColor
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals

/** Surface and Expander, read back from their Raven widgets. */
class ContainersTest {
    /** The border's four corner radii, top left first, clockwise. */
    private fun corners(widget: Rid): List<Float> = memScoped {
        val values = List(4) { alloc<FloatVar>() }
        Rborder_get_corner_radius(widget, values[0].ptr, values[1].ptr, values[2].ptr, values[3].ptr)
        values.map { it.value }
    }

    /** The border's outline width and colour. */
    private fun outline(widget: Rid): Pair<Float, Color> = memScoped {
        val width = alloc<FloatVar>()
        val color = ravenColor { r, g, b, a -> Rborder_get_outline(widget, width.ptr, r, g, b, a) }
        width.value to color
    }

    /** The border's shadow blur and offset. */
    private fun shadow(widget: Rid): List<Float> = memScoped {
        val values = List(3) { alloc<FloatVar>() }
        Rborder_get_shadow(widget, values[0].ptr, values[1].ptr, values[2].ptr)
        values.map { it.value }
    }

    private fun panelColor(widget: Rid): Color = ravenColor { r, g, b, a -> Rwidget_get_color(widget, R_COLOR_PANEL, r, g, b, a) }

    @Test
    fun aSurfaceFollowsItsParametersAndReturnsToTheThemesWhenUnset() = runRavenTest {
        var color by mutableStateOf(Color.Unspecified)
        var shape by mutableStateOf<Shape>(RectangleShape)
        var border by mutableStateOf<BorderStroke?>(null)
        var elevation by mutableStateOf(0.dp)
        setContent {
            Surface(Modifier.testTag("surface"), shape = shape, color = color, border = border, shadowElevation = elevation) {
                Text("Card")
            }
        }
        val surface = onNodeWithTag("surface").fetchWidget()
        val themes = panelColor(surface)
        assertEquals(listOf(0f, 0f, 0f, 0f), corners(surface))
        assertEquals(0f, outline(surface).first)
        assertEquals(listOf(0f, 0f, 0f), shadow(surface))

        color = Color(0xFF3B82F6)
        shape = RoundedCornerShape(1.dp, 2.dp, 3.dp, 4.dp)
        border = BorderStroke(2.dp, Color.Red)
        elevation = 8.dp
        waitForIdle()
        assertEquals(Color(0xFF3B82F6), panelColor(surface))
        assertEquals(listOf(1f, 2f, 3f, 4f), corners(surface))
        assertEquals(2f to Color.Red, outline(surface))
        assertEquals(listOf(8f, 0f, 4f), shadow(surface))

        color = Color.Unspecified
        shape = RectangleShape
        border = null
        elevation = 0.dp
        waitForIdle()
        assertEquals(themes, panelColor(surface))
        assertEquals(listOf(0f, 0f, 0f, 0f), corners(surface))
        assertEquals(0f, outline(surface).first)
        assertEquals(listOf(0f, 0f, 0f), shadow(surface))
    }

    @Test
    fun aSurfaceRoundsEveryCornerAlikeAndOutlinesInTheThemesBorderColour() = runRavenTest {
        setContent {
            Surface(Modifier.testTag("pill"), shape = CircleShape, border = BorderStroke(1.dp, Color.Unspecified)) { Text("Pill") }
        }
        val pill = onNodeWithTag("pill").fetchWidget()
        // Raven clamps the radius only as it draws it.
        assertEquals(List(4) { 100_000f }, corners(pill))
        val themeBorder = ravenColor { r, g, b, a -> Rwidget_get_color(pill, R_COLOR_BORDER, r, g, b, a) }
        assertEquals(1f to themeBorder, outline(pill))
    }

    @Test
    fun aSurfacesInnerPaddingIsItsContentsOwn() = runRavenTest {
        setContent {
            Surface(Modifier.testTag("surface")) {
                Text("Padded", Modifier.padding(8.dp).testTag("text"))
            }
        }
        val surface = widgetRect(onNodeWithTag("surface").fetchWidget())
        val text = widgetRect(onNodeWithTag("text").fetchWidget())
        assertEquals(surface.x + 8f, text.x)
        assertEquals(surface.y + 8f, text.y)
        assertEquals(text.width + 16f, surface.width)
        assertEquals(text.height + 16f, surface.height)
    }

    @Test
    fun anExpanderWhoseAppIgnoresTheToggleReverts() = runRavenTest {
        val changes = mutableListOf<Boolean>()
        setContent {
            Expander("Details", expanded = false, onExpandedChange = { changes += it }, Modifier.testTag("expander")) {
                Text("Hidden")
            }
        }
        val expander = onNodeWithTag("expander").fetchWidget()
        assertEquals("Details", Rexpander_get_title(expander).toRavenString())
        assertEquals(false, Rexpander_is_expanded(expander) == R_TRUE)
        onNodeWithTag("expander").performClick()
        waitForIdle()
        assertEquals(false, Rexpander_is_expanded(expander) == R_TRUE)
        assertEquals(listOf(true), changes)
    }

    @Test
    fun anExpanderWhoseAppTakesTheToggleOpensAndCloses() = runRavenTest {
        var expanded by mutableStateOf(false)
        setContent {
            Expander("Details", expanded = expanded, onExpandedChange = { expanded = it }, Modifier.testTag("expander")) {
                Text("Inside")
            }
        }
        val expander = onNodeWithTag("expander").fetchWidget()
        onNodeWithTag("expander").performClick()
        waitForIdle()
        assertEquals(true, expanded)
        assertEquals(true, Rexpander_is_expanded(expander) == R_TRUE)
        onNodeWithText("Inside").assertExists()

        expanded = false
        waitForIdle()
        assertEquals(false, Rexpander_is_expanded(expander) == R_TRUE)
    }
}
