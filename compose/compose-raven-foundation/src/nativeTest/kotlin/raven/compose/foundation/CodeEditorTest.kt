package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Rtext_editor_can_undo
import raven.cinterop.Rtext_editor_get_language
import raven.cinterop.Rtext_editor_get_text
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.ui.Modifier
import raven.compose.ui.testTag
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CodeEditorTest {
    private fun RavenTestScope.editorText(): String = Rtext_editor_get_text(onNodeWithTag("editor").fetchWidget()).toRavenString()

    private fun RavenTestScope.editorLanguage(): String =
        Rtext_editor_get_language(onNodeWithTag("editor").fetchWidget()).toRavenString()

    @Test
    fun typingRoundTripsThroughTheApp() = runRavenTest {
        var text by mutableStateOf("")
        val changes = mutableListOf<String>()
        setContent {
            CodeEditor(text, onValueChange = {
                changes += it
                text = it
            }, Modifier.fillMaxSize().testTag("editor"))
        }
        onNodeWithTag("editor").performTextInput("fun")
        assertEquals(listOf("f", "fu", "fun"), changes)
        assertEquals("fun", editorText())
        // Nothing was written back, so the editor's undo history survives.
        assertEquals(R_TRUE, Rtext_editor_can_undo(onNodeWithTag("editor").fetchWidget()))

        text = "val x = 1\nval y = 2"
        assertEquals("val x = 1\nval y = 2", editorText())
    }

    @Test
    fun rewrittenTextIsWrittenBack() = runRavenTest {
        var text by mutableStateOf("")
        setContent { CodeEditor(text, onValueChange = { text = it.uppercase() }, Modifier.fillMaxSize().testTag("editor")) }
        onNodeWithTag("editor").performTextInput("ab")
        assertEquals("AB", editorText())
    }

    @Test
    fun anEditorWithoutAHandlerReverts() = runRavenTest {
        setContent { CodeEditor("fixed", modifier = Modifier.fillMaxSize().testTag("editor")) }
        onNodeWithTag("editor").performTextInput("x")
        assertEquals("fixed", editorText())
    }

    @Test
    fun aReadOnlyEditorTakesNoTyping() = runRavenTest {
        var changes = 0
        setContent {
            CodeEditor("fixed", onValueChange = { changes++ }, Modifier.fillMaxSize().testTag("editor"), readOnly = true)
        }
        onNodeWithTag("editor").performTextInput("x")
        assertEquals("fixed", editorText())
        assertEquals(0, changes)
        assertEquals(R_FALSE, Rtext_editor_can_undo(onNodeWithTag("editor").fetchWidget()))
    }

    @Test
    fun kotlinIsALanguage() = runRavenTest {
        setContent { CodeEditor("fun main() {}", modifier = Modifier.testTag("editor"), language = "Kotlin") }
        assertEquals("Kotlin", editorLanguage())
    }

    @Test
    fun anUnknownLanguageShowsPlainText() = runRavenTest {
        var language by mutableStateOf<String?>("Kotlin")
        setContent { CodeEditor("x", modifier = Modifier.testTag("editor"), language = language) }
        assertEquals("Kotlin", editorLanguage())
        language = "NoSuchLanguage"
        assertEquals("", editorLanguage())
        language = "xml"
        assertEquals("XML", editorLanguage())
        language = null
        assertEquals("", editorLanguage())
    }

    @Test
    fun codeEditorLanguagesIncludesKotlin() = runRavenTest {
        var languages = emptyList<String>()
        setContent { languages = codeEditorLanguages() }
        assertTrue("Kotlin" in languages, "Kotlin is not in $languages")
    }

    @Test
    fun codeEditorLanguagesFailsOutsideTheComposition() {
        assertFailsWith<IllegalStateException> { codeEditorLanguages() }
    }
}
