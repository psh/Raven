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
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.set
import kotlinx.cinterop.free
import raven.cinterop.RAccessibleRole
import raven.cinterop.RWidgetType
import raven.cinterop.R_FALSE
import raven.cinterop.R_HALIGN_CENTER
import raven.cinterop.R_VALIGN_CENTER
import raven.cinterop.Rslot_set_halign
import raven.cinterop.Rslot_set_valign
import raven.cinterop.R_ROLE_AUTO
import raven.cinterop.R_ROLE_NONE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_HBOX
import raven.cinterop.R_WIDGET_OVERLAY
import raven.cinterop.Rbutton_set_clicked_callback
import raven.cinterop.Rcheckbox_is_checked
import raven.cinterop.Rcheckbox_set_changed_callback
import raven.cinterop.Rcheckbox_set_checked
import raven.cinterop.Rcolor_picker_get_color
import raven.cinterop.Rcolor_picker_set_changed_callback
import raven.cinterop.Rcolor_picker_set_color
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
import raven.cinterop.Risland_is_expanded
import raven.cinterop.Risland_set_changed_callback
import raven.cinterop.Risland_set_expanded
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
import raven.cinterop.Rlist_view_get_selected
import raven.cinterop.Rlist_view_set_activated_callback
import raven.cinterop.Rlist_view_set_selected
import raven.cinterop.Rlist_view_set_selection_changed_callback
import raven.cinterop.Rlist_view_set_virtual_items
import raven.cinterop.Rtree_item_set_selected
import raven.cinterop.Rtree_view_clear_selection
import raven.cinterop.Rtree_view_get_selection
import raven.cinterop.Rtree_view_get_selection_count
import raven.cinterop.Rtree_view_set_activated_callback
import raven.cinterop.Rtree_view_set_context_callback
import raven.cinterop.Rtree_view_set_selection_changed_callback
import raven.cinterop.Rwidget_get_user_handle
import raven.cinterop.Rtab_view_get_active
import raven.cinterop.Rtab_view_set_active
import raven.cinterop.Rtab_view_set_changed_callback
import raven.cinterop.Rtab_view_set_close_callback
import raven.cinterop.Rdock_area_set_changed_callback
import raven.cinterop.Rdock_area_set_close_callback
import raven.cinterop.Rfile_browser_get_selection
import raven.cinterop.Rfile_browser_get_selection_count
import raven.cinterop.Rfile_browser_set_accepted_callback
import raven.cinterop.Rfile_browser_set_cancelled_callback
import raven.cinterop.Rfile_browser_set_selection_changed_callback
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.ravenColor
import raven.compose.ui.graphics.toRaven
import raven.compose.ui.input.key.KeyShortcut
import raven.compose.ui.text.font.FontFamily
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
 * accessible [role] (null keeps Raven's own), the [events] it reports, and whether it is a [popup]
 * that Raven keeps out of its parent's widget ([RavenNode.isPopup]).
 */
@InternalComposeRavenApi
public class WidgetDescriptor(
    internal val name: String,
    internal val type: RWidgetType,
    internal val children: ChildKind = ChildKind.None,
    internal val content: ContentKind? = null,
    internal val role: RAccessibleRole? = null,
    internal val events: List<Event<*>> = emptyList(),
    internal val popup: Boolean = false,
) {
    init {
        require(content == null || children == ChildKind.Single) { "only a single-child widget has a content panel" }
    }

    internal fun create(host: RavenHost): RavenNode {
        val widget = Rwidget_create(type)
        role?.let { Rwidget_set_accessible_role(widget, it) }
        return RavenNode(widget, children, host, name = name, isPopup = popup, defaultRole = role ?: R_ROLE_AUTO).also { node -> events.forEach { it.register(node) } }
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
 * and the [font] family of a window-level `RavenTheme` (`Rwidget_set_theme`, `Rwidget_set_font`).
 * Null follows the parent.
 */
internal data class SubtreeSettings(val theme: String? = null, val font: FontFamily? = null)

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

/**
 * Emits a [content node][RavenNode.isContentNode] of [kind] holding [content], for a widget whose
 * children are each a slot of its own (an Island's folded and unfolded content), where
 * [RavenWidget]'s single content panel doesn't fit.
 */
@InternalComposeRavenApi
@Composable
public fun RavenContentNode(kind: ContentKind, content: @Composable () -> Unit) {
    val host = LocalRavenHost.current
    ComposeNode<RavenNode, RavenApplier>(factory = { createContentNode(kind, host) }, update = {}, content = content)
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
 * A link of a Markdown label clicked: the lambda gets its address. Its callback is set through
 * [bindLinkClick], so it is never in a descriptor's events.
 */
@InternalComposeRavenApi
public val LabelLinkClicked: Event<(String) -> Unit> = Event { widget, userData ->
    Rlabel_set_link_callback(widget, onLabelLinkClicked, userData)
}

/**
 * Points a Markdown label's link clicks at [onLinkClick], when it changes. The callback replaces
 * Raven's own handling of every link, so an `action:` link fires no action and a web link opens
 * only if the lambda opens it.
 */
@InternalComposeRavenApi
public fun Updater<RavenNode>.bindLinkClick(onLinkClick: (String) -> Unit): Unit = set(onLinkClick) {
    setHandler(LabelLinkClicked, it)
    LabelLinkClicked.register(this)
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

// The island's controlled value and event.

/** Whether an island is unfolded. Raven doesn't call the changed callback when it is written. */
@InternalComposeRavenApi
public val IslandExpanded: ControlledValue<Boolean> = ControlledValue(
    read = { Risland_is_expanded(it) == R_TRUE },
    write = { widget, expanded -> Risland_set_expanded(widget, if (expanded) R_TRUE else R_FALSE) },
)

/** An island unfolded or folded by the user: by the pointer coming over it or leaving it, or by a click. */
@InternalComposeRavenApi
public val IslandChanged: Event<(Boolean) -> Unit> = Event(marks = IslandExpanded) { widget, userData ->
    Risland_set_changed_callback(widget, onIslandChanged, userData)
}

private val onIslandChanged = staticCFunction { _: Rid, expanded: Int, userData: COpaquePointer? ->
    dispatchEvent(userData, IslandChanged) { it(expanded == R_TRUE) }
}

// The colour picker's controlled value and event kind.

/**
 * A colour picker's colour. Raven keeps it as hue, saturation and value, so what is read back is
 * compared as the 8-bit colour it rounds to.
 */
@InternalComposeRavenApi
public val ColorPickerColor: ControlledValue<Color> = ControlledValue(
    read = { widget -> ravenColor { r, g, b, a -> Rcolor_picker_get_color(widget, r, g, b, a) } },
    write = { widget, color -> color.toRaven { r, g, b, a -> Rcolor_picker_set_color(widget, r, g, b, a) } },
)

/** A colour picker's change by the user: during a drag too, and from a popup picker's own picker. */
@InternalComposeRavenApi
public val ColorPickerChanged: Event<(Color) -> Unit> = Event(marks = ColorPickerColor) { widget, userData ->
    Rcolor_picker_set_changed_callback(widget, onColorPickerChanged, userData)
}

private val onColorPickerChanged = staticCFunction { _: Rid, r: Float, g: Float, b: Float, a: Float, userData: COpaquePointer? ->
    dispatchEvent(userData, ColorPickerChanged) { it(Color(r, g, b, a)) }
}

// The list view's and the tree view's controlled values and event kinds.

/** A list view's selected row, -1 for none. Raven selects nothing for a row it doesn't have. */
@InternalComposeRavenApi
public val ListViewSelected: ControlledValue<Int> = ControlledValue(
    read = { Rlist_view_get_selected(it) },
    write = { widget, index -> Rlist_view_set_selected(widget, index) },
)

/** A list view's rows, in virtual mode: their count, each row's text asked of [ListViewItemText]. */
@InternalComposeRavenApi
public val ListViewItems: Property<List<*>> = Property { widget, items ->
    // Set again, the rows are asked for again; Raven keeps the selection and the scroll position.
    Rlist_view_set_virtual_items(widget, items.size, onListViewItemText, Rwidget_get_user_handle(widget))
}

/** The text of a list view's row, asked as Raven paints it. */
@InternalComposeRavenApi
public val ListViewItemText: Event<(Int) -> String> = Event { _, _ -> }

@InternalComposeRavenApi
public val ListViewSelectionChanged: Event<(Int) -> Unit> = Event(marks = ListViewSelected) { widget, userData ->
    Rlist_view_set_selection_changed_callback(widget, onListViewSelectionChanged, userData)
}

/** A row activated: double-clicked, or Enter pressed on it. */
@InternalComposeRavenApi
public val ListViewActivated: Event<(Int) -> Unit> = Event { widget, userData ->
    Rlist_view_set_activated_callback(widget, onListViewActivated, userData)
}

/**
 * A tree view's selected items, as Raven's item handles: one at most unless it is multi-select.
 * Raven calls no callback when it is written.
 */
@InternalComposeRavenApi
public val TreeViewSelection: ControlledValue<Set<Rid>> = ControlledValue(
    read = { treeSelection(it) },
    write = { widget, items ->
        Rtree_view_clear_selection(widget)
        items.forEach { Rtree_item_set_selected(it, R_TRUE) }
    },
)

/** The user changed a tree view's selection: the items selected now. */
@InternalComposeRavenApi
public val TreeViewSelectionChanged: Event<(Set<Rid>) -> Unit> = Event(marks = TreeViewSelection) { widget, userData ->
    Rtree_view_set_selection_changed_callback(widget, onTreeViewSelectionChanged, userData)
}

/** An item activated: double-clicked, or Enter pressed on it. */
@InternalComposeRavenApi
public val TreeViewActivated: Event<(Rid) -> Unit> = Event { widget, userData ->
    Rtree_view_set_activated_callback(widget, onTreeViewActivated, userData)
}

/**
 * A right click on a tree view: the item under the pointer, selected first, or `R_INVALID_ID` in the
 * empty space, and where in the window, for `Rmenu_open`.
 */
@InternalComposeRavenApi
public val TreeViewContext: Event<(item: Rid, x: Float, y: Float) -> Unit> = Event { widget, userData ->
    Rtree_view_set_context_callback(widget, onTreeViewContext, userData)
}

private fun treeSelection(tree: Rid): Set<Rid> = (0 until Rtree_view_get_selection_count(tree)).mapTo(LinkedHashSet()) { Rtree_view_get_selection(tree, it) }

// The text a list view asked for last: Raven draws it at once, so it lives until the next row's.
private var lastItemText: CPointer<ByteVar>? = null

private val onListViewItemText = staticCFunction { _: Rid, index: Int, userData: COpaquePointer? -> listViewItemText(index, userData) }

private fun listViewItemText(index: Int, userData: COpaquePointer?): CPointer<ByteVar>? {
    var text = ""
    dispatchEvent(userData, ListViewItemText) { text = it(index) }
    lastItemText?.let { nativeHeap.free(it) }
    val bytes = text.encodeToByteArray()
    val copy = nativeHeap.allocArray<ByteVar>(bytes.size + 1)
    bytes.forEachIndexed { i, byte -> copy[i] = byte }
    copy[bytes.size] = 0
    lastItemText = copy
    return copy
}

private val onListViewSelectionChanged = staticCFunction { _: Rid, index: Int, userData: COpaquePointer? ->
    dispatchEvent(userData, ListViewSelectionChanged) { it(index) }
}

private val onListViewActivated = staticCFunction { _: Rid, index: Int, userData: COpaquePointer? ->
    dispatchEvent(userData, ListViewActivated) { it(index) }
}

private val onTreeViewSelectionChanged = staticCFunction { tree: Rid, _: Rid, userData: COpaquePointer? ->
    dispatchEvent(userData, TreeViewSelectionChanged) { it(treeSelection(tree)) }
}

private val onTreeViewActivated = staticCFunction { _: Rid, item: Rid, userData: COpaquePointer? ->
    dispatchEvent(userData, TreeViewActivated) { it(item) }
}

private val onTreeViewContext = staticCFunction { _: Rid, item: Rid, x: Float, y: Float, userData: COpaquePointer? ->
    dispatchEvent(userData, TreeViewContext) { it(item, x, y) }
}

// The tab view's controlled value and event kinds.

/** A tab view's active tab. Raven calls no callback when it is written. */
@InternalComposeRavenApi
public val TabViewActive: ControlledValue<Int> = ControlledValue(
    read = { Rtab_view_get_active(it) },
    write = { widget, index -> Rtab_view_set_active(widget, index) },
)

@InternalComposeRavenApi
public val TabViewChanged: Event<(Int) -> Unit> = Event(marks = TabViewActive) { widget, userData ->
    Rtab_view_set_changed_callback(widget, onTabViewChanged, userData)
}

/**
 * The user closing a tab, by its button or a middle click: the page of the tab. Raven keeps the
 * tab, so it goes only when the app leaves it out of the composition.
 */
@InternalComposeRavenApi
public val TabViewClose: Event<(page: Rid) -> Unit> = Event { widget, userData ->
    Rtab_view_set_close_callback(widget, onTabViewClose, userData)
}

private val onTabViewChanged = staticCFunction { _: Rid, index: Int, userData: COpaquePointer? ->
    dispatchEvent(userData, TabViewChanged) { it(index) }
}

private val onTabViewClose = staticCFunction { _: Rid, _: Int, page: Rid, userData: COpaquePointer? ->
    dispatchEvent(userData, TabViewClose) { it(page) }
    R_FALSE
}

// The dock area's and the file browser's event kinds.

/**
 * The user closing a panel, by its tab's button: the panel. Raven keeps it, so it goes only as the
 * app says, through the panel's `open`.
 */
@InternalComposeRavenApi
public val DockAreaClose: Event<(panel: Rid) -> Unit> = Event { widget, userData ->
    Rdock_area_set_close_callback(widget, onDockAreaClose, userData)
}

/** The user moved, resized, showed or closed a panel. */
@InternalComposeRavenApi
public val DockAreaChanged: Event<() -> Unit> = Event { widget, userData ->
    Rdock_area_set_changed_callback(widget, onDockAreaChanged, userData)
}

/** The user chose: the paths chosen, the first being Raven's `path`. */
@InternalComposeRavenApi
public val FileBrowserAccepted: Event<(List<String>) -> Unit> = Event { widget, userData ->
    Rfile_browser_set_accepted_callback(widget, onFileBrowserAccepted, userData)
}

@InternalComposeRavenApi
public val FileBrowserCancelled: Event<() -> Unit> = Event { widget, userData ->
    Rfile_browser_set_cancelled_callback(widget, onFileBrowserCancelled, userData)
}

/** The selection changed: the paths selected now. */
@InternalComposeRavenApi
public val FileBrowserSelectionChanged: Event<(List<String>) -> Unit> = Event { widget, userData ->
    Rfile_browser_set_selection_changed_callback(widget, onFileBrowserSelectionChanged, userData)
}

private fun fileBrowserSelection(browser: Rid): List<String> =
    List(Rfile_browser_get_selection_count(browser)) { Rfile_browser_get_selection(browser, it).toRavenString() }

private val onDockAreaClose = staticCFunction { _: Rid, panel: Rid, userData: COpaquePointer? ->
    dispatchEvent(userData, DockAreaClose) { it(panel) }
    R_FALSE
}

private val onDockAreaChanged = staticCFunction { _: Rid, userData: COpaquePointer? ->
    dispatchEvent(userData, DockAreaChanged) { it() }
}

private val onFileBrowserAccepted = staticCFunction { browser: Rid, path: CPointer<ByteVar>?, userData: COpaquePointer? ->
    dispatchEvent(userData, FileBrowserAccepted) {
        // A name typed to save is the path alone; several files chosen are the selection.
        val chosen = path.toRavenString()
        val selection = fileBrowserSelection(browser)
        it(if (selection.size > 1 && chosen in selection) listOf(chosen) + (selection - chosen) else listOf(chosen))
    }
}

private val onFileBrowserCancelled = staticCFunction { _: Rid, _: CPointer<ByteVar>?, userData: COpaquePointer? ->
    dispatchEvent(userData, FileBrowserCancelled) { it() }
}

private val onFileBrowserSelectionChanged = staticCFunction { browser: Rid, _: CPointer<ByteVar>?, userData: COpaquePointer? ->
    dispatchEvent(userData, FileBrowserSelectionChanged) { it(fileBrowserSelection(browser)) }
}
