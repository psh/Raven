package raven.compose.foundation

import androidx.compose.runtime.Composable
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_TEXT_EDITOR
import raven.cinterop.Rtext_editor_set_language
import raven.cinterop.Rtext_editor_set_line_numbers
import raven.cinterop.Rtext_editor_set_read_only
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.TextEditorChanged
import raven.compose.ui.TextEditorText
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind

private val CodeEditorWidget = WidgetDescriptor("CodeEditor", R_WIDGET_TEXT_EDITOR, events = listOf(TextEditorChanged))

/**
 * Raven keeps the old language when it doesn't know a name, and warns. Clearing it first makes
 * an unknown name plain text instead.
 */
private val Language = Property<String?> { widget, name ->
    Rtext_editor_set_language(widget, null)
    if (!name.isNullOrEmpty()) Rtext_editor_set_language(widget, name)
}
private val EditorReadOnly = Property<Boolean> { widget, readOnly -> Rtext_editor_set_read_only(widget, if (readOnly) R_TRUE else R_FALSE) }
private val LineNumbers = Property<Boolean> { widget, shown -> Rtext_editor_set_line_numbers(widget, if (shown) R_TRUE else R_FALSE) }

/**
 * A multi-line editor showing [value], controlled as [TextField] is: each edit calls
 * [onValueChange] with the whole text. Text the app rewrites moves the caret to the end and clears
 * the editor's undo history.
 *
 * [language] is one of Raven's languages, by name ("Kotlin", "XML"), usual short name ("js") or
 * file extension ("rs"). An unknown name shows plain text, and Raven warns.
 */
@Composable
public fun CodeEditor(
    value: String,
    onValueChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    language: String? = null,
    readOnly: Boolean = false,
    lineNumbers: Boolean = true,
) {
    RavenWidget(CodeEditorWidget, modifier, {
        bind(language, Language)
        bind(readOnly, EditorReadOnly)
        bind(lineNumbers, LineNumbers)
        bind(value, TextEditorText)
        bind(onValueChange, TextEditorChanged)
    })
}
