package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.RKey
import raven.cinterop.R_FALSE
import raven.cinterop.R_KEY_ENTER
import raven.cinterop.R_TRUE
import raven.cinterop.Rtext_editor_get_marker_count
import raven.cinterop.Rtext_editor_get_text
import raven.cinterop.Rtext_editor_is_completing
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_key
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.ui.Modifier
import raven.compose.ui.testTag
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodeEditorExtrasTest {
    private fun RavenTestScope.editorText(): String = Rtext_editor_get_text(onNodeWithTag("editor").fetchWidget()).toRavenString()

    private fun RavenTestScope.markerCount(): Int = Rtext_editor_get_marker_count(onNodeWithTag("editor").fetchWidget())

    private fun RavenTestScope.press(key: RKey) {
        val window = Rwidget_get_window(onNodeWithTag("editor").fetchWidget())
        Rwindow_inject_key(window, key, R_TRUE, 0u)
        Rwindow_inject_key(window, key, R_FALSE, 0u)
    }

    @Test
    fun undoAfterTypingRestoresTheText() = runRavenTest {
        var text by mutableStateOf("")
        val changes = mutableListOf<String>()
        val state = CodeEditorState()
        setContent {
            CodeEditor(text, onValueChange = {
                changes += it
                text = it
            }, Modifier.fillMaxSize().testTag("editor"), state = state)
        }
        onNodeWithTag("editor").performTextInput("ab")
        waitForIdle()
        state.undo()
        waitForIdle()
        assertEquals("", text)
        assertEquals("", editorText())
        assertEquals(listOf("a", "ab", ""), changes)

        state.redo()
        waitForIdle()
        assertEquals("ab", text)
    }

    @Test
    fun insertTextTypesAtTheCursorAndCanBeUndone() = runRavenTest {
        var text by mutableStateOf("")
        val state = CodeEditorState()
        setContent { CodeEditor(text, onValueChange = { text = it }, Modifier.fillMaxSize().testTag("editor"), state = state) }
        onNodeWithTag("editor").performTextInput("ac")
        waitForIdle()
        state.insertText("b")
        waitForIdle()
        assertEquals("acb", text)
        assertEquals("acb", editorText())
        state.undo()
        waitForIdle()
        assertEquals("ac", text)
        assertEquals("ac", editorText())
    }

    @Test
    fun selectAllThenInsertTextReplacesEverything() = runRavenTest {
        var text by mutableStateOf("one two")
        val state = CodeEditorState()
        setContent { CodeEditor(text, onValueChange = { text = it }, Modifier.fillMaxSize().testTag("editor"), state = state) }
        state.selectAll()
        state.insertText("three")
        waitForIdle()
        assertEquals("three", text)
    }

    @Test
    fun commandsOnADetachedStateDoNothing() = runRavenTest {
        var shown by mutableStateOf(true)
        var text by mutableStateOf("")
        val state = CodeEditorState()
        setContent {
            if (shown) CodeEditor(text, onValueChange = { text = it }, Modifier.fillMaxSize().testTag("editor"), state = state)
        }
        onNodeWithTag("editor").performTextInput("x")
        waitForIdle()
        shown = false
        waitForIdle()
        state.undo()
        state.insertText("y")
        state.selectAll()
        state.showCompletions()
        waitForIdle()
        assertEquals("x", text)
        // A state no editor ever showed does nothing either.
        CodeEditorState().undo()
    }

    @Test
    fun theCursorFollowsATypedCharacter() = runRavenTest {
        var text by mutableStateOf("")
        val state = CodeEditorState()
        var cursorReads = 0
        var shownCursor = TextPosition(-1, -1)
        setContent {
            CodeEditor(text, onValueChange = { text = it }, Modifier.fillMaxSize().testTag("editor"), state = state)
            cursorReads++
            shownCursor = state.cursor
        }
        assertEquals(TextPosition(0, 0), shownCursor)
        onNodeWithTag("editor").performTextInput("a")
        frame()
        waitForIdle()
        assertEquals(TextPosition(0, 1), state.cursor)
        assertEquals(TextPosition(0, 1), shownCursor)

        // Frames where it doesn't move recompose nothing.
        val reads = cursorReads
        repeat(5) { frame() }
        waitForIdle()
        assertEquals(reads, cursorReads)

        state.insertText("b\nc")
        frame()
        waitForIdle()
        assertEquals(TextPosition(1, 1), shownCursor)
    }

    @Test
    fun completionsFromTheLambdaAreOfferedForAPrefix() = runRavenTest {
        var text by mutableStateOf("")
        val prefixes = mutableListOf<String>()
        val state = CodeEditorState()
        setContent {
            CodeEditor(
                text,
                onValueChange = { text = it },
                Modifier.fillMaxSize().testTag("editor"),
                state = state,
                completions = { prefix ->
                    prefixes += prefix
                    listOf(Completion("println", "function"), Completion("other"))
                },
            )
        }
        onNodeWithTag("editor").performTextInput("pri")
        waitForIdle()
        state.showCompletions()
        assertEquals("pri", prefixes.last())
        assertEquals(R_TRUE, Rtext_editor_is_completing(onNodeWithTag("editor").fetchWidget()))
        press(R_KEY_ENTER)
        waitForIdle()
        assertEquals("println", text)
        assertEquals("println", editorText())
    }

    @Test
    fun noCompletionsOfferNothing() = runRavenTest {
        var text by mutableStateOf("")
        val state = CodeEditorState()
        setContent {
            CodeEditor(text, onValueChange = { text = it }, Modifier.fillMaxSize().testTag("editor"), state = state, completions = { emptyList() })
        }
        onNodeWithTag("editor").performTextInput("zq")
        waitForIdle()
        state.showCompletions()
        assertEquals(R_FALSE, Rtext_editor_is_completing(onNodeWithTag("editor").fetchWidget()))
    }

    @Test
    fun aMarkerOnAnEditedLineComesBackAfterTheEdit() = runRavenTest {
        var text by mutableStateOf("one\ntwo")
        val markers = listOf(
            EditorMarker(0, 0, kind = MarkerKind.Info, message = "first"),
            EditorMarker(1, 0, 3, MarkerKind.Warning, "second"),
        )
        val state = CodeEditorState()
        setContent {
            CodeEditor(text, onValueChange = { text = it }, Modifier.fillMaxSize().testTag("editor"), state = state, markers = markers)
        }
        assertEquals(2, markerCount())
        // Raven drops the marker of the line the cursor types on, and the edit puts it back at once.
        val editor = onNodeWithTag("editor").fetchWidget()
        state.insertText("!")
        assertEquals(2, Rtext_editor_get_marker_count(editor))
        waitForIdle()
        assertEquals("!one\ntwo", text)
        assertEquals(2, markerCount())
        state.selectAll()
        state.insertText("new")
        waitForIdle()
        assertEquals(2, markerCount())
    }

    @Test
    fun markersComeBackAfterARejectedEdit() = runRavenTest {
        val markers = listOf(EditorMarker(0, 0, kind = MarkerKind.Error, message = "bad"))
        setContent { CodeEditor("fixed", modifier = Modifier.fillMaxSize().testTag("editor"), markers = markers) }
        onNodeWithTag("editor").performTextInput("x")
        waitForIdle()
        frame()
        assertEquals("fixed", editorText())
        assertEquals(1, markerCount())
    }

    @Test
    fun aNewMarkerListReplacesTheOldOne() = runRavenTest {
        var markers by mutableStateOf(listOf(EditorMarker(0, 0, kind = MarkerKind.Error, message = "bad")))
        setContent { CodeEditor("a\nb\nc", modifier = Modifier.testTag("editor"), markers = markers) }
        assertEquals(1, markerCount())
        markers = List(3) { EditorMarker(it, 0, kind = MarkerKind.Info, message = "line $it") }
        assertEquals(3, markerCount())
        markers = emptyList()
        assertEquals(0, markerCount())
    }

    @Test
    fun aRegisteredLanguageIsListedAndChosenForItsFiles() = runRavenTest {
        var name = ""
        var languages = emptyList<String>()
        var forIni: String? = null
        var forUnknown: String? = "?"
        setContent {
            name = rememberCodeEditorLanguage(
                CodeEditorLanguage(
                    name = "INI",
                    extensions = listOf("ini", "cfg", "conf"),
                    constants = listOf("true", "false", "yes", "no", "on", "off"),
                    lineComment = ";",
                    stringQuotes = "\"",
                    flags = setOf(LanguageFlag.IgnoreCase),
                ),
            )
            languages = codeEditorLanguages()
            forIni = codeEditorLanguageForFile("x.ini")
            forUnknown = codeEditorLanguageForFile("x.nosuchextension")
            CodeEditor("; note", modifier = Modifier.testTag("editor"), language = name)
        }
        assertEquals("INI", name)
        assertTrue("INI" in languages, "INI is not in $languages")
        assertEquals("INI", forIni)
        assertNull(forUnknown)
    }

    @Test
    fun codeEditorLanguageForFileFailsOutsideTheComposition() {
        assertFailsWith<IllegalStateException> { codeEditorLanguageForFile("x.ini") }
    }
}
