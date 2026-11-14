package raven.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import raven.cinterop.ROrientation
import raven.cinterop.R_HORIZONTAL
import raven.cinterop.R_VERTICAL
import raven.cinterop.R_WIDGET_SCROLL_BOX
import raven.cinterop.R_WIDGET_SPLITTER
import raven.cinterop.Rscroll_box_set_orientation
import raven.cinterop.Rsplitter_set_orientation
import raven.compose.ui.Alignment
import raven.compose.ui.ChildKind
import raven.compose.ui.LayoutAxis
import raven.compose.ui.MainAxis
import raven.compose.ui.Modifier
import raven.compose.ui.PanelLayout
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WeightElement
import raven.compose.ui.WidgetDescriptor
import raven.compose.foundation.ScrollFollower
import raven.compose.foundation.ScrollState
import raven.compose.foundation.ScrollTarget
import raven.compose.ui.bind

/** What a splitter's panes can say about their place in it. */
public interface SplitterScope {
    /**
     * The pane's size relative to the other panes; a pane without one has 1. Dragging a handle
     * resizes the panes until the weight changes.
     */
    public fun Modifier.weight(weight: Float): Modifier = this then WeightElement(weight)
}

private object SplitterScopeInstance : SplitterScope

private val VerticalScrollWidget = WidgetDescriptor("VerticalScroll", R_WIDGET_SCROLL_BOX, ChildKind.Many)
private val HorizontalScrollWidget = WidgetDescriptor("HorizontalScroll", R_WIDGET_SCROLL_BOX, ChildKind.Many)
private val HorizontalSplitterWidget = WidgetDescriptor("HorizontalSplitter", R_WIDGET_SPLITTER, ChildKind.Many)
private val VerticalSplitterWidget = WidgetDescriptor("VerticalSplitter", R_WIDGET_SPLITTER, ChildKind.Many)

private val ScrollOrientation = Property<ROrientation> { widget, orientation -> Rscroll_box_set_orientation(widget, orientation) }
private val SplitterOrientation = Property<ROrientation> { widget, orientation -> Rsplitter_set_orientation(widget, orientation) }

/**
 * A Column that scrolls when it is taller than the space it gets. Each child keeps its own
 * height, so `Modifier.weight` is ignored here. [state], when given, holds its position.
 */
@Composable
public fun VerticalScroll(
    modifier: Modifier = Modifier,
    state: ScrollState? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    VerticalScroll(modifier, state as ScrollTarget?, verticalArrangement, horizontalAlignment, content)
}

/** A [VerticalScroll] following [target]: a [ScrollState], or a lazy list's state. */
@Composable
internal fun VerticalScroll(
    modifier: Modifier,
    target: ScrollTarget?,
    verticalArrangement: Arrangement.Vertical,
    horizontalAlignment: Alignment.Horizontal,
    content: @Composable ColumnScope.() -> Unit,
) {
    val follower = remember { ScrollFollower(vertical = true) }
    RavenWidget(VerticalScrollWidget, modifier, {
        bind(follower)
        set(target) { follower.target = it }
        bind(R_VERTICAL, ScrollOrientation)
        bind(PanelLayout(LayoutAxis.Vertical, horizontal = horizontalAlignment, mainAxis = MainAxis.Scrolling))
        bind(verticalArrangement.spacing, PanelSpacing)
    }) { ColumnScopeInstance.content() }
}

/** A Row that scrolls when it is wider than the space it gets, as [VerticalScroll] is. */
@Composable
public fun HorizontalScroll(
    modifier: Modifier = Modifier,
    state: ScrollState? = null,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit,
) {
    HorizontalScroll(modifier, state as ScrollTarget?, horizontalArrangement, verticalAlignment, content)
}

/** A [HorizontalScroll] following [target]: a [ScrollState], or a lazy list's state. */
@Composable
internal fun HorizontalScroll(
    modifier: Modifier,
    target: ScrollTarget?,
    horizontalArrangement: Arrangement.Horizontal,
    verticalAlignment: Alignment.Vertical,
    content: @Composable RowScope.() -> Unit,
) {
    val follower = remember { ScrollFollower(vertical = false) }
    RavenWidget(HorizontalScrollWidget, modifier, {
        bind(follower)
        set(target) { follower.target = it }
        bind(R_HORIZONTAL, ScrollOrientation)
        bind(PanelLayout(LayoutAxis.Horizontal, vertical = verticalAlignment, mainAxis = MainAxis.Scrolling))
        bind(horizontalArrangement.spacing, PanelSpacing)
    }) { RowScopeInstance.content() }
}

/** Panes side by side, with handles between them that the user drags. Each child is a pane it fills. */
@Composable
public fun HorizontalSplitter(modifier: Modifier = Modifier, content: @Composable SplitterScope.() -> Unit) {
    RavenWidget(HorizontalSplitterWidget, modifier, {
        bind(R_HORIZONTAL, SplitterOrientation)
        bind(PanelLayout(LayoutAxis.Horizontal, mainAxis = MainAxis.Panes))
    }) { SplitterScopeInstance.content() }
}

/** Panes stacked top to bottom, as [HorizontalSplitter] places them side by side. */
@Composable
public fun VerticalSplitter(modifier: Modifier = Modifier, content: @Composable SplitterScope.() -> Unit) {
    RavenWidget(VerticalSplitterWidget, modifier, {
        bind(R_VERTICAL, SplitterOrientation)
        bind(PanelLayout(LayoutAxis.Vertical, mainAxis = MainAxis.Panes))
    }) { SplitterScopeInstance.content() }
}
