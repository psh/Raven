package raven.sample.xml

import raven.cinterop.Rlabel_get_text
import raven.cinterop.Rtext_editor_get_text
import raven.compose.foundation.runRavenTest
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslatorUiTest {
    @Test
    fun itStartsWithTheExampleOfXmlMdTranslated() = runRavenTest {
        setContent { TranslatorUi() }
        assertEquals(xmlExample, Rtext_editor_get_text(onNodeWithTag("xml").fetchWidget()).toRavenString())
        val code = Rtext_editor_get_text(onNodeWithTag("code").fetchWidget()).toRavenString()
        assertTrue("fun TranslatedUi()" in code && "fun main() = singleWindowApplication(title = \"Tool\"" in code, code)
        assertEquals(
            "2 warnings:\nline 2: <Shortcut> has no composable yet\nline 9: <ListView> has no composable yet",
            Rlabel_get_text(onNodeWithTag("warnings").fetchWidget()).toRavenString(),
        )
    }

    @Test
    fun itTranslatesAgainOnEveryChange() = runRavenTest {
        setContent { TranslatorUi("<VBox>\n    <Label>Hi</Label>\n</VBox>\n") }
        assertEquals("No warnings", Rlabel_get_text(onNodeWithTag("warnings").fetchWidget()).toRavenString())
        // Typed at the caret, the end of the text: after the root, so the text no longer parses.
        onNodeWithTag("xml").performTextInput("oops")
        assertEquals("", Rtext_editor_get_text(onNodeWithTag("code").fetchWidget()).toRavenString())
        val warnings = Rlabel_get_text(onNodeWithTag("warnings").fetchWidget()).toRavenString()
        assertTrue(warnings.startsWith("1 warning: line 4, column ") && "after the root element" in warnings, warnings)
    }
}
