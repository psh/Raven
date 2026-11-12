package raven.sample.xml

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import raven.compose.foundation.CodeEditor
import raven.compose.foundation.Text
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.HorizontalSplitter
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.padding
import raven.compose.ui.Modifier
import raven.compose.ui.WindowState
import raven.compose.ui.singleWindowApplication
import raven.compose.ui.testTag
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp

// Raven XML on the left, the compose-raven code it translates to on the right, live.

fun main() = singleWindowApplication(state = WindowState(size = DpSize(1200.dp, 760.dp)), title = "Raven XML to Compose") {
    TranslatorUi()
}

/** The example of `docs/XML.md`, which the editor starts with. */
internal val xmlExample = """
    <Window Title="Tool" Width="800" Height="500">
        <Shortcut Keys="Ctrl+S" Action="file.save"/>
        <VBox Spacing="8" Slot.Padding="12">
            <Label TextSize="18">Settings</Label>
            <HBox Spacing="8">
                <TextInput Name="path" Placeholder="Folder..." Slot.Fill="1"/>
                <Button Text="Save" Style="Primary" OnClicked="file.save"/>
            </HBox>
            <ListView Name="files" Slot.Fill="1"/>
        </VBox>
    </Window>

""".trimIndent()

/**
 * The XML editor beside the generated code, with the warnings below. The translation reruns on
 * every change; it parses with Raven, so this runs on Raven's UI thread.
 */
@Composable
fun TranslatorUi(initialXml: String = xmlExample) {
    var xml by remember { mutableStateOf(initialXml) }
    val translation = remember(xml) { translate(xml) }
    Column(Modifier.fillMaxSize()) {
        HorizontalSplitter(Modifier.fillMaxWidth().weight(1f)) {
            CodeEditor(xml, { xml = it }, Modifier.testTag("xml"), language = "XML")
            CodeEditor(translation.code, modifier = Modifier.testTag("code"), language = "Kotlin", readOnly = true)
        }
        Text(warningsText(translation.warnings), Modifier.fillMaxWidth().padding(8.dp).testTag("warnings"))
    }
}

internal fun warningsText(warnings: List<TranslationWarning>): String = when (warnings.size) {
    0 -> "No warnings"
    1 -> "1 warning: ${warnings.single()}"
    else -> "${warnings.size} warnings:\n" + warnings.joinToString("\n")
}
