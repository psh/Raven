@file:OptIn(InternalComposeRavenApi::class, ExperimentalForeignApi::class)

package raven.sample.notepad

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.getpid
import platform.posix.mkdtemp
import platform.posix.remove
import platform.posix.rmdir
import platform.posix.usleep
import raven.cinterop.R_WIDGET_FILE_BROWSER
import raven.cinterop.R_WIDGET_TEXT_INPUT
import raven.cinterop.Rfile_browser_select
import raven.cinterop.Rfile_browser_set_directory
import raven.cinterop.Rid
import raven.cinterop.Rlabel_get_text
import raven.cinterop.Rtext_editor_get_language
import raven.cinterop.Rtext_editor_get_marker_count
import raven.cinterop.Rtext_editor_get_text
import raven.cinterop.Rtext_input_get_text
import raven.cinterop.Rwidget_focus
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_type
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_get_title
import raven.compose.foundation.RavenTestScope
import raven.compose.foundation.codeEditorLanguages
import raven.compose.foundation.runRavenTest
import raven.compose.foundation.widgetRect
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.input.key.Key
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class NotepadTest {
    @Test
    fun itStartsWithTheWelcomeTextAndItsNotes() = runRavenTest {
        setNotepad()
        assertEquals(WELCOME, editorText())
        assertEquals("C", editorLanguage())
        assertEquals("Untitled - Raven Notepad", title())
        onNodeWithText("Drop a file on the window to open it.").assertExists()
        onNodeWithText("2 notes").assertExists()
        assertEquals(2, Rtext_editor_get_marker_count(onNodeWithTag("editor").fetchWidget()))
        onNodeWithText("Line 1, column 1").assertExists()
        // the language is on the right of the header bar, as in the C example
        val editor = widgetRect(onNodeWithTag("editor").fetchWidget())
        val language = widgetRect(onNodeWithTag("language").fetchWidget())
        assertTrue(language.x > editor.x + editor.width / 2, "the language is at ${language.x}, the editor ${editor.x} to ${editor.x + editor.width}")
    }

    @Test
    fun typingMarksTheTextModified() = runRavenTest {
        setNotepad()
        onNodeWithTag("editor").performTextInput("x")
        assertEquals("Untitled * - Raven Notepad", title())
        assertTrue("x" in editorText())
    }

    @Test
    fun choosingALanguageReachesTheEditor() = runRavenTest {
        setNotepad()
        val languages = listOf("Plain text") + codeEditorLanguages()
        assertTrue("INI" in languages, "the INI language is listed: $languages")
        onNodeWithTag("language").performSelection(languages.indexOf("Python"))
        assertEquals("Python", editorLanguage())
        onNodeWithTag("language").performSelection(0)
        assertEquals("", editorLanguage())
    }

    @Test
    fun aDroppedTextIsTypedAtTheCursorAndRescannedOnceTypingPauses() = runRavenTest {
        setNotepad()
        assertTrue(performExternalDrop(text = "// FIXME dropped\n"))
        assertTrue(editorText().startsWith("// FIXME dropped\n/* Raven Notepad"), editorText())
        assertEquals("Untitled * - Raven Notepad", title())
        // the notes are scanned 0.4 s after the last change
        waitUntil { labelShown("3 notes") }
    }

    @Test
    fun aDroppedFileOpensWithTheLanguageOfItsName() = runRavenTest {
        val path = "/tmp/notepad-test-${getpid()}.ini"
        assertTrue(writeTextFile(path, "; settings\nname = TODO\n"))
        try {
            setNotepad()
            assertTrue(performExternalDrop(paths = listOf(path)))
            waitForIdle()
            assertEquals("; settings\nname = TODO\n", editorText())
            assertEquals("INI", editorLanguage())
            assertEquals("${fileName(path)} - Raven Notepad", title())
            onNodeWithText("Opened $path").assertExists()
            onNodeWithText("1 note").assertExists()
        } finally {
            remove(path)
        }
    }

    @Test
    fun discardingAsksFirst() = runRavenTest {
        setNotepad()
        onNodeWithTag("editor").performTextInput("x")
        waitForIdle()
        pressKey(Key.N, ctrl = true)
        waitForIdle()
        onNodeWithText("The text has changes that were not saved. Discard them?").assertExists()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertTrue(editorText().isNotEmpty(), "kept")

        pressKey(Key.N, ctrl = true)
        waitForIdle()
        onNodeWithText("OK").performClick()
        waitForIdle()
        assertEquals("", editorText())
        onNodeWithText("New text.").assertExists()
    }

    @Test
    fun aFileIsOpenedThroughTheDialog() = runRavenTest {
        val directory = memScoped { checkNotNull(mkdtemp("/tmp/notepad-open-XXXXXX".cstr.getPointer(this))).toKString() }
        val path = "$directory/main.rs"
        assertTrue(writeTextFile(path, "fn main() {}\n"))
        try {
            setNotepad()
            pressKey(Key.O, ctrl = true)
            waitForIdle()
            onNodeWithText("Open a text").assertExists()
            val browser = fileBrowser()
            Rfile_browser_set_directory(browser, directory)
            Rfile_browser_select(browser, "main.rs")
            Rwidget_focus(browser)
            pressKey(Key.Enter)
            waitForIdle()
            assertEquals("fn main() {}\n", editorText())
            assertEquals("Rust", editorLanguage())
            onNodeWithText("Opened $path").assertExists()
            onNodeWithText("Open a text").assertDoesNotExist()
        } finally {
            remove(path)
            rmdir(directory)
        }
    }

    @Test
    fun saveAsWritesWhereTheDialogSays() = runRavenTest {
        val directory = memScoped { checkNotNull(mkdtemp("/tmp/notepad-save-XXXXXX".cstr.getPointer(this))).toKString() }
        val path = "$directory/untitled.txt"
        try {
            setNotepad()
            pressKey(Key.S, ctrl = true, shift = true)
            waitForIdle()
            onNodeWithText("Save the text").assertExists()
            Rfile_browser_set_directory(fileBrowser(), directory)
            // The name proposed, untitled.txt, taken with Enter in the browser's name field.
            Rwidget_focus(nameField(fileBrowser()))
            pressKey(Key.Enter)
            waitForIdle()
            assertEquals(WELCOME, readTextFile(path))
            onNodeWithText("Saved $path").assertExists()
            assertEquals("untitled.txt - Raven Notepad", title())

            // Cancelled, nothing is written and the text keeps its file.
            pressKey(Key.S, ctrl = true, shift = true)
            waitForIdle()
            pressKey(Key.Escape)
            waitForIdle()
            onNodeWithText("Save the text").assertDoesNotExist()
            assertEquals("untitled.txt - Raven Notepad", title())
        } finally {
            remove(path)
            rmdir(directory)
        }
    }

    /** The text input of [browser] holding the proposed name. */
    private fun nameField(browser: Rid): Rid {
        fun search(widget: Rid): Rid? {
            if (Rwidget_get_type(widget) == R_WIDGET_TEXT_INPUT && Rtext_input_get_text(widget).toRavenString() == "untitled.txt") return widget
            return (0 until Rwidget_get_child_count(widget)).firstNotNullOfOrNull { search(Rwidget_get_child(widget, it)) }
        }
        return search(browser) ?: fail("no name field holds untitled.txt")
    }

    private fun RavenTestScope.fileBrowser(): Rid = findNodes { Rwidget_get_type(it.widget) == R_WIDGET_FILE_BROWSER }.single().widget

    @Test
    fun theNotesAreScannedLikeTheCExample() {
        val markers = scanNotes("a\n// TODO x\n// FIXME y TODO\né TODO")
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), markers.map { it.line to it.fromColumn })
        assertEquals(listOf("Something left to do", "Something to fix", "Something left to do"), markers.map { it.message })
        assertEquals("", notesText(0))
        assertEquals("1 note", notesText(1))
        assertEquals("2 notes", notesText(2))
    }

    private fun RavenTestScope.setNotepad() {
        host.start { Notepad(onExit = {}) }
        waitForIdle()
    }

    private fun RavenTestScope.editorText() = Rtext_editor_get_text(onNodeWithTag("editor").fetchWidget()).toRavenString()

    private fun RavenTestScope.editorLanguage() = Rtext_editor_get_language(onNodeWithTag("editor").fetchWidget())?.toKString() ?: ""

    private fun RavenTestScope.title() = Rwindow_get_title(Rwidget_get_window(onNodeWithTag("editor").fetchWidget())).toRavenString()

    private fun RavenTestScope.labelShown(text: String) =
        Rlabel_get_text(onNodeWithTag("notes").fetchWidget()).toRavenString() == text

    /** Draws frames, in real time, until [condition] holds, for at most two seconds. */
    private fun RavenTestScope.waitUntil(condition: () -> Boolean) {
        repeat(200) {
            waitForIdle()
            if (condition()) return
            usleep(10_000u)
            frame()
        }
        fail("the condition never held")
    }
}
