@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.Updater
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.staticCFunction
import raven.cinterop.RAccessibleRole
import raven.cinterop.RWidgetType
import raven.cinterop.R_FALSE
import raven.cinterop.R_ROLE_NONE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_HBOX
import raven.cinterop.R_WIDGET_OVERLAY
import raven.cinterop.Rbutton_set_clicked_callback
import raven.cinterop.Rcheckbox_is_checked
import raven.cinterop.Rcheckbox_set_changed_callback
import raven.cinterop.Rcheckbox_set_checked
import raven.cinterop.Rid
import raven.cinterop.Rwidget_create
import raven.cinterop.Rwidget_is_valid
import raven.cinterop.Rwidget_set_accessible_role
import raven.native.callbackTarget

// The binding layer: composables are declarations over these few protocols.

/** The panel that holds a single-child widget's content: a Row for buttons, a Box for the others. */
internal enum class ContentKind(val type: RWidgetType) {
    Row(R_WIDGET_HBOX),
    Box(R_WIDGET_OVERLAY),
}

/**
 * What a composable's widget is: its Raven [type], the [children] it takes, the [content] panel a
 * single-child widget holds its content in, its accessible [role] (null keeps Raven's own), and
 * the [events] it reports.
 */
internal class WidgetDescriptor(
    val type: RWidgetType,
    val children: ChildKind = ChildKind.None,
    val content: ContentKind? = null,
    val role: RAccessibleRole? = null,
    val events: List<Event<*>> = emptyList(),
) {
    init {
        require(content == null || children == ChildKind.Single) { "only a single-child widget has a content panel" }
    }

    fun create(host: RavenHost): RavenNode {
        val widget = Rwidget_create(type)
        role?.let { Rwidget_set_accessible_role(widget, it) }
        return RavenNode(widget, children, host).also { node -> events.forEach { it.register(node) } }
    }
}

/** A widget property: its typed Raven setter. */
internal fun interface Property<T> {
    fun set(widget: Rid, value: T)
}

/**
 * A kind of Raven callback: registering it points the widget's callback at one shared
 * `staticCFunction`, which calls the lambda of type [L] the node holds for it. A user change to
 * the value [marks] is marked before the lambda runs.
 */
internal class Event<L : Any>(
    val marks: ControlledValue<*>? = null,
    private val registerCallback: (widget: Rid, userData: COpaquePointer) -> Unit,
) {
    fun register(node: RavenNode) = registerCallback(node.widget, node.userData)
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
internal class ControlledValue<T>(
    val read: (Rid) -> T,
    val write: (Rid, T) -> Unit,
    val equals: (T, T) -> Boolean = { a, b -> a == b },
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
internal fun <T> Updater<RavenNode>.bind(value: T, property: Property<T>) = set(value) { property.set(widget, it) }

/** Points [event] at [handler]: the callback stays registered, and a null handler does nothing. */
internal fun <L : Any> Updater<RavenNode>.bind(handler: L?, event: Event<L>) = set(handler) { setHandler(event, it) }

/** Shows [value] as the composed value of [controlled], when it changes. */
internal fun <T> Updater<RavenNode>.bind(value: T, controlled: ControlledValue<T>) = set(value) { compose(controlled, it) }

/**
 * Emits the widget [descriptor] describes, with [update] binding its properties and events. A
 * single-child widget's [content] goes into its content panel, so it may emit any number of nodes.
 */
@Composable
internal fun RavenWidget(
    descriptor: WidgetDescriptor,
    update: @DisallowComposableCalls Updater<RavenNode>.() -> Unit,
    content: @Composable () -> Unit = {},
) {
    val host = LocalRavenHost.current
    val contentKind = descriptor.content
    if (contentKind == null) {
        ComposeNode<RavenNode, RavenApplier>(factory = { descriptor.create(host) }, update = update, content = content)
    } else {
        ComposeNode<RavenNode, RavenApplier>(factory = { descriptor.create(host) }, update = update) {
            ComposeNode<RavenNode, RavenApplier>(factory = { createContentNode(contentKind, host) }, update = {}, content = content)
        }
    }
}

private fun createContentNode(kind: ContentKind, host: RavenHost): RavenNode {
    val widget = Rwidget_create(kind.type)
    Rwidget_set_accessible_role(widget, R_ROLE_NONE)
    return RavenNode(widget, ChildKind.Many, host, isContentNode = true)
}

// The event kinds and controlled values of the phase 1 widgets.

internal val CheckboxChecked = ControlledValue(
    read = { Rcheckbox_is_checked(it) == R_TRUE },
    write = { widget, checked -> Rcheckbox_set_checked(widget, if (checked) R_TRUE else R_FALSE) },
)

internal val ButtonClicked: Event<() -> Unit> = Event { widget, userData ->
    Rbutton_set_clicked_callback(widget, onButtonClicked, userData)
}

internal val CheckboxChanged: Event<(Boolean) -> Unit> = Event(marks = CheckboxChecked) { widget, userData ->
    Rcheckbox_set_changed_callback(widget, onCheckboxChanged, userData)
}

private val onButtonClicked = staticCFunction { _: Rid, userData: COpaquePointer? ->
    dispatchEvent(userData, ButtonClicked) { it() }
}

private val onCheckboxChanged = staticCFunction { _: Rid, checked: Int, userData: COpaquePointer? ->
    dispatchEvent(userData, CheckboxChanged) { it(checked == R_TRUE) }
}
