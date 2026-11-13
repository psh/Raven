package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.RColorRole
import raven.cinterop.R_COLOR_ACCENT
import raven.cinterop.R_COLOR_ACCENT_HOVERED
import raven.cinterop.R_COLOR_ACCENT_PRESSED
import raven.cinterop.R_COLOR_BORDER
import raven.cinterop.R_COLOR_CONTROL_BORDER
import raven.cinterop.R_COLOR_CONTROL_HOVERED
import raven.cinterop.R_COLOR_CONTROL_PRESSED
import raven.cinterop.R_METRIC_CONTROL_HEIGHT
import raven.cinterop.R_METRIC_FONT_SIZE
import raven.cinterop.Rid
import raven.cinterop.Rtheme_get_color
import raven.cinterop.Rtheme_get_metric
import raven.cinterop.Rwidget_get_color
import raven.compose.foundation.layout.Column
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.ravenColor
import raven.compose.ui.testTag
import raven.compose.ui.theme.Look
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.theme.ThemeMetric
import raven.compose.ui.theme.Typography
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ThemingExtrasTest {
    private val red = Color(0xFFD03030)
    private val faint = Color(0x22FFFFFF)
    private val blue = Color(0xFF3060D0)

    private fun colorOf(widget: Rid, role: RColorRole) = ravenColor { r, g, b, a -> Rwidget_get_color(widget, role, r, g, b, a) }

    private fun themeColor(role: RColorRole) = ravenColor { r, g, b, a -> Rtheme_get_color(role, r, g, b, a) }

    private fun RavenTestScope.widget(tag: String) = onNodeWithTag(tag).fetchWidget()

    @Test
    fun aButtonsShadesAreDerivedOrGiven() = runRavenTest {
        setContent {
            Column {
                Button(onClick = {}, Modifier.testTag("derived"), colors = ButtonDefaults.buttonColors(containerColor = red)) { Text("A") }
                Button(
                    onClick = {}, Modifier.testTag("given"),
                    colors = ButtonDefaults.buttonColors(containerColor = red, hoveredContainerColor = blue),
                ) { Text("B") }
                // A transparent button keeps a faint hover of its own, and the theme's press.
                TextButton(
                    onClick = {}, Modifier.testTag("alone"),
                    colors = ButtonDefaults.buttonColors(hoveredContainerColor = faint),
                ) { Text("C") }
            }
        }
        val derived = widget("derived")
        assertEquals(Shades.Raven.hovered(red, true), colorOf(derived, R_COLOR_ACCENT_HOVERED))
        assertEquals(Shades.Raven.pressed(red, true), colorOf(derived, R_COLOR_ACCENT_PRESSED))

        val given = widget("given")
        assertEquals(blue, colorOf(given, R_COLOR_ACCENT_HOVERED), "given as it is")
        assertEquals(Shades.Raven.pressed(red, true), colorOf(given, R_COLOR_ACCENT_PRESSED), "each on its own")

        val alone = widget("alone")
        assertEquals(faint, colorOf(alone, R_COLOR_CONTROL_HOVERED))
        assertEquals(themeColor(R_COLOR_CONTROL_PRESSED), colorOf(alone, R_COLOR_CONTROL_PRESSED))
    }

    @Test
    fun aGivenShadeGoesBackToTheDerivedOneWhenDropped() = runRavenTest {
        var hovered by mutableStateOf(blue)
        setContent {
            Button(onClick = {}, Modifier.testTag("b"), colors = ButtonDefaults.buttonColors(containerColor = red, hoveredContainerColor = hovered)) {
                Text("A")
            }
        }
        assertEquals(blue, colorOf(widget("b"), R_COLOR_ACCENT_HOVERED))
        hovered = Color.Unspecified
        waitForIdle()
        assertEquals(Shades.Raven.hovered(red, true), colorOf(widget("b"), R_COLOR_ACCENT_HOVERED))
    }

    @Test
    fun checkboxRadioAndSliderTakeGivenShades() = runRavenTest {
        setContent {
            Column {
                Checkbox(
                    true, {}, Modifier.testTag("checkbox"),
                    colors = CheckboxDefaults.colors(checkedColor = red, hoveredCheckedColor = blue, pressedCheckedColor = faint),
                )
                RadioButton(true, {}, Modifier.testTag("radio"), colors = RadioButtonDefaults.colors(hoveredSelectedColor = blue))
                Slider(
                    0.5f, {}, Modifier.testTag("slider"),
                    colors = SliderDefaults.colors(activeTrackColor = red, pressedActiveTrackColor = blue),
                )
            }
        }
        assertEquals(blue, colorOf(widget("checkbox"), R_COLOR_ACCENT_HOVERED))
        assertEquals(faint, colorOf(widget("checkbox"), R_COLOR_ACCENT_PRESSED))
        assertEquals(blue, colorOf(widget("radio"), R_COLOR_ACCENT_HOVERED))
        assertEquals(themeColor(R_COLOR_ACCENT), colorOf(widget("radio"), R_COLOR_ACCENT))
        assertEquals(Shades.Raven.hovered(red, true), colorOf(widget("slider"), R_COLOR_ACCENT_HOVERED))
        assertEquals(blue, colorOf(widget("slider"), R_COLOR_ACCENT_PRESSED))
    }

    @Test
    fun aTextFieldCanHaveNoFrame() = runRavenTest {
        setContent {
            Column {
                TextField("", {}, Modifier.testTag("frameless"), colors = TextFieldDefaults.colors(unfocusedIndicatorColor = Color.Transparent))
                TextField("", {}, Modifier.testTag("framed"))
            }
        }
        assertEquals(Color.Transparent, colorOf(widget("frameless"), R_COLOR_CONTROL_BORDER))
        assertEquals(Color.Transparent, colorOf(widget("frameless"), R_COLOR_BORDER))
        assertEquals(themeColor(R_COLOR_CONTROL_BORDER), colorOf(widget("framed"), R_COLOR_CONTROL_BORDER))
    }

    @Test
    fun metricsSurviveALookChangeAndGoBackWhenLeftOut() = runRavenTest {
        var look by mutableStateOf(Look.Raven)
        var metrics by mutableStateOf(mapOf(ThemeMetric.ControlHeight to 40f, ThemeMetric.FontSize to 18f))
        var typography: Typography? = null
        host.start {
            RavenTheme(look = look, metrics = metrics) {
                Window(onCloseRequest = {}, title = "Test") { typography = RavenTheme.typography }
            }
        }
        waitForIdle()
        assertEquals(40f, Rtheme_get_metric(R_METRIC_CONTROL_HEIGHT))
        assertEquals(18f, Rtheme_get_metric(R_METRIC_FONT_SIZE))
        assertEquals(18f * 1.26f, typography!!.title.fontSize.value, "the font size moves the typography's base")

        look = Look.Gnome
        waitForIdle()
        assertEquals(40f, Rtheme_get_metric(R_METRIC_CONTROL_HEIGHT), "applied again after the look change")
        assertEquals(18f, Rtheme_get_metric(R_METRIC_FONT_SIZE))

        // Leaving the font size out gives the look's own back.
        metrics = mapOf(ThemeMetric.ControlHeight to 40f)
        waitForIdle()
        val gnomeSize = Rtheme_get_metric(R_METRIC_FONT_SIZE)
        assertNotEquals(18f, gnomeSize)
        assertEquals(gnomeSize * 1.26f, typography!!.title.fontSize.value)
        assertEquals(40f, Rtheme_get_metric(R_METRIC_CONTROL_HEIGHT))
    }

    @Test
    fun aHeaderBarCanHaveNoBackground() = runRavenTest {
        var background by mutableStateOf(false)
        val content: @Composable () -> Unit = { HeaderBar(Modifier.testTag("bar"), title = "Title", background = background) {} }
        host.start { Window(onCloseRequest = {}, title = "Test", customHeader = true, content = content) }
        waitForIdle()
        onNodeWithTag("bar").assertExists()
        // Raven has no getter for it: the call reaches Raven, both ways, without an error.
        background = true
        waitForIdle()
        background = false
        waitForIdle()
    }
}
