package raven.compose.foundation

import androidx.compose.runtime.Composable
import raven.cinterop.ROrientation
import raven.cinterop.R_HORIZONTAL
import raven.cinterop.R_VERTICAL
import raven.cinterop.R_WIDGET_PROGRESSBAR
import raven.cinterop.R_WIDGET_SEPARATOR
import raven.cinterop.Rprogress_bar_set_value
import raven.cinterop.Rseparator_set_orientation
import raven.compose.foundation.layout.fillMaxHeight
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind

private val HorizontalDividerWidget = WidgetDescriptor("HorizontalDivider", R_WIDGET_SEPARATOR)
private val VerticalDividerWidget = WidgetDescriptor("VerticalDivider", R_WIDGET_SEPARATOR)
private val ProgressWidget = WidgetDescriptor("LinearProgressIndicator", R_WIDGET_PROGRESSBAR)

private val Orientation = Property<ROrientation> { widget, orientation -> Rseparator_set_orientation(widget, orientation) }
private val Progress = Property<Float> { widget, progress -> Rprogress_bar_set_value(widget, progress) }

/** A horizontal line across the parent's width. */
@Composable
public fun HorizontalDivider(modifier: Modifier = Modifier) {
    RavenWidget(HorizontalDividerWidget, modifier.fillMaxWidth(), { bind(R_HORIZONTAL, Orientation) })
}

/** A vertical line down the parent's height. */
@Composable
public fun VerticalDivider(modifier: Modifier = Modifier) {
    RavenWidget(VerticalDividerWidget, modifier.fillMaxHeight(), { bind(R_VERTICAL, Orientation) })
}

/** A bar filled to [progress], from 0 to 1. */
@Composable
public fun LinearProgressIndicator(progress: Float, modifier: Modifier = Modifier) {
    RavenWidget(ProgressWidget, modifier, { bind(progress.coerceIn(0f, 1f), Progress) })
}
