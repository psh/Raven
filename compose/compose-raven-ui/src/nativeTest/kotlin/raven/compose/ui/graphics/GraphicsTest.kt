package raven.compose.ui.graphics

import raven.cinterop.R_COLOR_TEXT
import raven.cinterop.R_WIDGET_LABEL
import raven.cinterop.Rlabel_set_color
import raven.cinterop.Rwidget_create
import raven.cinterop.Rwidget_destroy
import raven.cinterop.Rwidget_get_color
import raven.compose.ui.unit.dp
import raven.compose.ui.withHeadlessRaven
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GraphicsTest {
    private val colors = listOf(
        Color(0xFF3B82F6), Color(0x803B82F6), Color.Black, Color.White, Color.Gray, Color.Red, Color.Green,
        Color.Blue, Color.Yellow, Color.Transparent,
    )

    @Test
    fun aHexColourHasItsChannels() {
        val color = Color(0x803B82F6)
        assertEquals(0x80 / 255f, color.alpha)
        assertEquals(0x3B / 255f, color.red)
        assertEquals(0x82 / 255f, color.green)
        assertEquals(0xF6 / 255f, color.blue)
        assertEquals("Color(0x803B82F6)", color.toString())
    }

    @Test
    fun theChannelsRoundTrip() {
        for (color in colors) assertEquals(color, Color(color.red, color.green, color.blue, color.alpha))
    }

    @Test
    fun channelsRoundToEightBitsAndClampAsRavenDoes() {
        assertEquals(Color(0xFF800000), Color(0.5f, 0f, 0f))
        assertEquals(Color(0x00FF0000), Color(2f, -1f, 0f, -0.5f))
    }

    @Test
    fun unspecifiedIsNoColour() {
        assertTrue(Color.Unspecified.isUnspecified)
        assertFalse(Color.Unspecified.isSpecified)
        assertNotEquals(Color.Transparent, Color.Unspecified)
        assertTrue(colors.all { it.isSpecified })
        assertFailsWith<IllegalStateException> { Color.Unspecified.toRaven { _, _, _, _ -> } }
    }

    @Test
    fun aColourRoundTripsThroughRaven() = withHeadlessRaven {
        val label = Rwidget_create(R_WIDGET_LABEL)
        try {
            for (color in colors) {
                color.toRaven { r, g, b, a -> Rlabel_set_color(label, r, g, b, a) }
                assertEquals(color, ravenColor { r, g, b, a -> Rwidget_get_color(label, R_COLOR_TEXT, r, g, b, a) })
            }
        } finally {
            Rwidget_destroy(label)
        }
    }

    @Test
    fun aRoundedCornerShapeOfOneRadiusRoundsEveryCorner() {
        assertEquals(RoundedCornerShape(8.dp, 8.dp, 8.dp, 8.dp), RoundedCornerShape(8.dp))
        val circle = CircleShape as RoundedCornerShape
        assertTrue(circle.topStart > 10_000.dp, "larger than any widget")
        assertEquals(RoundedCornerShape(circle.topStart), circle)
    }

    @Test
    fun aBorderStrokeIsItsWidthAndColour() {
        assertEquals(BorderStroke(1.dp, Color.Red), BorderStroke(1.dp, Color(0xFFFF0000)))
    }
}
