@file:OptIn(InternalComposeRavenApi::class)

package raven.sample.themes

import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.mkdtemp
import platform.posix.remove
import platform.posix.rmdir
import platform.posix.usleep
import raven.cinterop.RColorRole
import raven.cinterop.R_COLOR_ACCENT
import raven.cinterop.R_COLOR_DANGER
import raven.cinterop.R_COLOR_TEXT
import raven.cinterop.R_LOOK_RAVEN_DARK
import raven.cinterop.R_LOOK_WINDOWS_LIGHT
import raven.cinterop.Rid
import raven.cinterop.Rlook_get
import raven.cinterop.Rtheme_get_color
import raven.cinterop.Rtheme_get_name
import raven.cinterop.Rwidget_get_color
import raven.cinterop.Rwidget_get_theme
import raven.compose.foundation.RavenTestScope
import raven.compose.foundation.runRavenTest
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.ravenColor
import raven.compose.ui.theme.Look
import raven.compose.ui.theme.RavenTheme
import raven.native.toRavenString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ThemesTest {
    private val directory = memScoped {
        checkNotNull(mkdtemp("/tmp/compose-raven-themes-XXXXXX".cstr.getPointer(this))) { "no temporary directory" }.toKString()
    }
    private val savePath = "$directory/my-theme.xml"
    private val notesPath = "$directory/notes.md"

    @AfterTest
    fun removeFiles() {
        remove(savePath)
        remove(notesPath)
        rmdir(directory)
    }

    private fun RavenTestScope.startThemes(notes: String = "$EXAMPLE_DIR/notes.md") {
        host.start { ThemesApp(EXAMPLE_DIR, onCloseRequest = {}, notesPath = notes, savePath = savePath) }
        waitForIdle()
    }

    private fun color(widget: Rid, role: RColorRole): Color = ravenColor { r, g, b, a -> Rwidget_get_color(widget, role, r, g, b, a) }

    private fun themeColor(role: RColorRole): Color = ravenColor { r, g, b, a -> Rtheme_get_color(role, r, g, b, a) }

    private fun themeName(): String = Rtheme_get_name().toRavenString()

    /** What `RavenTheme.colors` gives the sample's widgets: the muted status line and the danger button. */
    private fun RavenTestScope.assertColorsFollowTheAppTheme() {
        val text = themeColor(R_COLOR_TEXT)
        assertEquals(Color(text.red, text.green, text.blue, 0.65f), color(onNodeWithTag("status").fetchWidget(), R_COLOR_TEXT))
        assertEquals(themeColor(R_COLOR_DANGER), color(onNodeWithTag("delete").fetchWidget(), R_COLOR_ACCENT))
    }

    @Test
    fun choosingALookOrAThemeChangesRavensAndTheColorsFollow() = runRavenTest {
        startThemes()
        // At start: Raven's dark look, with ocean.xml over it.
        assertEquals(R_LOOK_RAVEN_DARK, Rlook_get())
        assertEquals("Ocean", themeName())
        assertTrue(RavenTheme.themeNames.containsAll(listOf("Ocean", "Paper", "Tweaks")))
        onNodeWithTag("looks").assertSelectedIndex(0)
        onNodeWithTag("themes").assertSelectedIndex(RavenTheme.themeNames.indexOf("Ocean"))
        assertEquals(Color(0xFFDCE7F5), themeColor(R_COLOR_TEXT))
        assertColorsFollowTheAppTheme()

        // A look: Ocean names its look, so it gives way to the look's own theme.
        onNodeWithTag("looks").performSelection(7)
        waitForIdle()
        assertEquals(R_LOOK_WINDOWS_LIGHT, Rlook_get())
        assertNotEquals("Ocean", themeName())
        onNodeWithTag("themes").assertSelectedIndex(-1)
        assertColorsFollowTheAppTheme()

        // A theme file.
        onNodeWithTag("themes").performSelection(RavenTheme.themeNames.indexOf("Paper"))
        waitForIdle()
        assertEquals("Paper", themeName())
        assertEquals(Color(0xFF2B2620), themeColor(R_COLOR_TEXT))
        assertColorsFollowTheAppTheme()

        // Tweaks names no look: it stays over the next one.
        onNodeWithTag("themes").performSelection(RavenTheme.themeNames.indexOf("Tweaks"))
        waitForIdle()
        assertEquals("Tweaks", themeName())
        onNodeWithTag("looks").performSelection(0)
        waitForIdle()
        assertEquals(R_LOOK_RAVEN_DARK, Rlook_get())
        assertEquals("Tweaks", themeName())
        onNodeWithTag("themes").assertSelectedIndex(RavenTheme.themeNames.indexOf("Tweaks"))
        assertColorsFollowTheAppTheme()
    }

    @Test
    fun aSwatchSetsTheAccentWhichSurvivesALook() = runRavenTest {
        startThemes()
        val violet = Color(0xFF8B5CF6)
        onNodeWithTag("accent $violet").performClick()
        waitForIdle()
        assertEquals(violet, themeColor(R_COLOR_ACCENT))
        onNodeWithTag("looks").performSelection(lookChoices.indexOfFirst { it.look == Look.Gnome && !it.dark })
        waitForIdle()
        assertEquals(violet, themeColor(R_COLOR_ACCENT))
    }

    @Test
    fun saveWritesTheThemeInUse() = runRavenTest {
        startThemes()
        onNodeWithText("Save as my-theme.xml").performClick()
        waitForIdle()
        onNodeWithText("Written: my-theme.xml (in the working folder)").assertExists()
        val saved = checkNotNull(readTextFile(savePath)) { "nothing written at $savePath" }
        assertTrue("Name=\"Mine\"" in saved, saved)
    }

    @Test
    fun theCardsKeepTheirOwnThemes() = runRavenTest {
        startThemes()
        assertEquals("Paper", Rwidget_get_theme(onNodeWithTag("paper card").fetchWidget()).toRavenString())
        assertEquals("GlassDark", Rwidget_get_theme(onNodeWithTag("glass card").fetchWidget()).toRavenString())
        onNodeWithTag("themes").performSelection(RavenTheme.themeNames.indexOf("Tweaks"))
        waitForIdle()
        assertEquals("Paper", Rwidget_get_theme(onNodeWithTag("paper card").fetchWidget()).toRavenString())
    }

    @Test
    fun theNotesAreReadAgainWhenTheFileChanges() = runRavenTest {
        writeNotes("# Notes\n\nThe first version.")
        startThemes(notes = notesPath)
        onNodeWithText("first version", substring = true).assertExists()

        writeNotes("# Notes\n\nThe second version.")
        // The sample looks at the file every half second.
        repeat(20) {
            usleep(100_000u)
            frame()
        }
        waitForIdle()
        onNodeWithText("second version", substring = true).assertExists()
        onNodeWithText("first version", substring = true).assertDoesNotExist()
    }

    @Test
    fun theNotesOfTheCExampleAreShown() = runRavenTest {
        startThemes()
        onNodeWithText("Open the documentation", substring = true).assertExists()
    }

    @Test
    fun theDocsLinkGivesTheCExamplesStatus() {
        assertEquals("A link of the Markdown text fired the action \"themes.docs\".", linkStatus(DOCS_LINK))
        assertTrue(readTextFile("$EXAMPLE_DIR/notes.md")!!.contains("($DOCS_LINK)"))
    }

    @Test
    fun theLooksAreTheCExamples() {
        assertEquals(16, lookChoices.size)
        assertEquals(LookChoice("Raven dark", Look.Raven, true), lookChoices[0])
        assertEquals(LookChoice("Windows light", Look.Windows, false), lookChoices[7])
        assertEquals(LookChoice("macOS 26 glass light", Look.MacosGlass, false), lookChoices[15])
    }

    private fun writeNotes(text: String) {
        val file = checkNotNull(fopen(notesPath, "w")) { "can't write $notesPath" }
        try {
            fputs(text, file)
        } finally {
            fclose(file)
        }
    }
}
