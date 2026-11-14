package raven.compose.ui.test

import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import raven.cinterop.RKey
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_FALSE
import raven.cinterop.R_KEY_DELETE
import raven.cinterop.R_KEY_DOWN
import raven.cinterop.R_KEY_END
import raven.cinterop.R_KEY_ENTER
import raven.cinterop.R_KEY_HOME
import raven.cinterop.R_METRIC_ROW_HEIGHT
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_MOUSE_RIGHT
import raven.cinterop.R_ROLE_SEPARATOR
import raven.cinterop.R_TRUE
import raven.cinterop.R_VISIBLE
import raven.cinterop.R_WIDGET_CHECKBOX
import raven.cinterop.R_WIDGET_COLOR_PICKER
import raven.cinterop.R_WIDGET_COMBO_BOX
import raven.cinterop.R_WIDGET_LIST_VIEW
import raven.cinterop.R_WIDGET_MENU
import raven.cinterop.R_WIDGET_MENU_BAR
import raven.cinterop.R_WIDGET_MENU_ITEM
import raven.cinterop.R_WIDGET_PROGRESSBAR
import raven.cinterop.R_WIDGET_SCROLL_BOX
import raven.cinterop.R_WIDGET_SLIDER
import raven.cinterop.R_WIDGET_SPIN_BOX
import raven.cinterop.R_WIDGET_TEXT_EDITOR
import raven.cinterop.R_WIDGET_TEXT_INPUT
import raven.cinterop.Rcheckbox_is_checked
import raven.cinterop.Rcombo_box_get_item_count
import raven.cinterop.Rcombo_box_get_selected
import raven.cinterop.Rid
import raven.cinterop.Rlist_view_get_item_count
import raven.cinterop.Rlist_view_scroll_to_item
import raven.cinterop.Rmenu_item_is_checked
import raven.cinterop.Rmenu_open
import raven.cinterop.Rprogress_bar_get_value
import raven.cinterop.Rscroll_box_get_offset
import raven.cinterop.Rscroll_box_set_offset
import raven.cinterop.Rslider_get_value
import raven.cinterop.Rspin_box_get_value
import raven.cinterop.Rtext_editor_get_text
import raven.cinterop.Rtext_input_get_text
import raven.cinterop.Rtext_input_select_all
import raven.cinterop.Rtext_input_set_text
import raven.cinterop.Rwidget_focus
import raven.cinterop.Rwidget_get_accessible_role
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_metric
import raven.cinterop.Rwidget_get_parent
import raven.cinterop.Rwidget_get_rect
import raven.cinterop.Rwidget_get_type
import raven.cinterop.Rwidget_get_visibility
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwidget_is_enabled
import raven.cinterop.Rwidget_is_focused
import raven.cinterop.Rwidget_scroll_into_view
import raven.cinterop.Rwindow_close_popups
import raven.cinterop.Rwindow_get_popup
import raven.cinterop.Rwindow_get_popup_count
import raven.cinterop.Rwindow_inject_key
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.cinterop.Rwindow_inject_text
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.RavenNode
import raven.compose.ui.graphics.Color
import raven.compose.ui.input.key.KeyShortcut
import raven.native.toRavenString
import kotlin.test.fail

/**
 * One node a finder selects: the one [matcher] accepts, or the one at [index] of all it accepts.
 * Every use waits for the UI to be idle, then finds it again.
 */
public class NodeInteraction internal constructor(
    private val scope: RavenTestScope,
    private val matcher: NodeMatcher,
    private val index: Int? = null,
) {
    private val selector: String = if (index == null) matcher.description else "${matcher.description} [$index]"

    /** The one node that matches; fails if there is none, or more than one. */
    @InternalComposeRavenApi
    public fun fetchNode(): RavenNode {
        scope.waitForIdle()
        val found = scope.findNodes(matcher.matches)
        if (index != null) return found.getOrNull(index) ?: fail("${found.size} nodes with $selector, so none at $index")
        if (found.isEmpty()) fail("no node with $selector")
        if (found.size > 1) fail("${found.size} nodes with $selector, expected one")
        return found.single()
    }

    /** The node's Raven widget. */
    @InternalComposeRavenApi
    public fun fetchWidget(): Rid = fetchNode().widget

    public fun assertExists(): NodeInteraction = apply { fetchNode() }

    public fun assertDoesNotExist(): NodeInteraction = apply {
        scope.waitForIdle()
        val found = scope.findNodes(matcher.matches)
        val exists = if (index == null) found.isNotEmpty() else found.size > index
        if (exists) fail("${found.size} nodes with $selector, expected none")
    }

    /**
     * Fails unless the node's widget can be seen: shown, as are its ancestors, with a size, and
     * not scrolled or clipped out of any of them.
     */
    public fun assertIsDisplayed(): NodeInteraction = apply {
        val widget = fetchWidget()
        val rect = widgetRect(widget)
        if (rect.width <= 0f || rect.height <= 0f) fail("the node with $selector has no size")
        var current = widget
        while (current != R_INVALID_ID) {
            if (Rwidget_get_visibility(current) != R_VISIBLE) fail("the node with $selector is hidden")
            if (current != widget && !rect.overlaps(widgetRect(current))) fail("the node with $selector is out of view")
            current = Rwidget_get_parent(current)
        }
    }

    /** The text the node shows: a Text's, a text field's, a code editor's or a menu item's. */
    public fun fetchText(): String {
        val node = fetchNode()
        return labelText(node) ?: menuItemText(node) ?: when (Rwidget_get_type(node.widget)) {
            R_WIDGET_TEXT_INPUT -> Rtext_input_get_text(node.widget).toRavenString()
            R_WIDGET_TEXT_EDITOR -> Rtext_editor_get_text(node.widget).toRavenString()
            else -> fail("the node with $selector shows no text")
        }
    }

    /** Fails unless the node shows [expected], as [fetchText] reads it. */
    public fun assertTextEquals(expected: String): NodeInteraction = apply {
        val text = fetchText()
        if (text != expected) fail("the node with $selector shows \"$text\", expected \"$expected\"")
    }

    /** Fails unless [matcher] accepts the node. */
    public fun assert(matcher: NodeMatcher): NodeInteraction = apply {
        if (!matcher.matches(fetchNode())) fail("the node with $selector is not ${matcher.description}")
    }

    public fun assertIsChecked(): NodeInteraction = apply { assertChecked(true) }

    public fun assertIsNotChecked(): NodeInteraction = apply { assertChecked(false) }

    private fun assertChecked(expected: Boolean) {
        val widget = fetchWidget()
        val checked = when (Rwidget_get_type(widget)) {
            R_WIDGET_CHECKBOX -> Rcheckbox_is_checked(widget) == R_TRUE
            R_WIDGET_MENU_ITEM -> Rmenu_item_is_checked(widget) == R_TRUE
            else -> fail("the node with $selector can't be checked")
        }
        if (checked != expected) fail("the node with $selector is ${if (checked) "checked" else "not checked"}")
    }

    public fun assertIsEnabled(): NodeInteraction = apply { assertEnabled(true) }

    public fun assertIsNotEnabled(): NodeInteraction = apply { assertEnabled(false) }

    private fun assertEnabled(expected: Boolean) {
        val enabled = Rwidget_is_enabled(fetchWidget()) == R_TRUE
        if (enabled != expected) fail("the node with $selector is ${if (enabled) "enabled" else "not enabled"}")
    }

    public fun assertIsFocused(): NodeInteraction = apply { assertFocused(true) }

    public fun assertIsNotFocused(): NodeInteraction = apply { assertFocused(false) }

    private fun assertFocused(expected: Boolean) {
        val focused = Rwidget_is_focused(fetchWidget()) == R_TRUE
        if (focused != expected) fail("the node with $selector is ${if (focused) "focused" else "not focused"}")
    }

    /** Fails unless the node is a combo box with the item at [expected] selected, or none at -1. */
    public fun assertSelectedIndex(expected: Int): NodeInteraction = apply {
        val selected = Rcombo_box_get_selected(comboBox())
        if (selected != expected) fail("the node with $selector has item $selected selected, expected $expected")
    }

    /** Fails unless the node is a slider, a spin box or a progress bar holding [expected]. */
    public fun assertValueEquals(expected: Double): NodeInteraction = apply {
        val widget = fetchWidget()
        val value = when (Rwidget_get_type(widget)) {
            R_WIDGET_SPIN_BOX -> Rspin_box_get_value(widget)
            R_WIDGET_SLIDER -> Rslider_get_value(widget).toDouble()
            R_WIDGET_PROGRESSBAR -> Rprogress_bar_get_value(widget).toDouble()
            else -> fail("the node with $selector has no number value")
        }
        if (value != expected) fail("the node with $selector holds $value, expected $expected")
    }

    /**
     * Picks the item at [index] of the node's combo box as the user would with the keyboard: a
     * click opens its list, Home and Down move to the item, and Enter picks it.
     */
    public fun performSelection(index: Int): NodeInteraction = apply {
        val widget = comboBox()
        val count = Rcombo_box_get_item_count(widget)
        if (index !in 0 until count) fail("the node with $selector has $count items, so none at $index")
        click(widget)
        press(widget, R_KEY_HOME)
        repeat(index) { press(widget, R_KEY_DOWN) }
        press(widget, R_KEY_ENTER)
    }

    /**
     * Types [value] into the node's spin box as the user would: a click starts typing over the
     * value, and Enter takes what was typed.
     */
    public fun performValueInput(value: Double): NodeInteraction = apply {
        val widget = fetchWidget()
        if (Rwidget_get_type(widget) != R_WIDGET_SPIN_BOX) fail("the node with $selector takes no number")
        click(widget)
        Rwindow_inject_text(Rwidget_get_window(widget), value.toString())
        press(widget, R_KEY_ENTER)
    }

    private fun comboBox(): Rid = fetchWidget().also {
        if (Rwidget_get_type(it) != R_WIDGET_COMBO_BOX) fail("the node with $selector is not a combo box")
    }

    /**
     * Presses and releases the left button at the centre of the node's widget, as the user would.
     * A menu item is chosen from its menu: see [chooseMenuItem].
     */
    public fun performClick(): NodeInteraction = apply {
        val node = fetchNode()
        if (Rwidget_get_type(node.widget) == R_WIDGET_MENU_ITEM) chooseMenuItem(node) else click(node.widget)
    }

    /**
     * Types [text] into the node's widget, one character at a time with no frame between them. It
     * clicks the widget first unless it already has focus, so the caret stays where it was.
     */
    public fun performTextInput(text: String): NodeInteraction = apply {
        val widget = fetchWidget()
        if (Rwidget_is_focused(widget) != R_TRUE) click(widget)
        Rwindow_inject_text(Rwidget_get_window(widget), text)
    }

    /**
     * Replaces the text of the node's text field with [text], as the user would: it focuses the
     * field, selects all of it, and types [text] over it, or deletes it for an empty one.
     */
    public fun performTextReplacement(text: String): NodeInteraction = apply {
        val widget = fetchWidget()
        if (Rwidget_get_type(widget) != R_WIDGET_TEXT_INPUT) fail("the node with $selector is not a text field")
        if (Rwidget_is_focused(widget) != R_TRUE) click(widget)
        Rtext_input_select_all(widget)
        if (text.isEmpty()) press(widget, R_KEY_DELETE) else Rwindow_inject_text(Rwidget_get_window(widget), text)
    }

    /** Focuses the node's widget, then presses and releases [shortcut] there. */
    public fun performKeyPress(shortcut: KeyShortcut): NodeInteraction = apply {
        val widget = fetchWidget()
        Rwidget_focus(widget)
        injectKey(Rwidget_get_window(widget), shortcut)
    }

    /**
     * Scrolls the node's scroll container (a `VerticalScroll`, a `HorizontalScroll` or a lazy list)
     * by [dx] and [dy] from where it is, then draws a frame, so the states following it read it.
     */
    public fun performScrollBy(dx: Float = 0f, dy: Float = 0f): NodeInteraction = apply {
        val widget = fetchWidget()
        if (Rwidget_get_type(widget) != R_WIDGET_SCROLL_BOX) fail("the node with $selector does not scroll")
        val (x, y) = scrollOffset(widget)
        Rscroll_box_set_offset(widget, x + dx, y + dy)
        scope.frame()
    }

    /**
     * Types [color]'s code into the node's colour picker and presses Enter, as the user would; a
     * popup picker is opened first, with the keyboard, and its popup closed after. A compact picker
     * shows no code, so it fails. The code is typed after an `x` that is then deleted, so the
     * picker sees no other colour on the way (`#F00` is one): it reports [color] once.
     */
    public fun performColorChange(color: Color): NodeInteraction = apply {
        val widget = fetchWidget()
        if (Rwidget_get_type(widget) != R_WIDGET_COLOR_PICKER) fail("the node with $selector is not a colour picker")
        val window = Rwidget_get_window(widget)
        var code = codeInput(widget)
        val popups = Rwindow_get_popup_count(window)
        if (code == null) {
            // A popup picker opens on Enter; a compact one takes no focus, and opens nothing.
            Rwidget_focus(widget)
            if (Rwidget_is_focused(widget) == R_TRUE) press(widget, R_KEY_ENTER)
            if (Rwindow_get_popup_count(window) > popups) {
                code = codeInput(Rwidget_get_child(Rwindow_get_popup(window, popups), 0))
            }
        }
        if (code == null) fail("the colour picker with $selector is compact: it shows no code to type")
        fun channel(value: Float) = (value * 255f + 0.5f).toInt().toString(16).padStart(2, '0').uppercase()
        val text = channel(color.red) + channel(color.green) + channel(color.blue) + if (color.alpha < 1f) channel(color.alpha) else ""
        Rtext_input_set_text(code, "x")
        Rwidget_focus(code)
        press(code, R_KEY_END)
        Rwindow_inject_text(window, text)
        press(code, R_KEY_HOME)
        press(code, R_KEY_DELETE)
        press(code, R_KEY_ENTER)
        if (Rwindow_get_popup_count(window) > popups) Rwindow_close_popups(window)
    }

    /**
     * Scrolls every scroll box above the node's widget until it is in view
     * (`Rwidget_scroll_into_view`), then draws a frame, so the states following them read it.
     */
    public fun performScrollTo(): NodeInteraction = apply {
        Rwidget_scroll_into_view(fetchWidget())
        scope.frame()
    }

    /**
     * Scrolls the node's lazy list so its item at [index] is at the top (or the start), as
     * `scrollToItem` does, or the node's ListView just enough to show its row at [index]
     * (`Rlist_view_scroll_to_item`), then draws a frame.
     */
    public fun performScrollToIndex(index: Int): NodeInteraction = apply {
        val widget = fetchWidget()
        when (Rwidget_get_type(widget)) {
            R_WIDGET_LIST_VIEW -> {
                val count = Rlist_view_get_item_count(widget)
                if (index !in 0 until count) fail("the node with $selector has $count rows, so none at $index")
                Rlist_view_scroll_to_item(widget, index)
            }
            R_WIDGET_SCROLL_BOX -> {
                val count = Rwidget_get_child_count(widget)
                if (index !in 0 until count) fail("the node with $selector has $count items, so none at $index")
                val box = widgetRect(widget)
                val item = widgetRect(Rwidget_get_child(widget, index))
                val offset = scrollOffset(widget)
                // The axis the items go along: a column's are below each other.
                val vertical = Rwidget_get_child_count(widget) < 2 ||
                    widgetRect(Rwidget_get_child(widget, 1)).y != widgetRect(Rwidget_get_child(widget, 0)).y
                if (vertical) {
                    Rscroll_box_set_offset(widget, 0f, item.y - box.y + offset.second)
                } else {
                    Rscroll_box_set_offset(widget, item.x - box.x + offset.first, 0f)
                }
            }
            else -> fail("the node with $selector is no lazy list or ListView")
        }
        scope.frame()
    }

    /** Double-clicks the centre of the node's widget with the left button, as the user would. */
    public fun performDoubleClick(): NodeInteraction = apply {
        val widget = fetchWidget()
        click(widget)
        click(widget)
    }

    /** Presses and releases the right button at the centre of the node's widget, as the user would. */
    public fun performRightClick(): NodeInteraction = apply {
        val widget = fetchWidget()
        val rect = widgetRect(widget)
        val window = Rwidget_get_window(widget)
        Rwindow_inject_mouse_move(window, rect.x + rect.width / 2, rect.y + rect.height / 2)
        Rwindow_inject_mouse_button(window, R_MOUSE_RIGHT, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_RIGHT, R_FALSE)
    }

    /** Moves the pointer onto the centre of the node's widget. */
    public fun performMouseEnter(): NodeInteraction = apply {
        val widget = fetchWidget()
        val rect = widgetRect(widget)
        Rwindow_inject_mouse_move(Rwidget_get_window(widget), rect.x + rect.width / 2, rect.y + rect.height / 2)
    }

    /** Moves the pointer out of the node's window, so it leaves the node's widget. */
    public fun performMouseExit(): NodeInteraction = apply {
        Rwindow_inject_mouse_move(Rwidget_get_window(fetchWidget()), -10f, -10f)
    }

    /** The text input of [picker]'s code, or null when it shows none. */
    private fun codeInput(picker: Rid): Rid? =
        (0 until Rwidget_get_child_count(picker)).map { Rwidget_get_child(picker, it) }.firstOrNull { Rwidget_get_type(it) == R_WIDGET_TEXT_INPUT }

    private fun click(widget: Rid) {
        val rect = widgetRect(widget)
        val window = Rwidget_get_window(widget)
        Rwindow_inject_mouse_move(window, rect.x + rect.width / 2, rect.y + rect.height / 2)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
    }

    private fun press(widget: Rid, key: RKey) {
        val window = Rwidget_get_window(widget)
        Rwindow_inject_key(window, key, R_TRUE, 0u)
        Rwindow_inject_key(window, key, R_FALSE, 0u)
    }

    /**
     * Opens the menus down to [item] and clicks it, as the user would, so Raven decides what a
     * click on it does: a disabled item, or one in a disabled menu, does nothing. The menu of the
     * bar opens with `Rmenu_open` below the bar, rather than by a click on its title, and each
     * sub-menu opens as the pointer moves onto its entry. A `DropdownMenu`, in no bar, must be open
     * already, as the app opens it. Any menu still open is closed at the end.
     */
    private fun chooseMenuItem(item: RavenNode) {
        val path = scope.pathTo(item)
        val menus = path.dropLast(1).takeLastWhile { Rwidget_get_type(it.widget) == R_WIDGET_MENU }
        if (menus.isEmpty()) fail("the menu item with $selector is in no menu")
        val bar = path[path.size - menus.size - 2]
        val window = Rwidget_get_window(bar.widget)
        if (Rwidget_get_type(bar.widget) == R_WIDGET_MENU_BAR) {
            val barRect = widgetRect(bar.widget)
            Rmenu_open(menus.first().widget, window, barRect.x, barRect.y + barRect.height)
        } else if (menus.first().widget !in scope.openMenus(window)) {
            fail("the menu holding the item with $selector is not open")
        }
        for ((i, menu) in menus.withIndex()) {
            val entry = menus.getOrNull(i + 1) ?: item
            val (x, y) = menuEntryCentre(menu.widget, menu.children.indexOf(entry))
            Rwindow_inject_mouse_move(window, x, y)
        }
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
        Rwindow_close_popups(window)
    }
}

/** Every node a finder selects, as Compose's `SemanticsNodeInteractionCollection`. */
public class NodeInteractionCollection internal constructor(
    private val scope: RavenTestScope,
    private val matcher: NodeMatcher,
) {
    /** How many nodes match now, once the UI is idle. */
    public fun fetchCount(): Int {
        scope.waitForIdle()
        return scope.findNodes(matcher.matches).size
    }

    public fun assertCountEquals(expected: Int): NodeInteractionCollection = apply {
        val count = fetchCount()
        if (count != expected) fail("$count nodes with ${matcher.description}, expected $expected")
    }

    /** The node at [index] of those that match, in the order of the windows' trees. */
    public operator fun get(index: Int): NodeInteraction = NodeInteraction(scope, matcher, index)

    public fun onFirst(): NodeInteraction = get(0)

    public fun onLast(): NodeInteraction = NodeInteraction(scope, matcher, (fetchCount() - 1).coerceAtLeast(0))
}

/** Where a widget is in its window, from `Rwidget_get_rect`. */
@InternalComposeRavenApi
public data class WidgetRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    internal fun overlaps(other: WidgetRect): Boolean =
        x < other.x + other.width && other.x < x + width && y < other.y + other.height && other.y < y + height
}

@InternalComposeRavenApi
public fun widgetRect(widget: Rid): WidgetRect = memScoped {
    val values = List(4) { alloc<FloatVar>() }
    Rwidget_get_rect(widget, values[0].ptr, values[1].ptr, values[2].ptr, values[3].ptr)
    WidgetRect(values[0].value, values[1].value, values[2].value, values[3].value)
}

private fun scrollOffset(widget: Rid): Pair<Float, Float> = memScoped {
    val x = alloc<FloatVar>()
    val y = alloc<FloatVar>()
    Rscroll_box_get_offset(widget, x.ptr, y.ptr)
    x.value to y.value
}

/**
 * Where the entry at [index] of an open [menu] is, in its window. Raven doesn't report it, so
 * this follows `src/widgets/menu.c`: the entries are stacked inside a padding, each a row
 * high plus 2, and a separator is a fixed height.
 */
internal fun menuEntryCentre(menu: Rid, index: Int): Pair<Float, Float> {
    val rect = widgetRect(menu)
    fun height(entry: Rid) =
        if (isMenuSeparator(entry)) MENU_SEPARATOR_HEIGHT else Rwidget_get_metric(menu, R_METRIC_ROW_HEIGHT) + 2f
    val top = MENU_PADDING + (0 until index).sumOf { height(Rwidget_get_child(menu, it)).toDouble() }.toFloat()
    return rect.x + rect.width / 2 to rect.y + top + height(Rwidget_get_child(menu, index)) / 2
}

/** A menu separator: compose-raven gives its MenuItem the separator's accessible role. */
internal fun isMenuSeparator(widget: Rid): Boolean = Rwidget_get_accessible_role(widget) == R_ROLE_SEPARATOR

// The layout of an open menu, from src/widgets/menu.c.
private const val MENU_PADDING = 4f
private const val MENU_SEPARATOR_HEIGHT = 7f
