package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import raven.cinterop.R_COLOR_TEXT
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_LABEL
import raven.cinterop.Rlabel_set_color
import raven.cinterop.Rlabel_set_markdown
import raven.cinterop.Rlabel_set_text
import raven.cinterop.Rlabel_set_text_size
import raven.cinterop.Rwidget_clear_color
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.bindLinkClick
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.isSpecified
import raven.compose.ui.graphics.toRaven
import raven.compose.ui.platform.LocalUriHandler
import raven.compose.ui.unit.TextUnit
import raven.compose.ui.unit.isSpecified

private val TextWidget = WidgetDescriptor("Text", R_WIDGET_LABEL)

private val LabelText = Property<String> { widget, text -> Rlabel_set_text(widget, text) }

/** Unspecified clears the label's own colour, so the theme's shows again. */
private val LabelColor = Property<Color> { widget, color ->
    if (color.isSpecified) color.toRaven { r, g, b, a -> Rlabel_set_color(widget, r, g, b, a) } else Rwidget_clear_color(widget, R_COLOR_TEXT)
}

/** 0 is Raven's "the theme's size". */
private val LabelTextSize = Property<TextUnit> { widget, size -> Rlabel_set_text_size(widget, if (size.isSpecified) size.value else 0f) }

/**
 * Shows [text] in [color], [fontSize] high; either one unspecified is the theme's. The font
 * arrives with fonts (phase 3); until then the text has Raven's.
 */
@Composable
public fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    RavenWidget(TextWidget, modifier, {
        bind(text, LabelText)
        bind(color, LabelColor)
        bind(fontSize, LabelTextSize)
    })
}

private val MarkdownTextWidget = WidgetDescriptor("MarkdownText", R_WIDGET_LABEL)

/** Markdown is switched on with the first text; Raven does nothing when it is on already. */
private val LabelMarkdown = Property<String> { widget, markdown ->
    Rlabel_set_markdown(widget, R_TRUE)
    Rlabel_set_text(widget, markdown)
}

/**
 * Shows [markdown] drawn as Raven draws Markdown: headings, emphasis, code, lists, quotes, tables
 * and links, in the theme's colours, wrapped on the label's width. A link clicked calls
 * [onLinkClick] with its address; without one, [LocalUriHandler] opens it, by default in the
 * user's browser or mail program. Raven's own link handling is never used, so an `action:` link is
 * not a Raven action: an app that wants one handles it in [onLinkClick]. The `style` arrives with
 * `TextStyle` (phase 3); until then the text has the theme's.
 */
@Composable
public fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
) {
    // The handler is read when a link is clicked, so the label's callback stays the same lambda.
    val uriHandler = rememberUpdatedState(LocalUriHandler.current)
    val openUri = remember { { url: String -> uriHandler.value.openUri(url) } }
    RavenWidget(MarkdownTextWidget, modifier, {
        bind(markdown, LabelMarkdown)
        bindLinkClick(onLinkClick ?: openUri)
    })
}
