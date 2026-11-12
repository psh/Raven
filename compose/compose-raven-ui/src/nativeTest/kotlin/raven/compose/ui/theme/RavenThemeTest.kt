package raven.compose.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
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
import raven.cinterop.Rid
import raven.cinterop.Rlook_get
import raven.cinterop.Rtheme_get_color
import raven.cinterop.Rtheme_get_material
import raven.cinterop.Rtheme_get_name
import raven.cinterop.Rwidget_get_material
import raven.cinterop.Rwidget_get_parent
import raven.cinterop.Rwidget_get_theme
import raven.cinterop.Rwindow_get_content
import raven.cinterop.Rwidget_get_window
import raven.compose.ui.RavenApplier
import raven.compose.ui.RavenNode
import raven.compose.ui.SingleWindowContent
import raven.compose.ui.Window
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.Material
import raven.compose.ui.graphics.ravenColor
import raven.compose.ui.node
import raven.compose.ui.runUntilIdle
import raven.compose.ui.withHost
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RavenThemeTest {
    private val labels = mutableMapOf<String, Rid>()

    @Composable
    private fun Label(name: String) {
        ComposeNode<RavenNode, RavenApplier>(factory = { node(name).also { labels[name] = it.widget } }, update = {})
    }

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

    private fun appColor(role: UInt): Color = ravenColor { r, g, b, a -> Rtheme_get_color(role, r, g, b, a) }
}

/** A new, empty directory for a test's files. */
private fun temporaryDirectory(): String = memScoped {
    checkNotNull(mkdtemp("/tmp/compose-raven-XXXXXX".cstr.getPointer(this))) { "no temporary directory" }.toKString()
}

private val GREEN = Color(0xFF00AA55)

private const val NIGHT = """<Theme Name="Night" Accent="#FF9F0A"><Colors Panel="#101018" Text="#FFF4E0"/></Theme>"""
