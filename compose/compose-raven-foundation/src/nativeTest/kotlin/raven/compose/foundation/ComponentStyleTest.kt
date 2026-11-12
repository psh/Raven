package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.RColorRole
import raven.cinterop.RMetric
import raven.cinterop.R_COLOR_ACCENT
import raven.cinterop.R_COLOR_ACCENT_HOVERED
import raven.cinterop.R_COLOR_ACCENT_PRESSED
import raven.cinterop.R_COLOR_ACCENT_TEXT
import raven.cinterop.R_COLOR_CONTROL
import raven.cinterop.R_COLOR_CONTROL_BORDER
import raven.cinterop.R_COLOR_CONTROL_HOVERED
import raven.cinterop.R_COLOR_CONTROL_PRESSED
import raven.cinterop.R_COLOR_FOCUS
import raven.cinterop.R_COLOR_HANDLE
import raven.cinterop.R_COLOR_INPUT
import raven.cinterop.R_COLOR_SELECTION
import raven.cinterop.R_COLOR_SEPARATOR
import raven.cinterop.R_COLOR_SWITCH
import raven.cinterop.R_COLOR_TEXT
import raven.cinterop.R_COLOR_TEXT_DISABLED
import raven.cinterop.R_COLOR_TEXT_PLACEHOLDER
import raven.cinterop.R_COLOR_TRACK
import raven.cinterop.R_METRIC_CORNER_RADIUS
import raven.cinterop.R_METRIC_PADDING_X
import raven.cinterop.R_METRIC_PADDING_Y
import raven.cinterop.Rid
import raven.cinterop.Rtheme_get_color
import raven.cinterop.Rtheme_get_metric
import raven.cinterop.Rtheme_set_accent
import raven.cinterop.Rwidget_get_color
import raven.cinterop.Rwidget_get_metric
import raven.compose.foundation.layout.Column
import raven.compose.ui.Modifier
import raven.compose.ui.PaddingValues
import raven.compose.ui.Window
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.RectangleShape
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.graphics.Shape
import raven.compose.ui.graphics.ravenColor
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.testTag
import raven.compose.ui.theme.Look
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The components' colours, shapes and padding, read back from their Raven widgets. */
class ComponentStyleTest {
    private fun color(widget: Rid, role: RColorRole): Color = ravenColor { r, g, b, a -> Rwidget_get_color(widget, role, r, g, b, a) }

    private fun themeColor(role: RColorRole): Color = ravenColor { r, g, b, a -> Rtheme_get_color(role, r, g, b, a) }

    /** What [block] logs as parameter warnings. */
    private fun collectWarnings(block: () -> Unit): List<String> {
        val warnings = mutableListOf<String>()
        val sink = layoutWarningSink
        layoutWarningSink = { warnings += it }
        try {
            block()
        } finally {
            layoutWarningSink = sink
        }
        return warnings
    }

    /** Each tagged composable, with the roles its colours set, given the colour or Unspecified. */
    private class Styled(val tag: String, val roles: List<RColorRole>, val content: @Composable (Color) -> Unit)

    private val styled = listOf(
        Styled("button", listOf(R_COLOR_ACCENT, R_COLOR_ACCENT_TEXT, R_COLOR_TEXT_DISABLED)) { c ->
            Button({}, Modifier.testTag("button"), colors = ButtonDefaults.buttonColors(c, c, c)) { }
        },
        Styled("outlined", listOf(R_COLOR_CONTROL, R_COLOR_TEXT, R_COLOR_TEXT_DISABLED)) { c ->
            OutlinedButton({}, Modifier.testTag("outlined"), colors = ButtonDefaults.buttonColors(c, c, c)) { }
        },
        Styled("text button", listOf(R_COLOR_CONTROL, R_COLOR_TEXT, R_COLOR_TEXT_DISABLED)) { c ->
            TextButton({}, Modifier.testTag("text button"), colors = ButtonDefaults.buttonColors(c, c, c)) { }
        },
        Styled("checkbox", listOf(R_COLOR_ACCENT, R_COLOR_CONTROL_BORDER, R_COLOR_ACCENT_TEXT)) { c ->
            Checkbox(true, {}, Modifier.testTag("checkbox"), colors = CheckboxDefaults.colors(c, c, c))
        },
        Styled("radio", listOf(R_COLOR_ACCENT, R_COLOR_CONTROL_BORDER)) { c ->
            RadioButton(true, {}, Modifier.testTag("radio"), colors = RadioButtonDefaults.colors(c, c))
        },
        Styled("switch", listOf(R_COLOR_SWITCH, R_COLOR_TRACK, R_COLOR_HANDLE)) { c ->
            Switch(true, {}, Modifier.testTag("switch"), colors = SwitchDefaults.colors(c, c, c))
        },
        Styled("slider", listOf(R_COLOR_HANDLE, R_COLOR_ACCENT, R_COLOR_TRACK)) { c ->
            Slider(0.5f, {}, Modifier.testTag("slider"), colors = SliderDefaults.colors(c, c, c))
        },
        Styled("field", listOf(R_COLOR_INPUT, R_COLOR_TEXT, R_COLOR_TEXT_PLACEHOLDER, R_COLOR_FOCUS, R_COLOR_SELECTION)) { c ->
            TextField("", {}, Modifier.testTag("field"), colors = TextFieldDefaults.colors(c, c, c, c, c))
        },
        Styled("progress", listOf(R_COLOR_ACCENT, R_COLOR_TRACK)) { c ->
            LinearProgressIndicator(0.5f, Modifier.testTag("progress"), color = c, trackColor = c)
        },
        Styled("horizontal", listOf(R_COLOR_SEPARATOR)) { c -> HorizontalDivider(Modifier.testTag("horizontal"), color = c) },
        Styled("vertical", listOf(R_COLOR_SEPARATOR)) { c -> VerticalDivider(Modifier.testTag("vertical"), color = c) },
        Styled("surface", listOf(R_COLOR_TEXT)) { c -> Surface(Modifier.testTag("surface"), contentColor = c) { } },
    )

    @Test
    fun aColourSetAndThenUnsetReturnsToTheThemes() = runRavenTest {
        val pink = Color(0xFFE040A0)
        var color by mutableStateOf(Color.Unspecified)
        setContent { Column { styled.forEach { it.content(color) } } }
        val themes = styled.associate { s ->
            val widget = onNodeWithTag(s.tag).fetchWidget()
            s.tag to s.roles.map { color(widget, it) }
        }
        styled.forEach { s -> assertTrue(themes.getValue(s.tag).none { it == pink }, "${s.tag} starts with the theme's colours") }

        color = pink
        waitForIdle()
        styled.forEach { s ->
            val widget = onNodeWithTag(s.tag).fetchWidget()
            assertEquals(s.roles.map { pink }, s.roles.map { color(widget, it) }, "${s.tag} with its colours set")
        }

        color = Color.Unspecified
        waitForIdle()
        styled.forEach { s ->
            val widget = onNodeWithTag(s.tag).fetchWidget()
            assertEquals(themes.getValue(s.tag), s.roles.map { color(widget, it) }, "${s.tag} back to the theme's")
        }
    }

    /**
     * Raven passes a widget's text colour down while it paints, which its getter doesn't show: a
     * child Text keeps no colour of its own, so the Surface's is the one painted.
     */
    @Test
    fun aSurfacesContentColourIsItsTextColourAndItsChildrenKeepNoneOfTheirOwn() = runRavenTest {
        val pink = Color(0xFFE040A0)
        var contentColor by mutableStateOf(pink)
        setContent { Surface(Modifier.testTag("surface"), contentColor = contentColor) { Text("Inside") } }
        assertEquals(pink, color(onNodeWithTag("surface").fetchWidget(), R_COLOR_TEXT))
        assertEquals(themeColor(R_COLOR_TEXT), color(onNodeWithText("Inside").fetchWidget(), R_COLOR_TEXT))

        contentColor = Color.Unspecified
        waitForIdle()
        assertEquals(themeColor(R_COLOR_TEXT), color(onNodeWithTag("surface").fetchWidget(), R_COLOR_TEXT))
    }

    @Test
    fun anAccentOrControlColourSetsItsHoveredAndPressedShadesAsTheLookDerivesTheAccents() = runRavenTest {
        val blue = Color(0xFF3B82F6)
        var look by mutableStateOf(Look.Raven)
        var dark by mutableStateOf(true)
        host.start {
            RavenTheme(look = look, dark = dark) {
                Window(onCloseRequest = {}, title = "Test") {
                    Column {
                        Button({}, Modifier.testTag("button"), colors = ButtonDefaults.buttonColors(containerColor = blue)) { }
                        OutlinedButton({}, Modifier.testTag("outlined"), colors = ButtonDefaults.buttonColors(containerColor = blue)) { }
                    }
                }
            }
        }
        val looks = listOf(
            Look.Raven to true, Look.Raven to false, Look.Glass to false, Look.HighContrast to true,
            Look.Windows to false, Look.Windows to true, Look.Macos to false, Look.Macos to true,
            Look.MacosGlass to false, Look.Gnome to false, Look.Gnome to true, Look.Native to false,
        )
        for ((nextLook, nextDark) in looks) {
            look = nextLook
            dark = nextDark
            waitForIdle()
            val button = onNodeWithTag("button").fetchWidget()
            val outlined = onNodeWithTag("outlined").fetchWidget()
            val shown = listOf(
                color(button, R_COLOR_ACCENT_HOVERED), color(button, R_COLOR_ACCENT_PRESSED),
                color(outlined, R_COLOR_CONTROL_HOVERED), color(outlined, R_COLOR_CONTROL_PRESSED),
            )
            // Raven's own shades of the same accent, under the same look; the next look resets it.
            Rtheme_set_accent(blue.red, blue.green, blue.blue)
            val hovered = themeColor(R_COLOR_ACCENT_HOVERED)
            val pressed = themeColor(R_COLOR_ACCENT_PRESSED)
            assertEquals(listOf(hovered, pressed, hovered, pressed), shown, "$nextLook, dark $nextDark")
            assertEquals(blue, color(outlined, R_COLOR_CONTROL))
        }
        assertNotEquals(Shades.Raven.pressed(blue, true), Shades.Macos.pressed(blue, true), "the looks shade differently")
    }

    @Test
    fun aShapeAndContentPaddingSetTheMetricsAndReturnToTheThemes() = runRavenTest {
        var shape by mutableStateOf<Shape?>(null)
        var padding by mutableStateOf<PaddingValues?>(null)
        setContent {
            Column {
                Button({}, Modifier.testTag("button"), shape = shape, contentPadding = padding) { Text("Go") }
                TextField("", {}, Modifier.testTag("field"), shape = shape)
            }
        }
        fun metric(tag: String, metric: RMetric) = Rwidget_get_metric(onNodeWithTag(tag).fetchWidget(), metric)
        val radius = Rtheme_get_metric(R_METRIC_CORNER_RADIUS)
        assertEquals(radius, metric("button", R_METRIC_CORNER_RADIUS))
        assertEquals(Rtheme_get_metric(R_METRIC_PADDING_X), metric("button", R_METRIC_PADDING_X))

        val warnings = collectWarnings {
            shape = RoundedCornerShape(9.dp)
            padding = PaddingValues(14.dp, 3.dp)
            waitForIdle()
        }
        assertEquals(emptyList(), warnings, "uniform corners and symmetric padding")
        assertEquals(9f, metric("button", R_METRIC_CORNER_RADIUS))
        assertEquals(9f, metric("field", R_METRIC_CORNER_RADIUS))
        assertEquals(14f, metric("button", R_METRIC_PADDING_X))
        assertEquals(3f, metric("button", R_METRIC_PADDING_Y))

        shape = RectangleShape
        waitForIdle()
        assertEquals(0f, metric("button", R_METRIC_CORNER_RADIUS))

        shape = null
        padding = null
        waitForIdle()
        assertEquals(radius, metric("button", R_METRIC_CORNER_RADIUS))
        assertEquals(radius, metric("field", R_METRIC_CORNER_RADIUS))
        assertEquals(Rtheme_get_metric(R_METRIC_PADDING_Y), metric("button", R_METRIC_PADDING_Y))
    }

    @Test
    fun aNonUniformShapeAndNonSymmetricPaddingLogWarnings() = runRavenTest {
        var shape by mutableStateOf<Shape?>(null)
        var padding by mutableStateOf<PaddingValues?>(null)
        setContent {
            Column {
                Button({}, Modifier.testTag("button"), shape = shape, contentPadding = padding) { Text("Go") }
                TextField("", {}, Modifier.testTag("field"), shape = shape)
            }
        }
        val warnings = collectWarnings {
            shape = RoundedCornerShape(4.dp, 8.dp, 8.dp, 4.dp)
            padding = PaddingValues(start = 12.dp, top = 2.dp, end = 6.dp, bottom = 2.dp)
            waitForIdle()
        }
        assertEquals(
            listOf(
                "Button: Raven rounds every corner alike, so the shape's topStart 4.0.dp is used for all four",
                "Button: Raven pads opposite sides alike, so contentPadding uses start 12.0.dp and top 2.0.dp",
                "TextField: Raven rounds every corner alike, so the shape's topStart 4.0.dp is used for all four",
            ),
            warnings,
        )
        assertEquals(4f, Rwidget_get_metric(onNodeWithTag("button").fetchWidget(), R_METRIC_CORNER_RADIUS))
        assertEquals(4f, Rwidget_get_metric(onNodeWithTag("field").fetchWidget(), R_METRIC_CORNER_RADIUS))
        assertEquals(12f, Rwidget_get_metric(onNodeWithTag("button").fetchWidget(), R_METRIC_PADDING_X))
    }
}
