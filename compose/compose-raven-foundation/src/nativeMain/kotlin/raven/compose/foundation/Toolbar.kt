package raven.compose.foundation

import androidx.compose.runtime.Composable
import raven.cinterop.R_ROLE_NONE
import raven.cinterop.R_ROLE_TOOLBAR
import raven.cinterop.R_WIDGET_HBOX
import raven.cinterop.R_WIDGET_OVERLAY
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.PanelSpacing
import raven.compose.foundation.layout.RowScope
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.padding
import raven.compose.ui.Alignment
import raven.compose.ui.ChildKind
import raven.compose.ui.LayoutAxis
import raven.compose.ui.Modifier
import raven.compose.ui.PaddingValues
import raven.compose.ui.PanelLayout
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.unit.dp

/** What a [Toolbar]'s children can do: a Row's weights and alignments, and [ToolbarSeparator]. */
public interface ToolbarScope : RowScope

private object ToolbarScopeInstance : ToolbarScope

// Raven's HBox has no padding of its own, so the toolbar is a Box with the toolbar role, holding
// the HBox its children are in, padded by the contentPadding.
private val ToolbarWidget = WidgetDescriptor("Toolbar", R_WIDGET_OVERLAY, ChildKind.Many, role = R_ROLE_TOOLBAR)
private val ToolbarRowWidget = WidgetDescriptor("Toolbar", R_WIDGET_HBOX, ChildKind.Many, role = R_ROLE_NONE)

/**
 * A row of tools, which assistive technology reads as a toolbar: its children are placed left to
 * right, [horizontalArrangement] apart, each centred vertically, inside [contentPadding]. A
 * `Spacer(Modifier.weight(1f))` pushes the children after it to the end, and [ToolbarSeparator]
 * divides groups. It draws nothing itself, so it sits under a `MenuBar`, in a `HeaderBar`, or on a
 * `Surface` for a floating toolbar.
 */
@Composable
public fun Toolbar(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(6.dp, 4.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp),
    content: @Composable ToolbarScope.() -> Unit,
) {
    RavenWidget(ToolbarWidget, modifier, {}) {
        val padding = Modifier.padding(contentPadding.start, contentPadding.top, contentPadding.end, contentPadding.bottom)
        RavenWidget(ToolbarRowWidget, padding.fillMaxSize(), {
            bind(PanelLayout(LayoutAxis.Horizontal, vertical = Alignment.CenterVertically))
            bind(horizontalArrangement.spacing, PanelSpacing)
        }) { ToolbarScopeInstance.content() }
    }
}

/** A vertical line between two groups of a [Toolbar]'s children, as high as the toolbar's row. */
@Composable
public fun ToolbarScope.ToolbarSeparator(modifier: Modifier = Modifier) {
    VerticalDivider(modifier)
}
