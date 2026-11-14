package raven.compose.foundation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import raven.cinterop.RKey
import raven.cinterop.R_FALSE
import raven.cinterop.R_KEY_ENTER
import raven.cinterop.R_KEY_ESCAPE
import raven.cinterop.R_MESSAGE_RESULT_CANCEL
import raven.cinterop.R_MESSAGE_RESULT_NO
import raven.cinterop.R_MESSAGE_RESULT_OK
import raven.cinterop.R_MESSAGE_RESULT_YES
import raven.cinterop.R_TRUE
import raven.cinterop.Rquit
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_has_dialog
import raven.cinterop.Rwindow_inject_key
import raven.compose.ui.Modifier
import raven.compose.ui.testTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DialogsTest {
    @Test
    fun aDefaultCallFailsWhenNoApplicationRuns() = runBlocking {
        assertFailsWith<IllegalStateException> { messageBox("Quit", "Discard the changes?") }
        assertFailsWith<IllegalStateException> { openFileDialog() }
        Unit
    }

    @Test
    fun aNativeCallFailsWhenNoApplicationRuns() = runBlocking {
        assertFailsWith<IllegalStateException> { messageBox("Quit", "Discard the changes?", native = true) }
        Unit
    }

    @Test
    fun aFileFilterJoinsItsPatternsAsRavenTakesThem() {
        val request = FileDialogRequest(
            title = null,
            initialDirectory = null,
            filters = listOf(FileFilter("Images", "*.png", "*.jpg"), FileFilter("All files", listOf("*"))),
        )
        assertEquals(listOf("*.png;*.jpg", "*"), request.ravenPatterns)
        assertFailsWith<IllegalArgumentException> { FileFilter("Images", "*.png;*.jpg") }
        assertFailsWith<IllegalArgumentException> { FileFilter("Nothing") }
    }

    @Test
    fun ravensResultsBecomeMessageResults() {
        assertEquals(MessageResult.Cancel, R_MESSAGE_RESULT_CANCEL.toMessageResult())
        assertEquals(MessageResult.Ok, R_MESSAGE_RESULT_OK.toMessageResult())
        assertEquals(MessageResult.Yes, R_MESSAGE_RESULT_YES.toMessageResult())
        assertEquals(MessageResult.No, R_MESSAGE_RESULT_NO.toMessageResult())
    }

    // Headless, every message box and file dialog is Raven's own, which a key answers.

    @Test
    fun escapeCancelsANativeMessageBox() = runRavenTest {
        assertEquals(MessageResult.Cancel, answer(R_KEY_ESCAPE) { messageBox("Quit", "Discard?", MessageButtons.OkCancel, native = true) })
    }

    @Test
    fun escapeAnswersNoWithoutACancelButton() = runRavenTest {
        assertEquals(MessageResult.No, answer(R_KEY_ESCAPE) { messageBox("Quit", "Discard?", MessageButtons.YesNo, native = true) })
    }

    @Test
    fun enterPressesTheMainButton() = runRavenTest {
        assertEquals(MessageResult.Yes, answer(R_KEY_ENTER) { messageBox("", "Discard?", MessageButtons.YesNoCancel, native = true) })
    }

    @Test
    fun escapeCancelsTheNativeFileDialogs() {
        runRavenTest {
            val filters = listOf(FileFilter("Text", "*.txt"))
            assertEquals(emptyList(), answer(R_KEY_ESCAPE) { openFileDialog(filters, multiple = true, native = true) })
        }
        runRavenTest {
            assertNull(answer(R_KEY_ESCAPE) { saveFileDialog(title = "Save the notes", fileName = "notes", defaultExtension = "txt", native = true) })
        }
        runRavenTest {
            assertNull(answer(R_KEY_ESCAPE) { selectFolderDialog(initialDirectory = "/", native = true) })
        }
    }
}

/**
 * Makes [call] from a click, as an app would, presses [key] in the window once its dialog shows,
 * and returns what the call returned. Raven quits, failing the test, if the dialog never closes.
 */
private fun <T> RavenTestScope.answer(key: RKey, call: suspend () -> T): T {
    var result: Result<T>? = null
    var framesShown = 0
    setContent {
        val scope = rememberCoroutineScope()
        Button(onClick = { scope.launch { result = runCatching { call() } } }, Modifier.testTag("open")) { Text("Open") }
        LaunchedEffect(Unit) {
            // Runs in the dialog's nested frames, with the Recomposer.
            while (result == null) {
                withFrameNanos { }
                val window = Rwidget_get_window(host.windowRoots.single().widget)
                if (Rwindow_has_dialog(window) != R_TRUE) continue
                framesShown++
                if (framesShown == 2) {
                    Rwindow_inject_key(window, key, R_TRUE, 0u)
                    Rwindow_inject_key(window, key, R_FALSE, 0u)
                }
                if (framesShown > MAX_DIALOG_FRAMES) Rquit(1)
            }
        }
    }
    onNodeWithTag("open").performClick()
    frame()
    waitForIdle()
    assertTrue(framesShown >= 2, "the dialog was shown for $framesShown frames")
    return result!!.getOrThrow()
}

private const val MAX_DIALOG_FRAMES = 300
