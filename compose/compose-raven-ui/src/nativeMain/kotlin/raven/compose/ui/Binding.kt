@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.Updater
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.staticCFunction
import raven.cinterop.RAccessibleRole
import raven.cinterop.RWidgetType
import raven.cinterop.R_FALSE
import raven.cinterop.R_HALIGN_CENTER
import raven.cinterop.R_VALIGN_CENTER
import raven.cinterop.Rslot_set_halign
import raven.cinterop.Rslot_set_valign
import raven.cinterop.R_ROLE_NONE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_HBOX
import raven.cinterop.R_WIDGET_OVERLAY
import raven.cinterop.Rbutton_set_clicked_callback
import raven.cinterop.Rcheckbox_is_checked
import raven.cinterop.Rcheckbox_set_changed_callback
import raven.cinterop.Rcheckbox_set_checked
import raven.cinterop.Rid
import raven.cinterop.Rslider_get_value
import raven.cinterop.Rslider_set_changed_callback
import raven.cinterop.Rslider_set_value
import raven.cinterop.Rtext_input_get_text
import raven.cinterop.Rtext_input_set_changed_callback
import raven.cinterop.Rtext_input_set_text
import raven.cinterop.Rwidget_create
import raven.cinterop.Rwidget_is_valid
import raven.cinterop.Rwidget_set_accessible_role
import raven.native.callbackTarget
import raven.native.toRavenString

// The binding layer: composables are declarations over these few protocols.

/** The panel that holds a single-child widget's content: a Row for buttons, a Box for the others. */
@InternalComposeRavenApi
public enum class ContentKind(internal val type: RWidgetType, internal val layout: PanelLayout, internal val centred: Boolean) {
    /** Centred in its widget, its children centred vertically, as a Compose Button's content is. */
    Row(R_WIDGET_HBOX, PanelLayout(LayoutAxis.Horizontal, vertical = Alignment.CenterVertically), centred = true),

    /** Filling its widget. */
    Box(R_WIDGET_OVERLAY, PanelLayout.Box, centred = false),
}

/**
 * What a composable's widget is: the composable's [name] (for warnings), its Raven [type], the
 * [children] it takes, the [content] panel a single-child widget holds its content in, its
 * accessible [role] (null keeps Raven's own), and the [events] it reports.
 */
@InternalComposeRavenApi
public class WidgetDescriptor(
    internal val name: String,
    internal val type: RWidgetType,
    internal val children: ChildKind = ChildKind.None,
    internal val content: ContentKind? = null,
    internal val role: RAccessibleRole? = null,
    internal val events: List<Event<*>> = emptyList(),
) {
    init {
        require(content == null || children == ChildKind.Single) { "only a single-child widget has a content panel" }
    }

    internal fun create(host: RavenHost): RavenNode {
        val widget = Rwidget_create(type)
        role?.let { Rwidget_set_accessible_role(widget, it) }
        return RavenNode(widget, children, host, name = name).also { node -> events.forEach { it.register(node) } }
    }
}

/** A widget property: its typed Raven setter. */
@InternalComposeRavenApi
public fun interface Property<T> {
    public fun set(widget: Rid, value: T)
}

/**
 * A kind of Raven callback: registering it points the widget's callback at one shared
 * `staticCFunction`, which calls the lambda of type [L] the node holds for it. A user change to
 * the value [marks] is marked before the lambda runs.
 */
@InternalComposeRavenApi
public class Event<L : Any> internal constructor(
    internal val marks: ControlledValue<*>? = null,
    private val registerCallback: (widget: Rid, userData: COpaquePointer) -> Unit,
) {
    internal fun register(node: RavenNode) = registerCallback(node.widget, node.userData)
}

/**
 * Calls the lambda [node][userData] holds for [event], if any, through [call]. Nothing it throws
 * crosses into C: the host's error path receives it.
 */
internal inline fun <L : Any> dispatchEvent(userData: COpaquePointer?, event: Event<L>, call: (L) -> Unit) {
    val node = userData!!.callbackTarget<RavenNode>()
    try {
        event.marks?.let { node.markUserChange(it) }
        node.handler(event)?.let(call)
    } catch (t: Throwable) {
        checkNotNull(node.host) { "an event needs a node made through the binding layer" }.reportError(t)
    }
}

/**
 * A value the user can change in the widget, which shows what the app composed (section 7): a
 * user change is checked once the app's response has been composed, and the composed value is
 * written back if the two differ. [read] and [write] are Raven's getter and setter.
 */
@InternalComposeRavenApi
public class ControlledValue<T> internal constructor(
    internal val read: (Rid) -> T,
    internal val write: (Rid, T) -> Unit,
    internal val equals: (T, T) -> Boolean = { a, b -> a == b },
)

/** One node's [ControlledValue]: what the app last composed, and when the user last changed it. */
internal class ComposedValue<T>(private val widget: Rid, private val controlled: ControlledValue<T>) {
    private var composed: T? = null
    private var hasComposed = false

    /** The apply generation when the user last changed the value. */
    var markedAt: Long = 0L

    fun compose(value: T) {
        composed = value
        hasComposed = true
        showComposed()
    }

    /** Writes the composed value back if the widget, still alive, shows something else. */
    fun showComposed() {
        if (!hasComposed || Rwidget_is_valid(widget) != R_TRUE) return
        @Suppress("UNCHECKED_CAST")
        val value = composed as T
        if (!controlled.equals(controlled.read(widget), value)) controlled.write(widget, value)
    }
}

/** Sets [property] to [value] when it changes. */
@InternalComposeRavenApi
public fun <T> Updater<RavenNode>.bind(value: T, property: Property<T>): Unit = set(value) { property.set(widget, it) }

/** Points [event] at [handler]: the callback stays registered, and a null handler does nothing. */
@InternalComposeRavenApi
public fun <L : Any> Updater<RavenNode>.bind(handler: L?, event: Event<L>): Unit = set(handler) { setHandler(event, it) }

/** Shows [value] as the composed value of [controlled], when it changes. */
@InternalComposeRavenApi
public fun <T> Updater<RavenNode>.bind(value: T, controlled: ControlledValue<T>): Unit = set(value) { compose(controlled, it) }

/** Sets how the node places its children, which places them again when it changes. */
@InternalComposeRavenApi
public fun Updater<RavenNode>.bind(layout: PanelLayout): Unit = set(layout) { panelLayout = it }

/**
 * Emits the widget [descriptor] describes, placed by [modifier], with [update] binding its
 * properties and events. A single-child widget's [content] goes into its content panel, so it may
 * emit any number of nodes.
 */
@InternalComposeRavenApi
@Composable
public fun RavenWidget(
    descriptor: WidgetDescriptor,
    modifier: Modifier,
    update: @DisallowComposableCalls Updater<RavenNode>.() -> Unit,
    content: @Composable () -> Unit = {},
) {
    val host = LocalRavenHost.current
    val contentKind = descriptor.content
    val updateWithModifier: Updater<RavenNode>.() -> Unit = {
        set(modifier) { setModifier(it) }
        update()
    }
    if (contentKind == null) {
        ComposeNode<RavenNode, RavenApplier>(factory = { descriptor.create(host) }, update = updateWithModifier, content = content)
    } else {
        ComposeNode<RavenNode, RavenApplier>(factory = { descriptor.create(host) }, update = updateWithModifier) {
            ComposeNode<RavenNode, RavenApplier>(factory = { createContentNode(contentKind, host) }, update = {}, content = content)
        }
    }
}

private fun createContentNode(kind: ContentKind, host: RavenHost): RavenNode {
    val widget = Rwidget_create(kind.type)
    Rwidget_set_accessible_role(widget, R_ROLE_NONE)
    // Set once: the node never applies a Modifier, so the slot keeps these.
    if (kind.centred) {
        Rslot_set_halign(widget, R_HALIGN_CENTER)
        Rslot_set_valign(widget, R_VALIGN_CENTER)
    }
    return RavenNode(widget, ChildKind.Many, host, isContentNode = true).also { it.panelLayout = kind.layout }
}

// The event kinds and controlled values of the phase 1 widgets.

@InternalComposeRavenApi
public val CheckboxChecked: ControlledValue<Boolean> = ControlledValue(
    read = { Rcheckbox_is_checked(it) == R_TRUE },
    write = { widget, checked -> Rcheckbox_set_checked(widget, if (checked) R_TRUE else R_FALSE) },
)

/**
 * A text input's text. It is written only when it differs from what the widget shows, so typing
 * the composed text back never moves the caret; Raven puts the caret at the end when it is written.
 */
@InternalComposeRavenApi
public val TextInputText: ControlledValue<String> = ControlledValue(
    read = { Rtext_input_get_text(it).toRavenString() },
    write = { widget, text -> Rtext_input_set_text(widget, text) },
)

/** A slider's value. Raven snaps what is written to the slider's range and step. */
@InternalComposeRavenApi
public val SliderValue: ControlledValue<Float> = ControlledValue(
    read = { Rslider_get_value(it) },
    write = { widget, value -> Rslider_set_value(widget, value) },
)

@InternalComposeRavenApi
public val ButtonClicked: Event<() -> Unit> = Event { widget, userData ->
    Rbutton_set_clicked_callback(widget, onButtonClicked, userData)
}

@InternalComposeRavenApi
public val CheckboxChanged: Event<(Boolean) -> Unit> = Event(marks = CheckboxChecked) { widget, userData ->
    Rcheckbox_set_changed_callback(widget, onCheckboxChanged, userData)
}

/**
 * A radio button's click. Raven reports a radio unchecked by checking a sibling too: that change
 * is marked, so the app's state wins, but only the radio that became checked calls back.
 */
@InternalComposeRavenApi
public val RadioClicked: Event<() -> Unit> = Event(marks = CheckboxChecked) { widget, userData ->
    Rcheckbox_set_changed_callback(widget, onRadioChanged, userData)
}

@InternalComposeRavenApi
public val TextChanged: Event<(String) -> Unit> = Event(marks = TextInputText) { widget, userData ->
    Rtext_input_set_changed_callback(widget, onTextChanged, userData)
}

@InternalComposeRavenApi
public val SliderChanged: Event<(Float) -> Unit> = Event(marks = SliderValue) { widget, userData ->
    Rslider_set_changed_callback(widget, onSliderChanged, userData)
}

private val onRadioChanged = staticCFunction { _: Rid, checked: Int, userData: COpaquePointer? ->
    dispatchEvent(userData, RadioClicked) { if (checked == R_TRUE) it() }
}

private val onButtonClicked = staticCFunction { _: Rid, userData: COpaquePointer? ->
    dispatchEvent(userData, ButtonClicked) { it() }
}

private val onCheckboxChanged = staticCFunction { _: Rid, checked: Int, userData: COpaquePointer? ->
    dispatchEvent(userData, CheckboxChanged) { it(checked == R_TRUE) }
}

private val onTextChanged = staticCFunction { _: Rid, text: CPointer<ByteVar>?, userData: COpaquePointer? ->
    dispatchEvent(userData, TextChanged) { it(text.toRavenString()) }
}

private val onSliderChanged = staticCFunction { _: Rid, value: Float, userData: COpaquePointer? ->
    dispatchEvent(userData, SliderChanged) { it(value) }
}
