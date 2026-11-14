package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.mkdtemp
import platform.posix.remove
import platform.posix.rmdir
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_FILE_BROWSER
import raven.cinterop.Rfile_browser_select
import raven.cinterop.Rid
import raven.cinterop.Rwidget_focus
import raven.cinterop.Rwidget_get_type
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_has_dialog
import raven.compose.ui.Dialog
import raven.compose.ui.DialogProperties
import raven.compose.ui.Modifier
import raven.compose.ui.input.key.Key
import raven.compose.ui.testTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dialog, and the message and file dialogs built on it, headless. */
class DefaultDialogsTest {
    private lateinit var scope: CoroutineScope

    private fun RavenTestScope.hasDialog(): Boolean = Rwindow_has_dialog(Rwidget_get_window(onNodeWithTag("page").fetchWidget())) == R_TRUE

    @Test
    fun aDialogIsAnsweredAndLeaves() = runRavenTest {
        var open by mutableStateOf(true)
        setContent {
            Text("Page", Modifier.testTag("page"))
            if (open) {
                Dialog(onDismissRequest = {}) { Button(onClick = { open = false }) { Text("Done") } }
            }
        }
        assertTrue(hasDialog())
        onNodeWithText("Done").performClick()
        waitForIdle()
        onNodeWithText("Done").assertDoesNotExist()
        assertEquals(false, hasDialog(), "removed from the composition, the dialog closes")
    }

    @Test
    fun escapeAsksAndTheAppDecides() = runRavenTest {
        var open by mutableStateOf(true)
        var take by mutableStateOf(false)
        var asked = 0
        setContent {
            Text("Page", Modifier.testTag("page"))
            if (open) Dialog(onDismissRequest = { asked++; if (take) open = false }) { Text("Sure?") }
        }
        pressKey(Key.Escape)
        waitForIdle()
        assertEquals(1, asked)
        assertTrue(hasDialog(), "refused, it stays")
        onNodeWithText("Sure?").assertExists()

        take = true
        pressKey(Key.Escape)
        waitForIdle()
        assertEquals(2, asked)
        assertEquals(false, hasDialog())
    }

    @Test
    fun withoutDismissOnBackPressEscapeDoesNothing() = runRavenTest {
        var asked = 0
        setContent {
            Text("Page", Modifier.testTag("page"))
            Dialog(onDismissRequest = { asked++ }, properties = DialogProperties(dismissOnBackPress = false)) { Text("Stay") }
        }
        pressKey(Key.Escape)
        waitForIdle()
        assertEquals(0, asked)
        assertTrue(hasDialog())
    }

    @Test
    fun aDialogSeesItsWindowsLocalsAndRecomposes() = runRavenTest {
        var count by mutableStateOf(1)
        setContent {
            Text("Page", Modifier.testTag("page"))
            Dialog(onDismissRequest = {}) { Text("Count $count", style = raven.compose.ui.theme.RavenTheme.typography.title) }
        }
        onNodeWithText("Count 1").assertExists()
        count = 2
        waitForIdle()
        onNodeWithText("Count 2").assertExists()
    }

    private fun RavenTestScope.setPage() = setContent {
        scope = rememberCoroutineScope()
        Text("Page", Modifier.testTag("page"))
    }

    @Test
    fun aMessageBoxIsAnsweredByItsButtons() = runRavenTest {
        setPage()
        val answers = mutableListOf<MessageResult>()
        scope.launch { answers += messageBox("Quit", "Discard the changes?", MessageButtons.YesNoCancel) }
        waitForIdle()
        onNodeWithText("Discard the changes?").assertExists()
        onNodeWithText("No").performClick()
        waitForIdle()
        assertEquals(listOf(MessageResult.No), answers)
        onNodeWithText("Discard the changes?").assertDoesNotExist()
        assertEquals(false, hasDialog())
    }

    @Test
    fun escapeAnswersCancelOrNo() = runRavenTest {
        setPage()
        val answers = mutableListOf<MessageResult>()
        scope.launch { answers += messageBox("Save", "Save the file?", MessageButtons.OkCancel) }
        waitForIdle()
        pressKey(Key.Escape)
        waitForIdle()
        scope.launch { answers += messageBox("Quit", "Quit now?", MessageButtons.YesNo) }
        waitForIdle()
        pressKey(Key.Escape)
        waitForIdle()
        assertEquals(listOf(MessageResult.Cancel, MessageResult.No), answers)
    }

    @Test
    fun cancellingTheCallTakesTheDialogAway() = runRavenTest {
        setPage()
        var job: Job? = null
        job = scope.launch { messageBox("Wait", "Working...") }
        waitForIdle()
        assertTrue(hasDialog())
        job.cancel()
        waitForIdle()
        assertEquals(false, hasDialog())
        onNodeWithText("Working...").assertDoesNotExist()
    }

    private fun RavenTestScope.fileBrowser(): Rid =
        findNodes { Rwidget_get_type(it.widget) == R_WIDGET_FILE_BROWSER }.single().widget

    @Test
    fun aFileIsChosenAndACancelChoosesNone() = runRavenTest {
        val directory = memScoped { checkNotNull(mkdtemp("/tmp/compose-raven-dialog-XXXXXX".cstr.getPointer(this))).toKString() }
        val file = fopen("$directory/notes.txt", "w")
        fputs("hello", file)
        fclose(file)
        setPage()
        val chosen = mutableListOf<List<String>>()
        scope.launch { chosen += openFileDialog(listOf(FileFilter("Text", "*.txt")), initialDirectory = directory) }
        waitForIdle()
        onNodeWithText("Open").assertExists()
        val browser = fileBrowser()
        Rfile_browser_select(browser, "notes.txt")
        Rwidget_focus(browser)
        pressKey(Key.Enter)
        waitForIdle()
        assertEquals(listOf("$directory/notes.txt"), chosen.single().map { it.replace("//", "/") })
        assertEquals(false, hasDialog())

        var saved: String? = "not yet"
        scope.launch { saved = saveFileDialog(title = "Export", initialDirectory = directory) }
        waitForIdle()
        onNodeWithText("Export").assertExists()
        pressKey(Key.Escape)
        waitForIdle()
        assertNull(saved, "dismissed, no file")
        remove("$directory/notes.txt")
        rmdir(directory)
    }
}
