package raven.compose.ui.test

import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import raven.cinterop.R_FALSE
import raven.cinterop.R_METRIC_ROW_HEIGHT
import raven.cinterop.R_MOD_ALT
import raven.cinterop.R_MOD_CTRL
import raven.cinterop.R_MOD_SHIFT
import raven.cinterop.R_MOD_SUPER
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_ROLE_SEPARATOR
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_MENU
import raven.cinterop.Rid
import raven.cinterop.Rmenu_item_get_text
import raven.cinterop.Rwidget_get_accessible_role
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_metric
import raven.cinterop.Rwidget_get_rect
import raven.cinterop.Rwidget_get_type
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_get_popup
import raven.cinterop.Rwindow_get_popup_count
import raven.cinterop.Rwindow_inject_key
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.RavenHost
import raven.compose.ui.RavenNode
import raven.compose.ui.input.key.KeyShortcut
import raven.compose.ui.input.key.ravenKey
import raven.native.toRavenString

/**
 * Why a lookup or an action failed, in the words tests and agents both read: `no node with text
 * "Save"`. compose-raven-test reports it as a test failure, the UI tools as a tool failure.
 */
@InternalComposeRavenApi
public class NodeException(message: String) : Exception(message)

/** The nodes of every open window, its island and its dialogs that [matches] accepts, content nodes left out. */
@InternalComposeRavenApi
public fun RavenHost.findNodes(matches: (RavenNode) -> Boolean): List<RavenNode> {
    val found = mutableListOf<RavenNode>()
    fun visit(node: RavenNode) {
        if (!node.isContentNode && matches(node)) found += node
        node.children.forEach(::visit)
    }
    (windowRoots + islandRoots + dialogRoots).forEach(::visit)
    return found
}

/**
 * The one node [matcher] accepts, or the one at [index] of all it accepts. Throws a
 * [NodeException] when there is none, or more than one.
 */
@InternalComposeRavenApi
public fun RavenHost.findNode(matcher: NodeMatcher, index: Int? = null): FoundNode {
    val selector = if (index == null) matcher.description else "${matcher.description} [$index]"
    val found = findNodes(matcher.matches)
    val node = when {
        index != null -> found.getOrNull(index) ?: throw NodeException("${found.size} nodes with $selector, so none at $index")
        found.isEmpty() -> throw NodeException("no node with $selector")
        found.size > 1 -> throw NodeException("${found.size} nodes with $selector, expected one")
        else -> found.single()
    }
    return FoundNode(this, node, selector)
}

/** The nodes from a window's root down to [node], both included. */
internal fun RavenHost.pathTo(node: RavenNode): List<RavenNode> {
    fun search(from: RavenNode): List<RavenNode>? =
        if (from === node) listOf(from) else from.children.firstNotNullOfOrNull { search(it) }?.let { listOf(from) + it }
    return (windowRoots + islandRoots + dialogRoots).firstNotNullOfOrNull { search(it) } ?: throw NodeException("the node is in no window")
}

/** The one window open, or a [NodeException]. */
@InternalComposeRavenApi
public fun RavenHost.singleWindow(): Rid {
    val root = windowRoots.singleOrNull() ?: throw NodeException("${windowRoots.size} windows, expected one")
    return Rwidget_get_window(root.widget)
}

/** The menus open in [window] now, bottom to top. */
@InternalComposeRavenApi
public fun openMenus(window: Rid): List<Rid> =
    List(Rwindow_get_popup_count(window)) { Rwindow_get_popup(window, it) }.filter { Rwidget_get_type(it) == R_WIDGET_MENU }

/**
 * Clicks the entry showing [text] in the menu open on top of [window], as the user would: a menu
 * the app built from data, such as a `ContextMenuArea`'s, which no finder sees.
 */
@InternalComposeRavenApi
public fun clickOpenMenuItem(window: Rid, text: String) {
    val menu = openMenus(window).lastOrNull() ?: throw NodeException("no menu is open")
    val entries = List(Rwidget_get_child_count(menu)) { Rwidget_get_child(menu, it) }
    val index = entries.indexOfFirst { !isMenuSeparator(it) && Rmenu_item_get_text(it).toRavenString() == text }
    if (index < 0) throw NodeException("the open menu has no entry \"$text\"")
    val (x, y) = menuEntryCentre(menu, index)
    Rwindow_inject_mouse_move(window, x, y)
    Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
    Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
}

/** Presses and releases [shortcut]'s key in [window], its modifier keys held. */
@InternalComposeRavenApi
public fun injectKey(window: Rid, shortcut: KeyShortcut) {
    var modifiers = 0u
    if (shortcut.ctrl) modifiers = modifiers or R_MOD_CTRL
    if (shortcut.shift) modifiers = modifiers or R_MOD_SHIFT
    if (shortcut.alt) modifiers = modifiers or R_MOD_ALT
    if (shortcut.meta) modifiers = modifiers or R_MOD_SUPER
    Rwindow_inject_key(window, shortcut.key.ravenKey, R_TRUE, modifiers)
    Rwindow_inject_key(window, shortcut.key.ravenKey, R_FALSE, modifiers)
}

/** Where a widget is in its window, from `Rwidget_get_rect`. */
@InternalComposeRavenApi
public data class WidgetRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    public fun overlaps(other: WidgetRect): Boolean =
        x < other.x + other.width && other.x < x + width && y < other.y + other.height && other.y < y + height
}

@InternalComposeRavenApi
public fun widgetRect(widget: Rid): WidgetRect = memScoped {
    val values = List(4) { alloc<FloatVar>() }
    Rwidget_get_rect(widget, values[0].ptr, values[1].ptr, values[2].ptr, values[3].ptr)
    WidgetRect(values[0].value, values[1].value, values[2].value, values[3].value)
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
