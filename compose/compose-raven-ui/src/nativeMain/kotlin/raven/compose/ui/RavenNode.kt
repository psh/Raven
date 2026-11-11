package raven.compose.ui

import androidx.compose.runtime.ComposeNodeLifecycleCallback
import kotlinx.cinterop.COpaquePointer
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rwidget_destroy
import raven.cinterop.Rwidget_get_parent
import raven.cinterop.Rwidget_is_valid
import raven.native.CallbackRegistry

/** What kind of children a widget takes. Raven keeps this in private flags, so each composable states it. */
internal enum class ChildKind {
    /** Leaf widgets: Label, Slider, ... */
    None,

    /** Border, SizeBox, Button, Expander, BackgroundBlur: Raven destroys the old child on a second insert. */
    Single,

    /** Panels: VBox, HBox, Overlay, ... */
    Many,
}

/**
 * The node behind one Raven widget. The factory of a composable creates the widget, and the node
 * owns it from then on: [onRelease] destroys it, and Raven's destroy callback then disposes the
 * node's [StableRef][kotlinx.cinterop.StableRef].
 */
internal class RavenNode(
    val widget: Rid,
    val childKind: ChildKind,
) : ComposeNodeLifecycleCallback {
    /** The `user_data` of the widget's callbacks: a reference to this node. */
    val userData: COpaquePointer = CallbackRegistry.register(widget, this)

    /**
     * The node's children as Compose sees them. For a [ChildKind.Many] widget this is exactly its
     * Raven children; a [ChildKind.Single] widget has at most one of them attached in Raven.
     */
    val children: MutableList<RavenNode> = mutableListOf()

    /** True when this node's widget is alive and attached to [parent]'s widget. */
    fun isAttachedTo(parent: RavenNode): Boolean =
        Rwidget_is_valid(widget) == R_TRUE && Rwidget_get_parent(widget) == parent.widget

    override fun onRelease() {
        // Destroying an ancestor first has already destroyed this widget with it.
        if (Rwidget_is_valid(widget) == R_TRUE) Rwidget_destroy(widget)
    }

    // Phase 1 has no node reuse (no ReusableComposeNode).
    override fun onReuse() {}

    override fun onDeactivate() {}
}
