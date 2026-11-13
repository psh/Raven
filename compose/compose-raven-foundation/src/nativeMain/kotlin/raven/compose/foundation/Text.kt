package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import raven.cinterop.RTextAlign
import raven.cinterop.R_COLOR_TEXT
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_LABEL
import raven.cinterop.Rid
import raven.cinterop.Rlabel_set_color
import raven.cinterop.Rlabel_set_font
import raven.cinterop.Rlabel_set_markdown
import raven.cinterop.Rlabel_set_text
import raven.cinterop.Rlabel_set_text_align
import raven.cinterop.Rlabel_set_text_size
import raven.cinterop.Rlabel_set_wrap
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
import raven.compose.ui.text.LocalTextStyle
import raven.compose.ui.text.TextAlign
import raven.compose.ui.text.TextStyle
import raven.compose.ui.text.font.FontFamily
import raven.compose.ui.text.font.FontStyle
import raven.compose.ui.text.font.FontWeight
import raven.compose.ui.text.font.rememberRavenFont
import raven.compose.ui.text.ravenTextAlign
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

/** R_INVALID_ID is the font the label inherits: a `RavenTheme`'s, or the theme's. */
private val LabelFont = Property<Rid> { widget, font -> Rlabel_set_font(widget, font) }
private val LabelAlign = Property<RTextAlign> { widget, align -> Rlabel_set_text_align(widget, align) }
private val LabelWrap = Property<Boolean> { widget, wrap -> Rlabel_set_wrap(widget, if (wrap) R_TRUE else R_FALSE) }

/**
 * Shows [text] as [style] says, the theme's by default, with each parameter given winning over the
 * style: [color], [fontSize], the [fontFamily] in [fontWeight] and [fontStyle], and [textAlign]
 * within the Text's width.
 *
 * Unlike Compose, a Text keeps to one line unless [softWrap] is true, as Raven's labels do: then it
 * wraps on the width its parent gives it. A weight of 600 or more is bold; any other is regular.
 */
@Composable
public fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textAlign: TextAlign? = null,
    softWrap: Boolean = false,
    style: TextStyle = LocalTextStyle.current,
) {
    val merged = style.merge(TextStyle(color, fontSize, fontFamily, fontWeight, fontStyle, textAlign))
    val font = rememberRavenFont(merged.fontFamily, merged.fontWeight, merged.fontStyle)
    RavenWidget(TextWidget, modifier, {
        bind(text, LabelText)
        bind(merged.color, LabelColor)
        bind(merged.fontSize, LabelTextSize)
        bind(font, LabelFont)
        bind(ravenTextAlign(merged.textAlign), LabelAlign)
        bind(softWrap, LabelWrap)
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
 * not a Raven action: an app that wants one handles it in [onLinkClick].
 *
 * [style] gives the body text its colour, size, font and alignment. Strong and emphasised text use
 * the family's bold and italic, and headings are sized from the body by Raven's own factors.
 */
@Composable
public fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val font = rememberRavenFont(style.fontFamily, style.fontWeight, style.fontStyle)
    // The handler is read when a link is clicked, so the label's callback stays the same lambda.
    val uriHandler = rememberUpdatedState(LocalUriHandler.current)
    val openUri = remember { { url: String -> uriHandler.value.openUri(url) } }
    RavenWidget(MarkdownTextWidget, modifier, {
        bind(markdown, LabelMarkdown)
        bind(style.color, LabelColor)
        bind(style.fontSize, LabelTextSize)
        bind(font, LabelFont)
        bind(ravenTextAlign(style.textAlign), LabelAlign)
        bindLinkClick(onLinkClick ?: openUri)
    })
}
