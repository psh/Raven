package raven.compose.foundation

import androidx.compose.runtime.Composable
import raven.cinterop.R_WIDGET_LABEL
import raven.cinterop.Rlabel_set_text
import raven.cinterop.Rlabel_set_text_size
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.unit.TextUnit
import raven.compose.ui.unit.isSpecified

private val TextWidget = WidgetDescriptor("Text", R_WIDGET_LABEL)

private val LabelText = Property<String> { widget, text -> Rlabel_set_text(widget, text) }

/** 0 is Raven's "the theme's size". */
private val LabelTextSize = Property<TextUnit> { widget, size -> Rlabel_set_text_size(widget, if (size.isSpecified) size.value else 0f) }

/**
 * Shows [text], [fontSize] high, or at the theme's size when it is unspecified. Colour and font
 * arrive with theming (phase 2) and fonts (phase 3); until then the text has Raven's style.
 */
@Composable
public fun Text(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = TextUnit.Unspecified) {
    RavenWidget(TextWidget, modifier, {
        bind(text, LabelText)
        bind(fontSize, LabelTextSize)
    })
}
