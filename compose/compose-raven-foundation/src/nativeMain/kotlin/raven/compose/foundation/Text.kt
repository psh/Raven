package raven.compose.foundation

import androidx.compose.runtime.Composable
import raven.cinterop.R_WIDGET_LABEL
import raven.cinterop.Rlabel_set_text
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind

private val TextWidget = WidgetDescriptor("Text", R_WIDGET_LABEL)

private val LabelText = Property<String> { widget, text -> Rlabel_set_text(widget, text) }

/**
 * Shows [text]. Colour, size and font arrive with theming (phase 2) and fonts (phase 3); until
 * then the text has Raven's style.
 */
@Composable
public fun Text(text: String, modifier: Modifier = Modifier) {
    RavenWidget(TextWidget, modifier, { bind(text, LabelText) })
}
