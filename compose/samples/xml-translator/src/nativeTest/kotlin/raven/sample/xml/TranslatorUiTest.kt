package raven.sample.xml

import raven.compose.ui.test.runRavenTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslatorUiTest {
    @Test
    fun itStartsWithTheExampleOfXmlMdTranslated() = runRavenTest {
        setContent { TranslatorUi() }
        assertEquals(xmlExample, onNodeWithTag("xml").fetchText())
        val code = onNodeWithTag("code").fetchText()
        assertTrue("fun TranslatedUi()" in code && "fun main() = singleWindowApplication(state = WindowState(size = DpSize(800.dp, 500.dp)), title = \"Tool\"" in code, code)
        assertEquals(
            "2 warnings:\nline 2: <Shortcut> has no composable yet\nline 9: <ListView> has no composable yet",
            onNodeWithTag("warnings").fetchText(),
        )
    }

    @Test
    fun itTranslatesAgainOnEveryChange() = runRavenTest {
        setContent { TranslatorUi("<VBox>\n    <Label>Hi</Label>\n</VBox>\n") }
        assertEquals("No warnings", onNodeWithTag("warnings").fetchText())
        // Typed at the caret, the end of the text: after the root, so the text no longer parses.
        onNodeWithTag("xml").performTextInput("oops")
        assertEquals("", onNodeWithTag("code").fetchText())
        val warnings = onNodeWithTag("warnings").fetchText()
        assertTrue(warnings.startsWith("1 warning: line 4, column ") && "after the root element" in warnings, warnings)
    }
}
