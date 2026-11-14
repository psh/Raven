package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.mkdtemp
import platform.posix.remove
import platform.posix.rmdir
import raven.cinterop.R_FALSE
import raven.cinterop.R_MOUSE_MIDDLE
import raven.cinterop.R_TRUE
import raven.cinterop.Rdock_area_hide_panel
import raven.cinterop.Rdock_area_is_panel_open
import raven.cinterop.Rdock_area_is_panel_shown
import raven.cinterop.Rfile_browser_get_directory
import raven.cinterop.Rfile_browser_select
import raven.cinterop.Rid
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_parent
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.cinterop.Rwidget_focus
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.ui.Modifier
import raven.compose.ui.input.key.Key
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.testTag
import raven.native.toRavenString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** DockArea and FileBrowser. */
class DockingTest {
    private val warnings = mutableListOf<String>()
    private val defaultSink = layoutWarningSink

    @AfterTest
    fun restoreSink() {
        layoutWarningSink = defaultSink
    }

    private fun RavenTestScope.widget(tag: String) = onNodeWithTag(tag).fetchWidget()

    /** The panel a tagged content is in: its internal Box. */
    private fun RavenTestScope.panel(contentTag: String): Rid = Rwidget_get_parent(widget(contentTag))

    @Test
    fun panelsAreDockedAsInitialDockSays() = runRavenTest {
        lateinit var state: DockAreaState
        setContent {
            state = rememberDockAreaState()
            DockArea(state, Modifier.testTag("dock").fillMaxSize()) {
                Panel("editor", "Editor") { Box(Modifier.fillMaxSize().testTag("editor")) {} }
                Panel("files", "Files", Dock.Left(0.25f)) { Box(Modifier.fillMaxSize().testTag("files")) {} }
                Panel("log", "Output", Dock.Bottom(0.3f)) { Box(Modifier.fillMaxSize().testTag("log")) {} }
                Panel("problems", "Problems", Dock.Tab("log")) { Box(Modifier.fillMaxSize().testTag("problems")) {} }
            }
        }
        val editor = widgetRect(panel("editor"))
        val files = widgetRect(panel("files"))
        val log = widgetRect(panel("log"))
        assertTrue(files.x + files.width <= editor.x, "files on the left: $files, $editor")
        assertTrue(log.y >= editor.y + editor.height, "output below: $log, $editor")
        val area = widget("dock")
        assertEquals(1, listOf(panel("log"), panel("problems")).count { Rdock_area_is_panel_shown(area, it) == R_TRUE }, "behind the same tabs")
        for (name in listOf("editor", "files", "log", "problems")) assertTrue(name in state.layout, "$name in ${state.layout}")
    }

    @Test
    fun aPanelCanBeDockedBesideAnother() = runRavenTest {
        setContent {
            DockArea(modifier = Modifier.testTag("dock").fillMaxSize()) {
                Panel("editor", "Editor") { Box(Modifier.fillMaxSize().testTag("editor")) {} }
                Panel("files", "Files", Dock.Left(0.25f)) { Box(Modifier.fillMaxSize().testTag("files")) {} }
                Panel("log", "Log", Dock.Bottom(0.3f, of = "editor")) { Box(Modifier.fillMaxSize().testTag("log")) {} }
            }
        }
        val editor = widgetRect(panel("editor"))
        val files = widgetRect(panel("files"))
        val log = widgetRect(panel("log"))
        assertTrue(log.y >= editor.y + editor.height, "below the editor: $log, $editor")
        assertTrue(log.x >= files.x + files.width, "only under the editor, not under the files: $log, $files")
    }

    @Test
    fun closingIsControlledAndHides() = runRavenTest {
        var open by mutableStateOf(true)
        var take by mutableStateOf(false)
        var asked = 0
        lateinit var state: DockAreaState
        setContent {
            state = rememberDockAreaState()
            DockArea(state, Modifier.testTag("dock").fillMaxSize()) {
                Panel("editor", "Editor") { Box(Modifier.fillMaxSize().testTag("editor")) {} }
                Panel("log", "Output", Dock.Bottom(0.4f), open = open, onClose = { asked++; if (take) open = false }) {
                    Box(Modifier.fillMaxSize().testTag("log"))
                }
            }
        }
        val area = widget("dock")
        val log = panel("log")
        middleClickTab(log)
        waitForIdle()
        assertEquals(1, asked)
        assertEquals(R_TRUE, Rdock_area_is_panel_open(area, log), "refused, the panel stays")

        take = true
        val before = state.layout
        middleClickTab(log)
        waitForIdle()
        assertEquals(2, asked)
        assertEquals(R_FALSE, Rdock_area_is_panel_open(area, log), "hidden")
        assertNotEquals(before, state.layout)

        open = true
        waitForIdle()
        assertEquals(R_TRUE, Rdock_area_is_panel_open(area, log), "put back")
        assertEquals(R_TRUE, Rdock_area_is_panel_shown(area, log))
    }

    @Test
    fun aLayoutIsSavedAndLoaded() = runRavenTest {
        lateinit var state: DockAreaState
        setContent {
            state = rememberDockAreaState()
            DockArea(state, Modifier.testTag("dock").fillMaxSize()) {
                Panel("editor", "Editor") { Box(Modifier.fillMaxSize().testTag("editor")) {} }
                Panel("files", "Files", Dock.Left()) { Box(Modifier.fillMaxSize().testTag("files")) {} }
            }
        }
        val saved = state.saveLayout()
        assertEquals(saved, state.layout)

        // The user hides a panel: the layout follows Raven's changed callback.
        Rdock_area_hide_panel(widget("dock"), panel("files"))
        assertNotEquals(saved, state.layout)

        assertTrue(state.loadLayout(saved))
        assertEquals(saved, state.saveLayout())
        assertEquals(saved, state.layout)
        assertFalse(state.loadLayout("not a layout"))
    }

    @Test
    fun aLayoutGivenBeforeTheAreaIsShownIsLoadedOnceThePanelsAreIn() {
        var saved = ""
        runRavenTest {
            lateinit var state: DockAreaState
            setContent {
                state = rememberDockAreaState()
                DockArea(state, Modifier.fillMaxSize()) {
                    Panel("editor", "Editor") { Box(Modifier.fillMaxSize()) {} }
                    Panel("files", "Files", Dock.Right(0.4f)) { Box(Modifier.fillMaxSize()) {} }
                }
            }
            saved = state.layout
        }
        runRavenTest {
            lateinit var state: DockAreaState
            setContent {
                state = rememberDockAreaState().also { it.loadLayout(saved) }
                DockArea(state, Modifier.fillMaxSize()) {
                    Panel("editor", "Editor") { Box(Modifier.fillMaxSize()) {} }
                    // Docked elsewhere at first: the layout puts it back on the right.
                    Panel("files", "Files", Dock.Left()) { Box(Modifier.fillMaxSize()) {} }
                }
            }
            assertEquals(saved, state.layout)
        }
    }

    @Test
    fun showPanelBringsATabToTheFront() = runRavenTest {
        layoutWarningSink = { warnings += it }
        lateinit var state: DockAreaState
        var open by mutableStateOf(true)
        setContent {
            state = rememberDockAreaState()
            DockArea(state, Modifier.testTag("dock").fillMaxSize()) {
                Panel("a", "A") { Box(Modifier.fillMaxSize().testTag("a")) {} }
                Panel("b", "B", Dock.Tab("a"), open = open) { Box(Modifier.fillMaxSize().testTag("b")) {} }
            }
        }
        val area = widget("dock")
        state.showPanel("a")
        assertEquals(R_TRUE, Rdock_area_is_panel_shown(area, panel("a")))
        state.showPanel("b")
        assertEquals(R_TRUE, Rdock_area_is_panel_shown(area, panel("b")))

        state.showPanel("nothing")
        open = false
        waitForIdle()
        state.showPanel("b")
        assertEquals(listOf("DockArea: no panel is named nothing", "DockArea: the panel b is hidden: open it to show it"), warnings)
    }

    @Test
    fun aFileIsAccepted() = runRavenTest {
        val directory = memScoped { checkNotNull(mkdtemp("/tmp/compose-raven-browser-XXXXXX".cstr.getPointer(this))).toKString() }
        val file = fopen("$directory/notes.txt", "w")
        fputs("hello", file)
        fclose(file)
        val accepted = mutableListOf<List<String>>()
        val selected = mutableListOf<List<String>>()
        setContent {
            FileBrowser(
                Modifier.testTag("browser").fillMaxSize(),
                initialDirectory = directory,
                filters = listOf(FileFilter("Text", "*.txt")),
                onAccept = { accepted += it },
                onSelectionChange = { selected += it },
            )
        }
        val browser = widget("browser")
        assertTrue(Rfile_browser_get_directory(browser).toRavenString().trimEnd('/').endsWith(directory.substringAfterLast('/')))
        Rfile_browser_select(browser, "notes.txt")
        Rwidget_focus(browser)
        pressKey(Key.Enter)
        waitForIdle()
        assertEquals(listOf(listOf("$directory/notes.txt")), accepted.map { paths -> paths.map { it.replace("//", "/") } })
        assertTrue(selected.isNotEmpty())
        remove("$directory/notes.txt")
        rmdir(directory)
    }

    @Test
    fun aPanelLeftOutOfTheCompositionLeavesTheArea() = runRavenTest {
        var withLog by mutableStateOf(true)
        lateinit var state: DockAreaState
        setContent {
            state = rememberDockAreaState()
            DockArea(state, Modifier.testTag("dock").fillMaxSize()) {
                Panel("editor", "Editor") { Box(Modifier.fillMaxSize().testTag("editor")) {} }
                if (withLog) Panel("log", "Output", Dock.Bottom()) { Box(Modifier.fillMaxSize().testTag("log")) {} }
            }
        }
        assertEquals(2, Rwidget_get_child_count(widget("dock")))
        withLog = false
        waitForIdle()
        assertEquals(1, Rwidget_get_child_count(widget("dock")))
        assertFalse("log" in state.layout, state.layout)
        withLog = true
        waitForIdle()
        assertEquals(2, Rwidget_get_child_count(widget("dock")))
        assertTrue("log" in state.layout, "a panel added later is in the layout: ${state.layout}")
    }

    /** Middle-clicks [panel]'s tab, just above the panel, which closes a closable panel. */
    private fun middleClickTab(panel: Rid) {
        val rect = widgetRect(panel)
        val window = Rwidget_get_window(panel)
        Rwindow_inject_mouse_move(window, rect.x + 14f, rect.y - 8f)
        Rwindow_inject_mouse_button(window, R_MOUSE_MIDDLE, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_MIDDLE, R_FALSE)
    }
}
