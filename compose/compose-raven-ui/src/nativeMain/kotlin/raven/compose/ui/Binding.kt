@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.Updater
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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
import raven.cinterop.R_KEY_UNKNOWN
import raven.cinterop.R_MOD_ALT
import raven.cinterop.R_MOD_CTRL
import raven.cinterop.R_MOD_SHIFT
import raven.cinterop.R_MOD_SUPER
import raven.cinterop.Rcombo_box_get_selected
import raven.cinterop.Rcombo_box_set_changed_callback
import raven.cinterop.Rcombo_box_set_selected
import raven.cinterop.Rexpander_is_expanded
import raven.cinterop.Rexpander_set_changed_callback
import raven.cinterop.Rexpander_set_expanded
import raven.cinterop.Rid
import raven.cinterop.Rlabel_set_link_callback
import raven.cinterop.Rmenu_item_is_checked
import raven.cinterop.Rmenu_item_set_checked
import raven.cinterop.Rmenu_item_set_clicked_callback
import raven.cinterop.Rmenu_item_set_shortcut
import raven.cinterop.Rslider_get_value
import raven.cinterop.Rslider_set_changed_callback
import raven.cinterop.Rslider_set_value
import raven.cinterop.Rspin_box_get_value
import raven.cinterop.Rspin_box_set_changed_callback
import raven.cinterop.Rspin_box_set_value
import raven.cinterop.Rtext_editor_get_text
import raven.cinterop.Rtext_editor_set_changed_callback
import raven.cinterop.Rtext_editor_set_completion_callback
import raven.cinterop.Rtext_editor_set_text
import raven.cinterop.Rtext_input_get_text
import raven.cinterop.Rtext_input_set_changed_callback
import raven.cinterop.Rtext_input_set_text
import raven.cinterop.Rwidget_create
import raven.cinterop.Rwidget_is_valid
import raven.cinterop.Rwidget_set_accessible_role
import raven.compose.ui.input.key.KeyShortcut
import raven.native.callbackTarget
import raven.native.toRavenString
import kotlin.math.roundToInt

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
 * What a subtree gives each of its nodes, which the node applies to its own outer widget when it
 * changes, and Raven passes on to the widgets inside it (a content node among them): the [theme]
 * of a window-level `RavenTheme`. Null follows the parent. A setting Raven gives a widget and its
 * descendants (phase 3's `fontFamily`, `Rwidget_set_font`) is one more property here.
 */
internal data class SubtreeSettings(val theme: String? = null)

/** The [SubtreeSettings] of the nodes emitted here. Each [RavenWidget] reads it. */
internal val LocalSubtreeSettings = staticCompositionLocalOf { SubtreeSettings() }

/**
 * Emits the widget [descriptor] describes, placed by [modifier], with [update] binding its
 * properties and events. A single-child widget's [content] goes into its content panel, so it may
 * emit any number of nodes. The node applies the [SubtreeSettings] it is emitted in, so no
 * composable handles them.
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
    val subtree = LocalSubtreeSettings.current
    val updateWithModifier: Updater<RavenNode>.() -> Unit = {
        set(modifier) { setModifier(it) }
        set(subtree) { setSubtreeSettings(it) }
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

/**
 * A text editor's text. As with [TextInputText], it is written only when it differs, which also
 * matters because Raven clears the editor's undo history when the text is written.
 */
@InternalComposeRavenApi
public val TextEditorText: ControlledValue<String> = ControlledValue(
    read = { Rtext_editor_get_text(it).toRavenString() },
    write = { widget, text -> Rtext_editor_set_text(widget, text) },
)

/** A slider's value. Raven snaps what is written to the slider's range and step. */
@InternalComposeRavenApi
public val SliderValue: ControlledValue<Float> = ControlledValue(
    read = { Rslider_get_value(it) },
    write = { widget, value -> Rslider_set_value(widget, value) },
)

/** A combo box's selected index, -1 when nothing is selected. Raven selects nothing for an index it doesn't have. */
@InternalComposeRavenApi
public val ComboBoxSelected: ControlledValue<Int> = ControlledValue(
    read = { Rcombo_box_get_selected(it) },
    write = { widget, index -> Rcombo_box_set_selected(widget, index) },
)

/**
 * A spin box's value. Raven clamps what is written to the spin box's range. The `Int` spin box
 * composes whole numbers into it too, so a fraction the user typed is written back rounded.
 */
@InternalComposeRavenApi
public val SpinBoxValue: ControlledValue<Double> = ControlledValue(
    read = { Rspin_box_get_value(it) },
    write = { widget, value -> Rspin_box_set_value(widget, value) },
)

/** Whether an expander shows its content. Raven animates the content open or closed when it is written. */
@InternalComposeRavenApi
public val ExpanderExpanded: ControlledValue<Boolean> = ControlledValue(
    read = { Rexpander_is_expanded(it) == R_TRUE },
    write = { widget, expanded -> Rexpander_set_expanded(widget, if (expanded) R_TRUE else R_FALSE) },
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

/** A text editor's edit. Raven's callback carries no text, so the editor's whole text is read. */
@InternalComposeRavenApi
public val TextEditorChanged: Event<(String) -> Unit> = Event(marks = TextEditorText) { widget, userData ->
    Rtext_editor_set_changed_callback(widget, onTextEditorChanged, userData)
}

@InternalComposeRavenApi
public val SliderChanged: Event<(Float) -> Unit> = Event(marks = SliderValue) { widget, userData ->
    Rslider_set_changed_callback(widget, onSliderChanged, userData)
}

@InternalComposeRavenApi
public val ComboBoxChanged: Event<(Int) -> Unit> = Event(marks = ComboBoxSelected) { widget, userData ->
    Rcombo_box_set_changed_callback(widget, onComboBoxChanged, userData)
}

@InternalComposeRavenApi
public val SpinBoxChanged: Event<(Double) -> Unit> = Event(marks = SpinBoxValue) { widget, userData ->
    Rspin_box_set_changed_callback(widget, onSpinBoxChanged, userData)
}

/** An `Int` spin box's change: the same callback, its value rounded with `roundToInt()`. */
@InternalComposeRavenApi
public val IntSpinBoxChanged: Event<(Int) -> Unit> = Event(marks = SpinBoxValue) { widget, userData ->
    Rspin_box_set_changed_callback(widget, onIntSpinBoxChanged, userData)
}

@InternalComposeRavenApi
public val ExpanderChanged: Event<(Boolean) -> Unit> = Event(marks = ExpanderExpanded) { widget, userData ->
    Rexpander_set_changed_callback(widget, onExpanderChanged, userData)
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

private val onTextEditorChanged = staticCFunction { editor: Rid, userData: COpaquePointer? ->
    dispatchEvent(userData, TextEditorChanged) { it(Rtext_editor_get_text(editor).toRavenString()) }
}

private val onSliderChanged = staticCFunction { _: Rid, value: Float, userData: COpaquePointer? ->
    dispatchEvent(userData, SliderChanged) { it(value) }
}

private val onComboBoxChanged = staticCFunction { _: Rid, index: Int, userData: COpaquePointer? ->
    dispatchEvent(userData, ComboBoxChanged) { it(index) }
}

private val onSpinBoxChanged = staticCFunction { _: Rid, value: Double, userData: COpaquePointer? ->
    dispatchEvent(userData, SpinBoxChanged) { it(value) }
}

private val onIntSpinBoxChanged = staticCFunction { _: Rid, value: Double, userData: COpaquePointer? ->
    dispatchEvent(userData, IntSpinBoxChanged) { it(value.roundToInt()) }
}

// The menus' event kinds, controlled value and shortcut.

/** A checkable menu item's check mark. */
@InternalComposeRavenApi
public val MenuItemChecked: ControlledValue<Boolean> = ControlledValue(
    read = { Rmenu_item_is_checked(it) == R_TRUE },
    write = { widget, checked -> Rmenu_item_set_checked(widget, if (checked) R_TRUE else R_FALSE) },
)

/** A menu item chosen, by a click, the keyboard or its shortcut. */
@InternalComposeRavenApi
public val MenuItemClicked: Event<() -> Unit> = Event { widget, userData ->
    Rmenu_item_set_clicked_callback(widget, onMenuItemClicked, userData)
}

/** A checkable menu item chosen: Raven has toggled its check mark already, and the lambda gets the new one. */
@InternalComposeRavenApi
public val MenuItemCheckedChanged: Event<(Boolean) -> Unit> = Event(marks = MenuItemChecked) { widget, userData ->
    Rmenu_item_set_clicked_callback(widget, onMenuItemCheckedChanged, userData)
}

/** The shortcut a menu item shows and answers to in its window; null removes it. */
@InternalComposeRavenApi
public val MenuItemShortcut: Property<KeyShortcut?> = Property { widget, shortcut ->
    var modifiers = 0u
    if (shortcut != null) {
        if (shortcut.ctrl) modifiers = modifiers or R_MOD_CTRL
        if (shortcut.shift) modifiers = modifiers or R_MOD_SHIFT
        if (shortcut.alt) modifiers = modifiers or R_MOD_ALT
        if (shortcut.meta) modifiers = modifiers or R_MOD_SUPER
    }
    Rmenu_item_set_shortcut(widget, shortcut?.key?.code ?: R_KEY_UNKNOWN, modifiers)
}

private val onMenuItemClicked = staticCFunction { _: Rid, userData: COpaquePointer? ->
    dispatchEvent(userData, MenuItemClicked) { it() }
}

private val onMenuItemCheckedChanged = staticCFunction { item: Rid, userData: COpaquePointer? ->
    dispatchEvent(userData, MenuItemCheckedChanged) { it(Rmenu_item_is_checked(item) == R_TRUE) }
}

private val onExpanderChanged = staticCFunction { _: Rid, expanded: Int, userData: COpaquePointer? ->
    dispatchEvent(userData, ExpanderChanged) { it(expanded == R_TRUE) }
}

// The links of a Markdown label.

/**
 * A link of a Markdown label clicked: the lambda gets its address. Its callback is set only while
 * the node holds a lambda, through [bindLinkClick], so it is never in a descriptor's events.
 */
@InternalComposeRavenApi
public val LabelLinkClicked: Event<(String) -> Unit> = Event { widget, userData ->
    Rlabel_set_link_callback(widget, onLabelLinkClicked, userData)
}

/**
 * Points a Markdown label's link clicks at [onLinkClick], when it changes. Null clears Raven's
 * callback, so Raven follows the link itself: an `action:` link fires its action, and an http,
 * https or mailto link opens in the user's browser or mail program.
 */
@InternalComposeRavenApi
public fun Updater<RavenNode>.bindLinkClick(onLinkClick: ((String) -> Unit)?): Unit = set(onLinkClick) {
    setHandler(LabelLinkClicked, it)
    if (it == null) Rlabel_set_link_callback(widget, null, null) else LabelLinkClicked.register(this)
}

private val onLabelLinkClicked = staticCFunction { _: Rid, url: CPointer<ByteVar>?, userData: COpaquePointer? ->
    dispatchEvent(userData, LabelLinkClicked) { it(url.toRavenString()) }
}

// Following a widget once a frame, the text editor's completions, and what a host keeps.

/**
 * Follows a node's widget, for what Raven reports no change of (a text editor's cursor): [attach]
 * when the node takes it, [follow] once a frame before the frame recomposes, and [detach] when the
 * node lets it go or is released. A node holds one at most.
 */
@InternalComposeRavenApi
public interface WidgetFollower {
    public fun attach(widget: Rid)

    public fun follow(widget: Rid)

    public fun detach(widget: Rid)
}

/** Has [follower] follow the node's widget, when it changes; null stops the one there was. */
@InternalComposeRavenApi
public fun Updater<RavenNode>.bind(follower: WidgetFollower?): Unit = set(follower) { this.follower = it }

/**
 * A text editor gathering its proposals for the word being typed: the lambda gets the editor and
 * the start of the word, and adds its own with `Rtext_editor_add_completion` before it returns.
 */
@InternalComposeRavenApi
public val TextEditorCompletion: Event<(editor: Rid, prefix: String) -> Unit> = Event { widget, userData ->
    Rtext_editor_set_completion_callback(widget, onTextEditorCompletion, userData)
}

private val onTextEditorCompletion = staticCFunction { editor: Rid, prefix: CPointer<ByteVar>?, userData: COpaquePointer? ->
    dispatchEvent(userData, TextEditorCompletion) { it(editor, prefix.toRavenString()) }
}

/**
 * What [create] makes once per host for [key], kept until the host closes: for what Raven keeps
 * until it shuts down, such as a registered language.
 */
@InternalComposeRavenApi
@Composable
public fun <T : Any> rememberPerHost(key: Any, create: () -> T): T {
    val host = LocalRavenHost.current
    return remember(host, key) { host.caches.getOrPut(key, create) }
}
