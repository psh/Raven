@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import androidx.compose.runtime.ComposeNodeLifecycleCallback
import kotlinx.cinterop.COpaquePointer
import raven.cinterop.REvent
import raven.cinterop.RCursor
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rwidget_destroy
import raven.cinterop.Rwidget_get_parent
import raven.cinterop.Rwidget_is_valid
import raven.cinterop.Rwidget_set_cursor
import raven.cinterop.Rwidget_set_event_callback
import raven.cinterop.Rwidget_set_focusable
import raven.cinterop.Rwidget_set_hit_testable
import raven.cinterop.Rwidget_set_material
import raven.cinterop.Rwidget_set_tooltip
import raven.compose.ui.graphics.Material
import raven.compose.ui.graphics.toRaven
import raven.compose.ui.input.pointer.PointerButton
import raven.native.CallbackRegistry
import kotlin.time.Duration

/** What kind of children a widget takes. Raven keeps this in private flags, so each composable states it. */
@InternalComposeRavenApi
public enum class ChildKind {
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
 * that holds a single-child widget's content. A [popup][isPopup] (a `DropdownMenu`'s Menu) is a
 * child of its parent in Compose only: Raven keeps it out of the parent's widget, and opens it as a
 * popup.
 */
@InternalComposeRavenApi
public class RavenNode internal constructor(
    public val widget: Rid,
    internal val childKind: ChildKind,
    internal val host: RavenHost? = null,
    public val isContentNode: Boolean = false,
    /** The composable's name, for warnings. */
    internal val name: String = "a widget",
    public val isPopup: Boolean = false,
) : ComposeNodeLifecycleCallback {
    /** The `user_data` of the widget's callbacks: a reference to this node. */
    internal val userData: COpaquePointer = CallbackRegistry.register(widget, this)

    /** The node this one is inserted into, which places it. */
    internal var parent: RavenNode? = null

    /** How this node places its children. Changing it places them again. */
    internal var panelLayout: PanelLayout = PanelLayout.Box
        set(value) {
            if (value == field) return
            field = value
            children.forEach { it.applyLayout() }
        }

    private var modifier = FoldedModifier()

    // What Raven was last told about this widget's slot and size.
    private var appliedLayout = SlotLayout.RavenDefault

    /** The tag of `Modifier.testTag`, for test finders. */
    public val testTag: String? get() = modifier.testTag

    internal fun setModifier(value: Modifier) {
        val previous = modifier
        modifier = fold(value)
        if (modifier.tooltip != previous.tooltip) Rwidget_set_tooltip(widget, modifier.tooltip)
        // A material left out of the chain is the theme's again.
        if (modifier.material != previous.material) Rwidget_set_material(widget, (modifier.material ?: Material.Default).toRaven())
        if (modifier.appearTransition != previous.appearTransition) setAppearTransition(modifier.appearTransition)
        if (modifier.alpha != previous.alpha) drawCalls.setAlpha(widget, modifier.alpha)
        if (modifier.clipToBounds != previous.clipToBounds) drawCalls.setClipping(widget, modifier.clipToBounds)
        applyInput(previous)
        applyLayout()
    }

    /**
     * Registers Raven's event callback while the chain has an input element, and gives Raven the
     * pointer icon and focusability that changed. Raven has no getters for a widget's own cursor,
     * focusability and hit testing, so a chain that drops them leaves the widget with its parent's
     * cursor and no focus, and still taking the pointer.
     */
    private fun applyInput(previous: FoldedModifier) {
        val handlesInput = modifier.inputs.isNotEmpty()
        if (handlesInput != previous.inputs.isNotEmpty()) {
            Rwidget_set_event_callback(widget, if (handlesInput) onWidgetInput else null, userData)
            if (!handlesInput) primaryPressed = false
        }
        // Panels and labels let the pointer through to what is under them. One that handles input,
        // or that a click can focus, takes it.
        val takesPointer = handlesInput || modifier.focusable == true
        if (takesPointer && !(previous.inputs.isNotEmpty() || previous.focusable == true)) Rwidget_set_hit_testable(widget, R_TRUE)
        if (modifier.pointerIcon != previous.pointerIcon) Rwidget_set_cursor(widget, modifier.pointerIcon?.cursor ?: NO_CURSOR)
        if (modifier.focusable != previous.focusable) Rwidget_set_focusable(widget, if (modifier.focusable == true) R_TRUE else R_FALSE)
        if (modifier.focusRequesters != previous.focusRequesters) {
            previous.focusRequesters.forEach { it.detach(widget) }
            modifier.focusRequesters.forEach { it.attach(widget) }
        }
    }

    // Whether the primary button went down over the widget, and hasn't come up or left it since.
    private var primaryPressed = false

    /**
     * Hands a Raven event to the input elements, in chain order, until one consumes it. Returns
     * whether one did.
     */
    internal fun dispatchInput(raw: REvent): Boolean {
        val kind = raw.inputKind() ?: return false
        val primary = raw.pointerButton() == PointerButton.Primary
        var completesClick = false
        when (kind) {
            InputKind.Press -> if (primary) primaryPressed = true
            InputKind.Release -> if (primary) {
                completesClick = primaryPressed
                primaryPressed = false
            }
            InputKind.Exit -> primaryPressed = false
            else -> {}
        }
        val event = inputEvent(raw, kind, completesClick)
        return modifier.inputs.any { it.onInput(event) }
    }

    // The subtree settings this node last applied: none, outside a themed subtree.
    private var subtreeSettings = SubtreeSettings()

    /** Applies the [SubtreeSettings] that changed to the widget, which passes them on to its descendants. */
    internal fun setSubtreeSettings(value: SubtreeSettings) {
        val previous = subtreeSettings
        subtreeSettings = value
        if (value.theme != previous.theme) {
            checkNotNull(host) { "a subtree setting needs a node made through the binding layer" }.theme.setWidgetTheme(widget, value.theme)
        }
    }

    /**
     * Raven plays a transition as soon as it is set. The first is set before the insert, which plays
     * it again as the widget appears; a later one, on a widget that has appeared, must not replay.
     */
    private fun setAppearTransition(appear: AppearTransitionElement?) {
        appearCalls.set(widget, appear?.transition, appear?.delay ?: Duration.ZERO)
        if (appear != null && parent != null) appearCalls.stop(widget)
    }

    /**
     * Tells Raven how the parent places this widget, calling only the setters whose values changed.
     * A content node keeps Raven's defaults, so it fills the widget that holds it.
     */
    internal fun applyLayout() {
        val parent = parent ?: return
        if (isContentNode) return
        if (isPopup) {
            if (resolve(modifier, PanelLayout.Box).slot != resolve(FoldedModifier(), PanelLayout.Box).slot) warnOnce("$name: Raven places a popup itself, so its size and placement modifiers are ignored")
            return
        }
        val resolved = resolve(modifier, parent.panelLayout)
        resolved.warnings.forEach { warnOnce("$name: $it, so it is ignored") }
        resolved.slot.applyChanges(appliedLayout, RavenSlotTarget(widget))
        appliedLayout = resolved.slot
    }

    /**
     * The node's children as Compose sees them. For a [ChildKind.Many] widget these are its Raven
     * children, in Raven's order, and its [popups][isPopup]; a [ChildKind.Single] widget has at most
     * one of them attached in Raven.
     */
    public val children: MutableList<RavenNode> = mutableListOf()

    // The lambdas of the node's events, by event kind.
    private val handlers = HashMap<Event<*>, Any>()

    // The values the app composed for the node's controlled values, by controlled value.
    private val composedValues = HashMap<ControlledValue<*>, ComposedValue<*>>()

    @Suppress("UNCHECKED_CAST")
    internal fun <L : Any> handler(event: Event<L>): L? = handlers[event] as L?

    internal fun <L : Any> setHandler(event: Event<L>, handler: L?) {
        if (handler == null) handlers.remove(event) else handlers[event] = handler
    }

    /** Shows [value], which the app composed, unless the widget shows it already. */
    internal fun <T> compose(controlled: ControlledValue<T>, value: T) = composedValue(controlled).compose(value)

    /** Marks a change the user made to [controlled], to be checked against the composed value. */
    internal fun markUserChange(controlled: ControlledValue<*>) {
        checkNotNull(host) { "a controlled value needs a node made through the binding layer" }
            .markUserChange(composedValue(controlled))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> composedValue(controlled: ControlledValue<T>): ComposedValue<T> =
        composedValues.getOrPut(controlled) { ComposedValue(widget, controlled) } as ComposedValue<T>

    /** What follows the widget once a frame, attached while the node holds it. */
    internal var follower: WidgetFollower? = null
        set(value) {
            if (value === field) return
            val host = checkNotNull(host) { "a follower needs a node made through the binding layer" }
            field?.detach(widget)
            field = value
            value?.attach(widget)
            if (value == null) host.followedNodes -= this else host.followedNodes += this
        }

    /** True when this node's widget is alive and attached to [parent]'s widget. */
    internal fun isAttachedTo(parent: RavenNode): Boolean =
        Rwidget_is_valid(widget) == R_TRUE && Rwidget_get_parent(widget) == parent.widget

    override fun onRelease() {
        follower = null
        modifier.focusRequesters.forEach { it.detach(widget) }
        // Destroying an ancestor first has already destroyed this widget with it.
        if (Rwidget_is_valid(widget) == R_TRUE) Rwidget_destroy(widget)
    }

    // Phase 1 has no node reuse (no ReusableComposeNode).
    override fun onReuse() {}

    override fun onDeactivate() {}
}

/** `Rwidget_set_cursor`'s "no cursor of its own": the parent's shows. */
private val NO_CURSOR: RCursor = 0u
