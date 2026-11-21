package raven.compose.ui.test

import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import raven.cinterop.RKey
import raven.cinterop.R_FALSE
import raven.cinterop.R_KEY_DELETE
import raven.cinterop.R_KEY_DOWN
import raven.cinterop.R_KEY_END
import raven.cinterop.R_KEY_ENTER
import raven.cinterop.R_KEY_HOME
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_MOUSE_RIGHT
import raven.cinterop.R_TRUE
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
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_type
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
import raven.compose.ui.RavenHost
import raven.compose.ui.RavenNode
import raven.compose.ui.graphics.Color
import raven.compose.ui.input.key.KeyShortcut
import raven.native.toRavenString

/**
 * A node [findNode] found, to read and act on as the user would, through Raven's input injection:
 * real input, so a disabled widget can't be used. [selector] names it in failures, which are
 * [NodeException]s. Nothing here waits or draws a frame: the harness waits for idle around it, and
 * a live app's tool suspends in `RavenHost.awaitIdle`.
 */
@InternalComposeRavenApi
public class FoundNode internal constructor(
    private val host: RavenHost,
    public val node: RavenNode,
    /** What found it, as failures name it: `text "Save"`, `tag "add" [1]`. */
    public val selector: String,
) {
    /** The node's Raven widget. */
    public val widget: Rid get() = node.widget

    // -----------------------------------------------------------------------------------------
    //  Reads
    // -----------------------------------------------------------------------------------------

    /** The text the node shows: a Text's, a text field's, a code editor's or a menu item's. */
    public fun fetchText(): String =
        labelText(node) ?: menuItemText(node) ?: when (Rwidget_get_type(widget)) {
            R_WIDGET_TEXT_INPUT -> Rtext_input_get_text(widget).toRavenString()
            R_WIDGET_TEXT_EDITOR -> Rtext_editor_get_text(widget).toRavenString()
            else -> fail("the node with $selector shows no text")
        }

    /** Whether the node's checkbox or menu item is checked. */
    public fun fetchChecked(): Boolean = when (Rwidget_get_type(widget)) {
        R_WIDGET_CHECKBOX -> Rcheckbox_is_checked(widget) == R_TRUE
        R_WIDGET_MENU_ITEM -> Rmenu_item_is_checked(widget) == R_TRUE
        else -> fail("the node with $selector can't be checked")
    }

    /** Whether the node's widget can be used: enabled, as are its ancestors. */
    public fun fetchEnabled(): Boolean = Rwidget_is_enabled(widget) == R_TRUE

    /** Whether the node's widget has the keyboard focus. */
    public fun fetchFocused(): Boolean = Rwidget_is_focused(widget) == R_TRUE

    /** The index of the item selected in the node's combo box, or -1 for none. */
    public fun fetchSelectedIndex(): Int = Rcombo_box_get_selected(comboBox())

    /** The number the node's slider, spin box or progress bar holds. */
    public fun fetchValue(): Double = when (Rwidget_get_type(widget)) {
        R_WIDGET_SPIN_BOX -> Rspin_box_get_value(widget)
        R_WIDGET_SLIDER -> Rslider_get_value(widget).toDouble()
        R_WIDGET_PROGRESSBAR -> Rprogress_bar_get_value(widget).toDouble()
        else -> fail("the node with $selector has no number value")
    }

    // -----------------------------------------------------------------------------------------
    //  Actions
    // -----------------------------------------------------------------------------------------

    /**
     * Presses and releases the left button at the centre of the node's widget, as the user would.
     * A menu item is chosen from its menu: see [chooseMenuItem].
     */
    public fun performClick() {
        if (Rwidget_get_type(widget) == R_WIDGET_MENU_ITEM) chooseMenuItem() else click(widget)
    }

    /** Double-clicks the centre of the node's widget with the left button, as the user would. */
    public fun performDoubleClick() {
        click(widget)
        click(widget)
    }

    /** Presses and releases the right button at the centre of the node's widget, as the user would. */
    public fun performRightClick() {
        val rect = widgetRect(widget)
        val window = Rwidget_get_window(widget)
        Rwindow_inject_mouse_move(window, rect.x + rect.width / 2, rect.y + rect.height / 2)
        Rwindow_inject_mouse_button(window, R_MOUSE_RIGHT, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_RIGHT, R_FALSE)
    }

    /** Moves the pointer onto the centre of the node's widget. */
    public fun performMouseEnter() {
        val rect = widgetRect(widget)
        Rwindow_inject_mouse_move(Rwidget_get_window(widget), rect.x + rect.width / 2, rect.y + rect.height / 2)
    }

    /** Moves the pointer out of the node's window, so it leaves the node's widget. */
    public fun performMouseExit() {
        Rwindow_inject_mouse_move(Rwidget_get_window(widget), -10f, -10f)
    }

    /**
     * Types [text] into the node's widget, one character at a time with no frame between them. It
     * clicks the widget first unless it already has focus, so the caret stays where it was.
     */
    public fun performTextInput(text: String) {
        if (Rwidget_is_focused(widget) != R_TRUE) click(widget)
        Rwindow_inject_text(Rwidget_get_window(widget), text)
    }

    /**
     * Replaces the text of the node's text field with [text], as the user would: it focuses the
     * field, selects all of it, and types [text] over it, or deletes it for an empty one.
     */
    public fun performTextReplacement(text: String) {
        if (Rwidget_get_type(widget) != R_WIDGET_TEXT_INPUT) fail("the node with $selector is not a text field")
        if (Rwidget_is_focused(widget) != R_TRUE) click(widget)
        Rtext_input_select_all(widget)
        if (text.isEmpty()) press(widget, R_KEY_DELETE) else Rwindow_inject_text(Rwidget_get_window(widget), text)
    }

    /** Focuses the node's widget, then presses and releases [shortcut] there. */
    public fun performKeyPress(shortcut: KeyShortcut) {
        Rwidget_focus(widget)
        injectKey(Rwidget_get_window(widget), shortcut)
    }

    /**
     * Picks the item at [index] of the node's combo box as the user would with the keyboard: a
     * click opens its list, Home and Down move to the item, and Enter picks it.
     */
    public fun performSelection(index: Int) {
        val box = comboBox()
        val count = Rcombo_box_get_item_count(box)
        if (index !in 0 until count) fail("the node with $selector has $count items, so none at $index")
        click(box)
        press(box, R_KEY_HOME)
        repeat(index) { press(box, R_KEY_DOWN) }
        press(box, R_KEY_ENTER)
    }

    /**
     * Types [value] into the node's spin box as the user would: a click starts typing over the
     * value, and Enter takes what was typed.
     */
    public fun performValueInput(value: Double) {
        if (Rwidget_get_type(widget) != R_WIDGET_SPIN_BOX) fail("the node with $selector takes no number")
        click(widget)
        Rwindow_inject_text(Rwidget_get_window(widget), value.toString())
        press(widget, R_KEY_ENTER)
    }

    /**
     * Types [color]'s code into the node's colour picker and presses Enter, as the user would; a
     * popup picker is opened first, with the keyboard, and its popup closed after. A compact picker
     * shows no code, so it fails. The code is typed after an `x` that is then deleted, so the
     * picker sees no other colour on the way (`#F00` is one): it reports [color] once.
     */
    public fun performColorChange(color: Color) {
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
     * Scrolls the node's scroll container (a `VerticalScroll`, a `HorizontalScroll` or a lazy list)
     * by [dx] and [dy] from where it is. The states following it read it after the next frame.
     */
    public fun performScrollBy(dx: Float = 0f, dy: Float = 0f) {
        if (Rwidget_get_type(widget) != R_WIDGET_SCROLL_BOX) fail("the node with $selector does not scroll")
        val (x, y) = scrollOffset(widget)
        Rscroll_box_set_offset(widget, x + dx, y + dy)
    }

    /** Scrolls every scroll box above the node's widget until it is in view (`Rwidget_scroll_into_view`). */
    public fun performScrollTo() {
        Rwidget_scroll_into_view(widget)
    }

    /**
     * Scrolls the node's lazy list so its item at [index] is at the top (or the start), as
     * `scrollToItem` does, or the node's ListView just enough to show its row at [index]
     * (`Rlist_view_scroll_to_item`).
     */
    public fun performScrollToIndex(index: Int) {
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
    }

    // -----------------------------------------------------------------------------------------

    private fun fail(message: String): Nothing = throw NodeException(message)

    private fun comboBox(): Rid = widget.also {
        if (Rwidget_get_type(it) != R_WIDGET_COMBO_BOX) fail("the node with $selector is not a combo box")
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
     * Opens the menus down to the node's item and clicks it, as the user would, so Raven decides
     * what a click on it does: a disabled item, or one in a disabled menu, does nothing. The menu of
     * the bar opens with `Rmenu_open` below the bar, rather than by a click on its title, and each
     * sub-menu opens as the pointer moves onto its entry. A `DropdownMenu`, in no bar, must be open
     * already, as the app opens it. Any menu still open is closed at the end.
     */
    private fun chooseMenuItem() {
        val path = host.pathTo(node)
        val menus = path.dropLast(1).takeLastWhile { Rwidget_get_type(it.widget) == R_WIDGET_MENU }
        if (menus.isEmpty()) fail("the menu item with $selector is in no menu")
        val bar = path[path.size - menus.size - 2]
        val window = Rwidget_get_window(bar.widget)
        if (Rwidget_get_type(bar.widget) == R_WIDGET_MENU_BAR) {
            val barRect = widgetRect(bar.widget)
            Rmenu_open(menus.first().widget, window, barRect.x, barRect.y + barRect.height)
        } else if (menus.first().widget !in openMenus(window)) {
            fail("the menu holding the item with $selector is not open")
        }
        for ((i, menu) in menus.withIndex()) {
            val entry = menus.getOrNull(i + 1) ?: node
            val (x, y) = menuEntryCentre(menu.widget, menu.children.indexOf(entry))
            Rwindow_inject_mouse_move(window, x, y)
        }
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
        Rwindow_close_popups(window)
    }
}

private fun scrollOffset(widget: Rid): Pair<Float, Float> = memScoped {
    val x = alloc<FloatVar>()
    val y = alloc<FloatVar>()
    Rscroll_box_get_offset(widget, x.ptr, y.ptr)
    x.value to y.value
}
