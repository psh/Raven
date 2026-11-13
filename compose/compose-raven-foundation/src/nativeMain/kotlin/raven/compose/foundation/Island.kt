package raven.compose.foundation

import androidx.compose.runtime.Composable
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_ISLAND
import raven.cinterop.Risland_set_attached
import raven.cinterop.Risland_set_expand_on_click
import raven.cinterop.Risland_set_expand_on_hover
import raven.cinterop.Risland_set_notch
import raven.compose.ui.ChildKind
import raven.compose.ui.ContentKind
import raven.compose.ui.IslandChanged
import raven.compose.ui.IslandExpanded
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenContentNode
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind

private val IslandWidget = WidgetDescriptor("Island", R_WIDGET_ISLAND, ChildKind.Many, events = listOf(IslandChanged))

private val ExpandOnHover = Property<Boolean> { widget, on -> Risland_set_expand_on_hover(widget, if (on) R_TRUE else R_FALSE) }
private val ExpandOnClick = Property<Boolean> { widget, on -> Risland_set_expand_on_click(widget, if (on) R_TRUE else R_FALSE) }
private val Attached = Property<Boolean> { widget, attached -> Risland_set_attached(widget, if (attached) R_TRUE else R_FALSE) }
private val Notch = Property<Boolean> { widget, notch -> Risland_set_notch(widget, if (notch) R_TRUE else R_FALSE) }

/**
 * A small dark capsule showing [folded], which unfolds into a panel showing [unfolded]: for what
 * is going on right now and the controls that go with it. In `Window(island = { Island(...) })`
 * it floats at the top of the window; anywhere else it is a widget like any other.
 *
 * It shows [expanded], which only the app changes. The pointer coming over it unfolds it and
 * leaving folds it back, with [expandOnHover], and a click unfolds or folds it, with
 * [expandOnClick]; each calls [onExpandedChange] with the new value, and if the app doesn't take
 * it, the island shows [expanded] again.
 *
 * [attached] gives it square upper corners, a notch hanging from the edge it touches. With
 * [notch], a window's island lives in the screen's camera housing, where the screen has one.
 * [folded] and [unfolded] are each stacked as in a Box.
 */
@Composable
public fun Island(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    expandOnHover: Boolean = true,
    expandOnClick: Boolean = true,
    attached: Boolean = false,
    notch: Boolean = false,
    folded: @Composable () -> Unit,
    unfolded: @Composable () -> Unit,
) {
    RavenWidget(IslandWidget, modifier, {
        bind(expandOnHover, ExpandOnHover)
        bind(expandOnClick, ExpandOnClick)
        bind(attached, Attached)
        bind(notch, Notch)
        bind(expanded, IslandExpanded)
        bind(onExpandedChange, IslandChanged)
    }) {
        // Raven's order: the first child shows folded, the second unfolded.
        RavenContentNode(ContentKind.Box, folded)
        RavenContentNode(ContentKind.Box, unfolded)
    }
}
