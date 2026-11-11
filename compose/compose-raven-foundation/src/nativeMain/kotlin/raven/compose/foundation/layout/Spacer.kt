package raven.compose.foundation.layout

import androidx.compose.runtime.Composable
import raven.cinterop.R_WIDGET_SPACER
import raven.compose.ui.Modifier
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor

private val SpacerWidget = WidgetDescriptor("Spacer", R_WIDGET_SPACER)

/** Empty space, sized by [modifier]: `Spacer(Modifier.height(8.dp))`, or `weight(1f)` in a Row. */
@Composable
public fun Spacer(modifier: Modifier) {
    RavenWidget(SpacerWidget, modifier, {})
}
