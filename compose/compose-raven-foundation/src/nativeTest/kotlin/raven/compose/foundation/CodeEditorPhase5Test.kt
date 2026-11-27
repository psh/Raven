package raven.compose.foundation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.runBlocking
import raven.cinterop.RKey
import raven.cinterop.RToken
import raven.cinterop.R_FALSE
import raven.cinterop.R_KEY_EQUAL
import raven.cinterop.R_KEY_RIGHT
import raven.cinterop.R_KEY_TAB
import raven.cinterop.R_MOD_CTRL
import raven.cinterop.R_MOD_SHIFT
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_TOKEN_KEYWORD
import raven.cinterop.R_TOKEN_STRING
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rtext_editor_get_text
import raven.cinterop.Rtext_editor_get_token_color
import raven.cinterop.Rtext_editor_get_zoom
import raven.cinterop.Rtext_editor_is_completing
import raven.cinterop.Rtheme_get_token_color
import raven.cinterop.Rwidget_focus
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_inject_key
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.height
import raven.compose.ui.Modifier
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.ravenColor
import raven.compose.ui.test.RavenTestScope
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.test.widgetRect
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CodeEditor's phase 5 extras: undo availability, the selection, scrolling, zoom, the editing options and SyntaxTheme. */
class CodeEditorPhase5Test {
    private val red = Color(0xFFFF0000)
    private val green = Color(0xFF00FF00)
    private val blue = Color(0xFF0000FF)

    private fun RavenTestScope.editor(tag: String = "editor"): Rid = onNodeWithTag(tag).fetchWidget()

    private fun RavenTestScope.text(): String = Rtext_editor_get_text(editor()).toRavenString()

    private fun RavenTestScope.press(key: RKey, modifiers: UInt = 0u) {
        val window = Rwidget_get_window(editor())
        Rwindow_inject_key(window, key, R_TRUE, modifiers)
        Rwindow_inject_key(window, key, R_FALSE, modifiers)
    }

    private fun tokenColor(editor: Rid, token: RToken): Color = ravenColor { r, g, b, a -> Rtext_editor_get_token_color(editor, token, r, g, b, a) }

    private fun themeColor(token: RToken): Color = ravenColor { r, g, b, a -> Rtheme_get_token_color(token, r, g, b, a) }

    @Test
    fun undoAndRedoAvailabilityFollowTheEdits() = runRavenTest {
        var text by mutableStateOf("")
        val state = CodeEditorState()
        setContent { CodeEditor(text, { text = it }, Modifier.height(200.dp).testTag("editor"), state = state) }
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
        onNodeWithTag("editor").performTextInput("ab")
        waitForIdle()
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
        state.undo()
        waitForIdle()
        assertFalse(state.canUndo)
        assertTrue(state.canRedo)
    }

    @Test
    fun theSelectionFollowsTheUserAndSelect() = runRavenTest {
        val state = CodeEditorState()
        setContent { CodeEditor("one\ntwo three\n", {}, Modifier.height(200.dp).testTag("editor"), state = state) }
        assertNull(state.selection)
        assertEquals("", state.selectedText())
        state.select(TextPosition(1, 4), TextPosition(1, 9))
        waitForIdle()
        assertEquals(EditorSelection(TextPosition(1, 4), TextPosition(1, 9)), state.selection)
        assertEquals("three", state.selectedText())
        // Backwards, it is still in the order of the text; the cursor is where it ends.
        state.select(TextPosition(1, 3), TextPosition(0, 1))
        waitForIdle()
        assertEquals(EditorSelection(TextPosition(0, 1), TextPosition(1, 3)), state.selection)
        assertEquals(TextPosition(0, 1), state.cursor)
        state.selectAll()
        assertEquals(EditorSelection(TextPosition(0, 0), TextPosition(2, 0)), state.selection)
        assertEquals("one\ntwo three\n", state.selectedText())
        // The user's, followed on the next frame: Shift+Right from the start.
        state.select(TextPosition(0, 0), TextPosition(0, 0))
        Rwidget_focus(editor())
        press(R_KEY_RIGHT, R_MOD_SHIFT)
        press(R_KEY_RIGHT, R_MOD_SHIFT)
        frame()
        assertEquals(EditorSelection(TextPosition(0, 0), TextPosition(0, 2)), state.selection)
        assertEquals("on", state.selectedText())
    }

    @Test
    fun scrollToLineBringsALineIntoView() = runRavenTest {
        val state = CodeEditorState()
        val lines = (0 until 400).joinToString("\n") { "line $it" }
        setContent { CodeEditor(lines, {}, Modifier.fillMaxWidth().height(120.dp).testTag("editor"), state = state) }
        frame()
        runBlocking { state.scrollToLine(300) }
        repeat(30) { frame() }
        // A click in the middle of the editor now lands near line 300.
        onNodeWithTag("editor").performClick()
        frame()
        assertTrue(state.cursor.line in 295..310, "clicked line ${state.cursor.line}")
    }

    @Test
    fun zoomIsSetByTheAppAndFollowsTheUser() = runRavenTest {
        val state = CodeEditorState()
        state.zoom = 2f
        setContent { CodeEditor("text", {}, Modifier.height(200.dp).testTag("editor"), state = state) }
        assertEquals(2f, Rtext_editor_get_zoom(editor()), "a zoom set before it was shown")
        state.zoom = 9f
        assertEquals(4f, state.zoom, "within 0.5 to 4")
        assertEquals(4f, Rtext_editor_get_zoom(editor()))
        state.zoom = 1f
        onNodeWithTag("editor").performClick()
        press(R_KEY_EQUAL, R_MOD_CTRL)
        waitForIdle()
        assertTrue(state.zoom > 1f, "zoomed in by Ctrl + '=': ${state.zoom}")
        assertEquals(Rtext_editor_get_zoom(editor()), state.zoom)
    }

    @Test
    fun aNonZoomableEditorIgnoresTheKeys() = runRavenTest {
        val state = CodeEditorState()
        setContent { CodeEditor("text", {}, Modifier.height(200.dp).testTag("editor"), state = state, zoomable = false) }
        onNodeWithTag("editor").performClick()
        press(R_KEY_EQUAL, R_MOD_CTRL)
        waitForIdle()
        assertEquals(1f, state.zoom)
        assertEquals(1f, Rtext_editor_get_zoom(editor()))
    }

    @Test
    fun tabTypesSpacesOfTheTabSizeWhenAsked() = runRavenTest {
        var text by mutableStateOf("")
        var spaces by mutableStateOf(true)
        setContent {
            CodeEditor(text, { text = it }, Modifier.height(200.dp).testTag("editor"), tabSize = 2, insertSpacesForTab = spaces)
        }
        onNodeWithTag("editor").performClick()
        press(R_KEY_TAB)
        waitForIdle()
        assertEquals("  ", text)
        spaces = false
        text = ""
        waitForIdle()
        press(R_KEY_TAB)
        waitForIdle()
        assertEquals("\t", text)
    }

    @Test
    fun autoPairsCloseABracketUnlessTurnedOff() = runRavenTest {
        var text by mutableStateOf("")
        var pairs by mutableStateOf(true)
        setContent { CodeEditor(text, { text = it }, Modifier.height(200.dp).testTag("editor"), language = "C", autoPairs = pairs) }
        onNodeWithTag("editor").performTextInput("(")
        waitForIdle()
        assertEquals("()", text)
        pairs = false
        text = ""
        waitForIdle()
        onNodeWithTag("editor").performTextInput("(")
        waitForIdle()
        assertEquals("(", text)
    }

    @Test
    fun autoCompletionProposesWhileTypingUnlessTurnedOff() = runRavenTest {
        var text by mutableStateOf("")
        var completes by mutableStateOf(true)
        setContent {
            CodeEditor(text, { text = it }, Modifier.height(200.dp).testTag("editor"), language = "C", autoCompletion = completes)
        }
        onNodeWithTag("editor").performTextInput("retu")
        waitForIdle()
        assertEquals(R_TRUE, Rtext_editor_is_completing(editor()))
        completes = false
        text = ""
        waitForIdle()
        onNodeWithTag("editor").performTextInput("retu")
        waitForIdle()
        assertEquals(R_FALSE, Rtext_editor_is_completing(editor()))
    }

    @Test
    fun twoEditorsUnderDifferentSyntaxThemesAndANestedOneWinning() = runRavenTest {
        var outer by mutableStateOf(SyntaxTheme(keyword = red))
        setContent {
            Column {
                CompositionLocalProvider(LocalSyntaxTheme provides outer) {
                    CodeEditor("int x;", {}, Modifier.height(60.dp).testTag("a"), language = "C")
                    CompositionLocalProvider(LocalSyntaxTheme provides SyntaxTheme(keyword = green, string = blue)) {
                        CodeEditor("int x;", {}, Modifier.height(60.dp).testTag("b"), language = "C")
                    }
                }
                CodeEditor("int x;", {}, Modifier.height(60.dp).testTag("c"), language = "C")
            }
        }
        assertEquals(red, tokenColor(editor("a"), R_TOKEN_KEYWORD))
        assertEquals(green, tokenColor(editor("b"), R_TOKEN_KEYWORD))
        assertEquals(blue, tokenColor(editor("b"), R_TOKEN_STRING))
        assertEquals(themeColor(R_TOKEN_KEYWORD), tokenColor(editor("c"), R_TOKEN_KEYWORD), "no provider: the theme's")
        assertEquals(themeColor(R_TOKEN_STRING), tokenColor(editor("a"), R_TOKEN_STRING), "unspecified: the theme's")
        // A colour no longer specified goes back to the theme's.
        outer = SyntaxTheme()
        waitForIdle()
        assertEquals(themeColor(R_TOKEN_KEYWORD), tokenColor(editor("a"), R_TOKEN_KEYWORD))
        assertNotEquals(themeColor(R_TOKEN_KEYWORD), tokenColor(editor("b"), R_TOKEN_KEYWORD))
    }
}
