package raven.compose.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.mkdtemp
import platform.posix.remove
import platform.posix.rmdir
import raven.cinterop.R_COLOR_ACCENT
import raven.cinterop.R_COLOR_PANEL
import raven.cinterop.R_LOOK_GNOME_DARK
import raven.cinterop.R_LOOK_RAVEN_DARK
import raven.cinterop.R_LOOK_RAVEN_GLASS_DARK
import raven.cinterop.R_LOOK_RAVEN_GLASS_LIGHT
import raven.cinterop.R_LOOK_RAVEN_LIGHT
import raven.cinterop.R_LOOK_WINDOWS_LIGHT
import raven.cinterop.R_MATERIAL_FROSTED_GLASS
import raven.cinterop.R_MATERIAL_GLASS
import raven.cinterop.R_MATERIAL_SOLID
import raven.cinterop.R_MATERIAL_TINTED_GLASS
import raven.cinterop.R_WIDGET_BUTTON
import raven.cinterop.R_WIDGET_LABEL
import raven.cinterop.R_WIDGET_VBOX
import raven.cinterop.Rid
import raven.cinterop.Rlabel_set_text
import raven.cinterop.Rlook_get
import raven.cinterop.Rtheme_get_color
import raven.cinterop.Rtheme_get_material
import raven.cinterop.Rtheme_get_name
import raven.cinterop.Rwidget_get_material
import raven.cinterop.Rwidget_get_parent
import raven.cinterop.Rwidget_get_rect
import raven.cinterop.Rwidget_get_theme
import raven.cinterop.Rwindow_get_content
import raven.cinterop.Rwidget_get_window
import raven.compose.ui.Alignment
import raven.compose.ui.ChildKind
import raven.compose.ui.ContentKind
import raven.compose.ui.FillElement
import raven.compose.ui.LayoutAxis
import raven.compose.ui.Modifier
import raven.compose.ui.PanelLayout
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.SingleWindowContent
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.Window
import raven.compose.ui.bind
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.Material
import raven.compose.ui.graphics.ravenColor
import raven.compose.ui.runUntilIdle
import raven.compose.ui.takeRavenErrors
import raven.compose.ui.withHost
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RavenThemeTest {
    private val labels = mutableMapOf<String, Rid>()

    /** Records the widget under a name. */
    private val Name = Property<String> { widget, name -> labels[name] = widget }

    private val Text = Property<String> { widget, text -> Rlabel_set_text(widget, text) }

    private val TestLabel = WidgetDescriptor("TestLabel", R_WIDGET_LABEL)
    private val TestColumn = WidgetDescriptor("TestColumn", R_WIDGET_VBOX, ChildKind.Many)
    private val TestButton = WidgetDescriptor("TestButton", R_WIDGET_BUTTON, ChildKind.Single, ContentKind.Box)

    /** A label showing its [name]. */
    @Composable
    private fun Label(name: String, modifier: Modifier = Modifier) {
        RavenWidget(TestLabel, modifier, {
            bind(name, Name)
            bind(name, Text)
        })
    }

    @Composable
    private fun Column(name: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
        RavenWidget(TestColumn, modifier, {
            bind(PanelLayout(LayoutAxis.Vertical, Alignment.CenterHorizontally))
            bind(name, Name)
        }, content)
    }

    @Composable
    private fun Button(name: String, content: @Composable () -> Unit) {
        RavenWidget(TestButton, Modifier, { bind(name, Name) }, content)
    }

    private fun theme(name: String): String = Rwidget_get_theme(labels.getValue(name)).toRavenString()

    @Test
    fun anApplicationLevelLookAndDarkBecomeRavensLook() {
        var look by mutableStateOf<Look?>(Look.Windows)
        var dark by mutableStateOf<Boolean?>(false)
        withHost({
            RavenTheme(look = look, dark = dark) { Window(onCloseRequest = {}) { Label("label") } }
        }) {
            assertEquals(R_LOOK_WINDOWS_LIGHT, Rlook_get())

            look = Look.Glass
            runUntilIdle()
            assertEquals(R_LOOK_RAVEN_GLASS_LIGHT, Rlook_get())

            // Unset, the darkness Raven has is kept.
            dark = true
            runUntilIdle()
            assertEquals(R_LOOK_RAVEN_GLASS_DARK, Rlook_get())
            look = null
            dark = false
            runUntilIdle()
            assertEquals(R_LOOK_RAVEN_GLASS_LIGHT, Rlook_get())
        }
    }

    @Test
    fun anApplicationLevelThemeBecomesTheAppTheme() {
        var theme by mutableStateOf<String?>(null)
        withHost({
            remember { loadThemeFromString(NIGHT) }
            RavenTheme(theme = theme) { Window(onCloseRequest = {}) { Label("label") } }
        }) {
            assertNotEquals("Night", Rtheme_get_name().toRavenString())

            theme = "Night"
            runUntilIdle()
            assertEquals("Night", Rtheme_get_name().toRavenString())
            assertEquals("Night", Rwidget_get_theme(labels.getValue("label")).toRavenString())
        }
    }

    @Test
    fun theOutermostRavenThemeOfASingleWindowIsApplicationLevel() = withHost({
        Window(onCloseRequest = {}) {
            SingleWindowContent {
                RavenTheme(look = Look.Gnome) {
                    Label("label")
                    RavenTheme(theme = "Light") { Label("nested") }
                }
            }
        }
    }) {
        assertEquals(R_LOOK_GNOME_DARK, Rlook_get())
        // No node for the outer one; the nested one is a subtree setting.
        val label = labels.getValue("label")
        assertEquals(Rwindow_get_content(Rwidget_get_window(label)), Rwidget_get_parent(label))
        assertEquals("Light", Rwidget_get_theme(labels.getValue("nested")).toRavenString())
    }

    @Test
    fun aSubtreeThemeAffectsOnlyItsSubtree() {
        var theme by mutableStateOf<String?>("Night")
        withHost({
            remember { loadThemeFromString(NIGHT) }
            Window(onCloseRequest = {}) {
                Label("outside")
                RavenTheme(theme = theme) { Label("inside") }
            }
        }) {
            val appTheme = Rtheme_get_name().toRavenString()
            assertNotEquals("Night", appTheme)
            assertEquals("Night", Rwidget_get_theme(labels.getValue("inside")).toRavenString())
            assertEquals(appTheme, Rwidget_get_theme(labels.getValue("outside")).toRavenString())

            // Unset, the subtree follows the app theme again.
            theme = null
            runUntilIdle()
            assertEquals(appTheme, Rwidget_get_theme(labels.getValue("inside")).toRavenString())
        }
    }

    @Test
    fun aWindowLevelThemeEmitsNoNodeSoItsChildrenLayOutAsTheirParentLaysThemOut() = withHost({
        remember { loadThemeFromString(NIGHT) }
        Window(onCloseRequest = {}) {
            Column("column", Modifier then FillElement(horizontal = true, vertical = true)) {
                RavenTheme(theme = "Night") {
                    Label("first", Modifier then FillElement(horizontal = true, vertical = false))
                    Label("second")
                }
            }
        }
    }) {
        val column = labels.getValue("column")
        assertEquals(column, Rwidget_get_parent(labels.getValue("first")))
        assertEquals(column, Rwidget_get_parent(labels.getValue("second")))
        // One under the other, as the column places them, the first filling its width.
        val first = rect(labels.getValue("first"))
        val second = rect(labels.getValue("second"))
        assertTrue(second.y >= first.y + first.height, "the second label is below the first")
        assertEquals(rect(column).width, first.width)
        assertTrue(second.width < first.width, "the second label keeps its own width")
        assertEquals("Night", theme("first"))
        assertEquals("Night", theme("second"))
    }

    @Test
    fun everyWidgetInAWindowLevelThemeIsDrawnWithIt() = withHost({
        remember { loadThemeFromString(NIGHT) }
        Window(onCloseRequest = {}) {
            Column("outer") {
                Label("outside")
                RavenTheme(theme = "Night") {
                    Column("column") {
                        Button("button") { Label("in button") }
                        Label("deep")
                    }
                    Label("label")
                }
            }
        }
    }) {
        for (name in listOf("column", "button", "in button", "deep", "label")) assertEquals("Night", theme(name), name)
        // A content node has it from its widget, through Raven.
        assertEquals("Night", Rwidget_get_theme(Rwidget_get_parent(labels.getValue("in button"))).toRavenString())
        val appTheme = Rtheme_get_name().toRavenString()
        assertEquals(appTheme, theme("outer"))
        assertEquals(appTheme, theme("outside"))
    }

    @Test
    fun theInnermostWindowLevelThemeWinsAndBothFollowTheirChanges() {
        var outer by mutableStateOf<String?>("Night")
        var inner by mutableStateOf<String?>("Light")
        withHost({
            remember { loadThemeFromString(NIGHT) }
            Window(onCloseRequest = {}) {
                Column("root") {
                    RavenTheme(theme = outer) {
                        Label("outer")
                        RavenTheme(theme = inner) { Label("inner") }
                        Label("after")
                    }
                }
            }
        }) {
            val appTheme = Rtheme_get_name().toRavenString()
            assertEquals(listOf("Night", "Light", "Night"), listOf(theme("outer"), theme("inner"), theme("after")))

            outer = "Dark"
            runUntilIdle()
            assertEquals(listOf("Dark", "Light", "Dark"), listOf(theme("outer"), theme("inner"), theme("after")))

            // Unset, the inner one follows the outer one again.
            inner = null
            runUntilIdle()
            assertEquals("Dark", theme("inner"))
            inner = "Night"
            runUntilIdle()
            assertEquals("Night", theme("inner"))

            // With neither, everything follows the app theme.
            outer = null
            inner = null
            runUntilIdle()
            assertEquals(listOf(appTheme, appTheme, appTheme), listOf(theme("outer"), theme("inner"), theme("after")))
        }
    }

    @Test
    fun aNodeTakenOutOfAWindowLevelThemeFollowsItsParentAgain() {
        var themed by mutableStateOf(true)
        withHost({
            remember { loadThemeFromString(NIGHT) }
            Window(onCloseRequest = {}) {
                Column("root") {
                    // The same node, moved out of the theme.
                    val content = remember { movableContentOf { Label("label") } }
                    if (themed) RavenTheme(theme = "Night", content = content) else content()
                }
            }
        }) {
            assertEquals("Night", theme("label"))
            val label = labels.getValue("label")

            themed = false
            runUntilIdle()
            assertEquals(label, labels.getValue("label"), "the label was kept")
            assertEquals(Rtheme_get_name().toRavenString(), theme("label"))
        }
    }

    @Test
    fun aThemeNameRavenDoesntKnowIsReportedOnce() {
        var name by mutableStateOf("Nowhere")
        withHost({
            Window(onCloseRequest = {}) {
                Column("root") {
                    RavenTheme(theme = name) {
                        Label("first")
                        Label("second")
                    }
                }
            }
        }) {
            val appTheme = Rtheme_get_name().toRavenString()
            val errors = takeRavenErrors()
            assertEquals(1, errors.size, "$errors")
            assertTrue("no theme is called 'Nowhere'" in errors.single())
            assertEquals(appTheme, theme("first"))
            assertEquals(appTheme, theme("second"))

            // Once Raven has it, it is asked for again.
            name = "Light"
            runUntilIdle()
            loadThemeFromString(NOWHERE)
            name = "Nowhere"
            runUntilIdle()
            assertEquals(emptyList(), takeRavenErrors())
            assertEquals("Nowhere", theme("first"))
            assertEquals("Nowhere", theme("second"))
        }
    }

    @Test
    fun colorsFollowAnApplicationLevelChange() {
        var look by mutableStateOf(Look.Raven)
        var theme by mutableStateOf<String?>(null)
        var colors: RavenColors? = null
        withHost({
            remember { loadThemeFromString(NIGHT) }
            RavenTheme(look = look, theme = theme) {
                Window(onCloseRequest = {}) { colors = RavenTheme.colors }
            }
        }) {
            assertEquals(appColor(R_COLOR_PANEL), colors?.panel)
            val darkPanel = colors?.panel

            look = Look.HighContrast
            runUntilIdle()
            assertEquals(appColor(R_COLOR_PANEL), colors?.panel)
            assertNotEquals(darkPanel, colors?.panel)

            theme = "Night"
            runUntilIdle()
            assertEquals(Color(0xFF101018), colors?.panel)
            assertEquals(Color(0xFFFF9F0A), colors?.accent)
        }
    }

    @Test
    fun anAppWideSettingInAWindowLogsAnErrorAndDoesNothing() {
        val errors = mutableListOf<String>()
        val sink = themeErrorSink
        themeErrorSink = { errors += it }
        try {
            withHost({
                Window(onCloseRequest = {}) {
                    RavenTheme(look = Look.Glass, accent = Color.Red) { Label("label") }
                }
            }) {
                assertEquals(R_LOOK_RAVEN_DARK, Rlook_get())
                assertNotEquals(Color.Red, appColor(R_COLOR_ACCENT))
            }
        } finally {
            themeErrorSink = sink
        }
        assertEquals(1, errors.size)
        assertTrue("only an application-level RavenTheme" in errors.single())
    }

    @Test
    fun theAccentSurvivesAChangeOfLook() {
        var look by mutableStateOf(Look.Raven)
        withHost({
            RavenTheme(look = look, dark = false, accent = GREEN) { Window(onCloseRequest = {}) { Label("label") } }
        }) {
            assertEquals(R_LOOK_RAVEN_LIGHT, Rlook_get())
            assertEquals(GREEN, appColor(R_COLOR_ACCENT))

            look = Look.Glass
            runUntilIdle()
            assertEquals(R_LOOK_RAVEN_GLASS_LIGHT, Rlook_get())
            assertEquals(GREEN, appColor(R_COLOR_ACCENT))
        }
    }

    @Test
    fun aSavedThemeLoadsBackWithItsAccent() {
        val directory = temporaryDirectory()
        val path = "$directory/saved.xml"
        var theme by mutableStateOf<String?>(null)
        var accent by mutableStateOf<Color?>(GREEN)
        var colors: RavenColors? = null
        try {
            withHost({
                RavenTheme(theme = theme, accent = accent) { Window(onCloseRequest = {}) { colors = RavenTheme.colors } }
            }) {
                assertTrue(saveTheme(path, "Saved"))

                // A look's theme resets the accent.
                accent = null
                theme = "Light"
                runUntilIdle()
                assertNotEquals(GREEN, colors?.accent)

                assertEquals("Saved", loadTheme(path))
                assertTrue("Saved" in RavenTheme.themeNames)
                theme = "Saved"
                runUntilIdle()
                assertEquals(GREEN, colors?.accent)
            }
        } finally {
            remove(path)
            rmdir(directory)
        }
    }

    @Test
    fun anApplicationLevelMaterialIsTheAppsAndSurvivesAChangeOfLook() {
        var look by mutableStateOf(Look.Glass)
        var material by mutableStateOf<Material?>(null)
        withHost({
            RavenTheme(look = look, material = material) { Window(onCloseRequest = {}) { Label("label") } }
        }) {
            // Unset, the look's is kept.
            assertEquals(R_MATERIAL_FROSTED_GLASS, Rtheme_get_material())

            for ((value, raven) in listOf(
                Material.Solid to R_MATERIAL_SOLID,
                Material.Glass to R_MATERIAL_GLASS,
                Material.TintedGlass to R_MATERIAL_TINTED_GLASS,
                Material.FrostedGlass to R_MATERIAL_FROSTED_GLASS,
            )) {
                material = value
                runUntilIdle()
                assertEquals(raven, Rtheme_get_material(), "$value")
                assertEquals(raven, Rwidget_get_material(labels.getValue("label")), "$value")
            }

            // A look change resets Raven's material, so it is applied again.
            material = Material.Glass
            look = Look.Raven
            runUntilIdle()
            assertEquals(R_MATERIAL_GLASS, Rtheme_get_material())

            // Default is the look's own.
            material = Material.Default
            runUntilIdle()
            assertEquals(R_MATERIAL_SOLID, Rtheme_get_material())
            look = Look.Glass
            runUntilIdle()
            assertEquals(R_MATERIAL_FROSTED_GLASS, Rtheme_get_material())
        }
    }

    @Test
    fun aMaterialInAWindowLogsAnErrorAndDoesNothing() {
        val errors = mutableListOf<String>()
        val sink = themeErrorSink
        themeErrorSink = { errors += it }
        try {
            withHost({
                Window(onCloseRequest = {}) {
                    RavenTheme(material = Material.Glass) { Label("label") }
                }
            }) {
                assertEquals(R_MATERIAL_SOLID, Rtheme_get_material())
            }
        } finally {
            themeErrorSink = sink
        }
        assertEquals(1, errors.size)
        assertTrue("material" in errors.single() && "only an application-level RavenTheme" in errors.single())
    }

    private class Rect(val y: Float, val width: Float, val height: Float)

    private fun rect(widget: Rid): Rect = memScoped {
        val x = alloc<FloatVar>()
        val y = alloc<FloatVar>()
        val width = alloc<FloatVar>()
        val height = alloc<FloatVar>()
        Rwidget_get_rect(widget, x.ptr, y.ptr, width.ptr, height.ptr)
        Rect(y.value, width.value, height.value)
    }

    private fun appColor(role: UInt): Color = ravenColor { r, g, b, a -> Rtheme_get_color(role, r, g, b, a) }
}

/** A new, empty directory for a test's files. */
private fun temporaryDirectory(): String = memScoped {
    checkNotNull(mkdtemp("/tmp/compose-raven-XXXXXX".cstr.getPointer(this))) { "no temporary directory" }.toKString()
}

private val GREEN = Color(0xFF00AA55)

private const val NOWHERE = """<Theme Name="Nowhere"><Colors Panel="#203040"/></Theme>"""

private const val NIGHT ="""<Theme Name="Night" Accent="#FF9F0A"><Colors Panel="#101018" Text="#FFF4E0"/></Theme>"""
