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
 *
 * Nodes made through the binding layer know their [host], which receives what their callbacks
 * throw and checks their controlled values. A [content node][isContentNode] is the internal panel
 * that holds a single-child widget's content.
 */
@OptIn(InternalComposeRavenApi::class)
internal class RavenNode(
    val widget: Rid,
    val childKind: ChildKind,
    val host: RavenHost? = null,
    val isContentNode: Boolean = false,
) : ComposeNodeLifecycleCallback {
    /** The `user_data` of the widget's callbacks: a reference to this node. */
    val userData: COpaquePointer = CallbackRegistry.register(widget, this)

    /**
     * The node's children as Compose sees them. For a [ChildKind.Many] widget this is exactly its
     * Raven children; a [ChildKind.Single] widget has at most one of them attached in Raven.
     */
    val children: MutableList<RavenNode> = mutableListOf()

    // The lambdas of the node's events, by event kind.
    private val handlers = HashMap<Event<*>, Any>()

    // The values the app composed for the node's controlled values, by controlled value.
    private val composedValues = HashMap<ControlledValue<*>, ComposedValue<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <L : Any> handler(event: Event<L>): L? = handlers[event] as L?

    fun <L : Any> setHandler(event: Event<L>, handler: L?) {
        if (handler == null) handlers.remove(event) else handlers[event] = handler
    }

    /** Shows [value], which the app composed, unless the widget shows it already. */
    fun <T> compose(controlled: ControlledValue<T>, value: T) = composedValue(controlled).compose(value)

    /** Marks a change the user made to [controlled], to be checked against the composed value. */
    fun markUserChange(controlled: ControlledValue<*>) {
        checkNotNull(host) { "a controlled value needs a node made through the binding layer" }
            .markUserChange(composedValue(controlled))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> composedValue(controlled: ControlledValue<T>): ComposedValue<T> =
        composedValues.getOrPut(controlled) { ComposedValue(widget, controlled) } as ComposedValue<T>

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
