@file:OptIn(InternalComposeRavenApi::class)

package raven.sample.workspace

import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.mkdtemp
import platform.posix.remove
import platform.posix.rmdir
import raven.cinterop.R_TRUE
import raven.cinterop.Rcombo_box_get_selected
import raven.cinterop.Rdock_area_hide_panel
import raven.cinterop.Rdock_area_is_panel_shown
import raven.cinterop.Rfile_browser_select
import raven.cinterop.Rtext_editor_get_text
import raven.cinterop.Rwidget_focus
import raven.cinterop.Rwidget_get_parent
import raven.compose.foundation.DockAreaState
import raven.compose.foundation.RavenTestScope
import raven.compose.foundation.rememberDockAreaState
import raven.compose.foundation.runRavenTest
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.input.key.Key
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The workspace sample, headless. */
class WorkspaceTest {
    private val workspace = Workspace()
    private lateinit var dock: DockAreaState
    private val directory = memScoped { checkNotNull(mkdtemp("/tmp/compose-raven-workspace-XXXXXX".cstr.getPointer(this))).toKString() }

    private fun RavenTestScope.show() {
        host.start { WorkspaceApp(onCloseRequest = {}, workspace = workspace, dock = rememberDockAreaState().also { dock = it }, directory = directory) }
        waitForIdle()
    }

    private fun RavenTestScope.panelOf(tag: String) = Rwidget_get_parent(onNodeWithTag(tag).fetchWidget())

    @Test
    fun aNewNoteIsAPanelInFront() = runRavenTest {
        show()
        onNodeWithTag("note").performClick()
        waitForIdle()
        assertEquals(listOf("Note 1"), workspace.editors.map { it.title })
        val dockWidget = onNodeWithTag("dock").fetchWidget()
        assertEquals(R_TRUE, Rdock_area_is_panel_shown(dockWidget, panelOf("panel1")), "brought forward")
        assertEquals("New panel: Note 1", workspace.log.last())
        assertEquals("New panel", workspace.islandTitle)
        assertTrue(workspace.islandExpanded, "the island tells it")
        assertTrue("panel1" in dock.layout, dock.layout)
    }

    @Test
    fun aLayoutIsSavedAndRestored() = runRavenTest {
        show()
        onNodeWithTag("restore").performClick()
        waitForIdle()
        assertEquals("Nothing saved yet: Use \"Save layout\" first.", workspace.log.last())

        onNodeWithTag("save").performClick()
        waitForIdle()
        val saved = dock.layout
        assertEquals("Layout saved: $saved", workspace.log.last())

        // The user hides the log: the layout shown follows.
        Rdock_area_hide_panel(onNodeWithTag("dock").fetchWidget(), panelOf("log"))
        waitForIdle()
        assertNotEquals(saved, dock.layout)
        onNodeWithTag("layout").assertExists()

        onNodeWithTag("restore").performClick()
        waitForIdle()
        assertEquals(saved, dock.layout)
        assertEquals("Layout restored: The panels fly back to where they were.", workspace.log.last())
    }

    @Test
    fun aFileAcceptedOpensInANewPanel() = runRavenTest {
        val path = "$directory/notes.c"
        val file = fopen(path, "w")
        fputs("int x;\n", file)
        fclose(file)
        try {
            show()
            val browser = onNodeWithTag("files").fetchWidget()
            Rfile_browser_select(browser, "notes.c")
            Rwidget_focus(browser)
            pressKey(Key.Enter)
            waitForIdle()
            assertEquals(listOf("notes.c"), workspace.editors.map { it.title })
            assertEquals("C", workspace.editors.single().language)
            assertEquals("int x;\n", Rtext_editor_get_text(onNodeWithTag("panel1").fetchWidget()).toRavenString())
            assertEquals("Opened: notes.c", workspace.log.last())
        } finally {
            remove(path)
            rmdir(directory)
        }
    }

    @Test
    fun theThemeStartsTheMaterialOver() = runRavenTest {
        show()
        onNodeWithTag("material").performSelection(3)
        waitForIdle()
        assertEquals("Material of everything: Frosted glass", workspace.log.last())
        onNodeWithTag("theme").performSelection(0)
        waitForIdle()
        assertEquals("Theme: Dark", workspace.log.last())
        assertEquals(0, Rcombo_box_get_selected(onNodeWithTag("material").fetchWidget()), "as the theme again")
    }
}
