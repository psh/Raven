package raven.compose.foundation.layout

import androidx.compose.runtime.Composable
import raven.cinterop.R_WIDGET_HBOX
import raven.cinterop.R_WIDGET_OVERLAY
import raven.cinterop.R_WIDGET_VBOX
import raven.cinterop.R_WIDGET_WRAP_BOX
import raven.cinterop.Rpanel_set_spacing
import raven.compose.ui.AlignElement
import raven.compose.ui.Alignment
import raven.compose.ui.ChildKind
import raven.compose.ui.LayoutAxis
import raven.compose.ui.Modifier
import raven.compose.ui.PanelLayout
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WeightElement
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.unit.Dp

/** What Column's children can say about their place in it. */
public interface ColumnScope {
    /** A share of the height left once the other children have theirs. */
    public fun Modifier.weight(weight: Float): Modifier = this then WeightElement(weight)

    /** Where the child sits across the Column, instead of the Column's `horizontalAlignment`. */
    public fun Modifier.align(alignment: Alignment.Horizontal): Modifier = this then AlignElement(alignment, null)
}

/** What Row's children can say about their place in it. */
public interface RowScope {
    /** A share of the width left once the other children have theirs. */
    public fun Modifier.weight(weight: Float): Modifier = this then WeightElement(weight)

    /** Where the child sits across the Row, instead of the Row's `verticalAlignment`. */
    public fun Modifier.align(alignment: Alignment.Vertical): Modifier = this then AlignElement(null, alignment)
}

/** What Box's children can say about their place in it. */
public interface BoxScope {
    /** Where the child sits in the Box, instead of the Box's `contentAlignment`. */
    public fun Modifier.align(alignment: Alignment): Modifier = this then AlignElement(alignment.horizontal, alignment.vertical)
}

internal object ColumnScopeInstance : ColumnScope
internal object RowScopeInstance : RowScope
private object BoxScopeInstance : BoxScope

private val ColumnWidget = WidgetDescriptor("Column", R_WIDGET_VBOX, ChildKind.Many)
private val RowWidget = WidgetDescriptor("Row", R_WIDGET_HBOX, ChildKind.Many)
private val BoxWidget = WidgetDescriptor("Box", R_WIDGET_OVERLAY, ChildKind.Many)
private val FlowRowWidget = WidgetDescriptor("FlowRow", R_WIDGET_WRAP_BOX, ChildKind.Many)

internal val PanelSpacing = Property<Dp> { widget, spacing -> Rpanel_set_spacing(widget, spacing.value) }

/**
 * Places its children top to bottom, [verticalArrangement] apart, each aligned across it by
 * [horizontalAlignment] unless its Modifier aligns it.
 */
@Composable
public fun Column(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    RavenWidget(ColumnWidget, modifier, {
        bind(PanelLayout(LayoutAxis.Vertical, horizontal = horizontalAlignment))
        bind(verticalArrangement.spacing, PanelSpacing)
    }) { ColumnScopeInstance.content() }
}

/**
 * Places its children left to right, [horizontalArrangement] apart, each aligned across it by
 * [verticalAlignment] unless its Modifier aligns it.
 */
@Composable
public fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit,
) {
    RavenWidget(RowWidget, modifier, {
        bind(PanelLayout(LayoutAxis.Horizontal, vertical = verticalAlignment))
        bind(horizontalArrangement.spacing, PanelSpacing)
    }) { RowScopeInstance.content() }
}

/** Stacks its children, each placed by [contentAlignment] unless its Modifier aligns it. */
@Composable
public fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit = {},
) {
    RavenWidget(BoxWidget, modifier, {
        bind(PanelLayout(LayoutAxis.None, contentAlignment.horizontal, contentAlignment.vertical))
    }) { BoxScopeInstance.content() }
}

/**
 * Places its children left to right, each at its own size, and goes on to a new line when the
 * width runs out. Raven has one gap for both: [horizontalArrangement]'s spacing is the gap between
 * items and the gap between lines, so there is no `verticalArrangement`.
 */
@Composable
public fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable () -> Unit,
) {
    RavenWidget(FlowRowWidget, modifier, {
        bind(horizontalArrangement.spacing, PanelSpacing)
    }, content)
}
