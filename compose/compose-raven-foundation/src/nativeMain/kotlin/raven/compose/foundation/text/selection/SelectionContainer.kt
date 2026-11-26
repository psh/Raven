package raven.compose.foundation.text.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import raven.compose.foundation.layout.Box
import raven.compose.ui.Modifier

/** Whether a `Text` or `MarkdownText` composed here is selectable: true inside a [SelectionContainer]. */
internal val LocalTextSelectable: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/**
 * Makes the `Text` and `MarkdownText` in [content] selectable with the mouse (`Rlabel_set_selectable`):
 * drag to select, double click for a word, triple click for a paragraph, Shift + click to extend,
 * then Ctrl+C (Cmd+C on macOS) copies. A Markdown text copies without its marks, and its links are
 * still clicked. [content] is stacked as in a `Box`, as Compose's is.
 *
 * Unlike Compose's, each text is selected on its own: a selection doesn't run from one `Text` into
 * the next, and Ctrl+A selects the text that has the focus. A selectable text takes the mouse, so
 * a click on it no longer reaches a `clickable` around it.
 */
@Composable
public fun SelectionContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        CompositionLocalProvider(LocalTextSelectable provides true, content = content)
    }
}

/** Makes the texts in [content] not selectable again, inside a [SelectionContainer]. It lays nothing out. */
@Composable
public fun DisableSelection(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTextSelectable provides false, content = content)
}
