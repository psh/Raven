package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.staticCFunction
import raven.cinterop.R_ROLE_NONE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_MENU
import raven.cinterop.R_WIDGET_OVERLAY
import raven.cinterop.Rid
import raven.cinterop.Rmenu_add_item
import raven.cinterop.Rmenu_item_set_clicked_callback
import raven.cinterop.Rmenu_open
import raven.cinterop.Rwidget_create
import raven.cinterop.Rwidget_destroy
import raven.cinterop.Rwidget_is_valid
import raven.compose.ui.ChildKind
import raven.compose.ui.InputElement
import raven.compose.ui.InputEvent
import raven.compose.ui.InputKind
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.input.pointer.PointerButton
import raven.compose.ui.rememberErrorReporter
import raven.native.CallbackRegistry
import raven.native.callbackTarget

/** An entry of a [ContextMenuArea]'s menu, showing [label], which calls [onClick] when chosen. */
public class ContextMenuItem(public val label: String, public val onClick: () -> Unit)

/** The internal Box that holds a [ContextMenuArea]'s content: no role, so the screen reader sees the content. */
private val ContextMenuAreaWidget = WidgetDescriptor("ContextMenuArea", R_WIDGET_OVERLAY, ChildKind.Many, role = R_ROLE_NONE)

/**
 * Opens a menu of [items] at the pointer when [content] is clicked with the secondary button, as in
 * Compose Desktop. [items] is called on each click, so the menu shows what they are then. Raven
 * dismisses the menu: a chosen entry, a click outside, Escape. While [enabled] is false, the click
 * goes on to what is around the area.
 *
 * The content goes in an internal Box, which takes the click when nothing inside consumes it.
 */
@Composable
public fun ContextMenuArea(
    items: () -> List<ContextMenuItem>,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val reportError = rememberErrorReporter()
    val menu = remember(reportError) { ContextMenu(reportError) }
    DisposableEffect(menu) { onDispose { menu.discard() } }
    RavenWidget(ContextMenuAreaWidget, OpenContextMenuElement(menu, items, enabled), {}) { content() }
}

/** Opens [menu] with [items] on a click of the secondary button. */
private data class OpenContextMenuElement(
    val menu: ContextMenu,
    val items: () -> List<ContextMenuItem>,
    val enabled: Boolean,
) : InputElement {
    override fun onInput(event: InputEvent): Boolean {
        if (!enabled || event.button != PointerButton.Secondary) return false
        // Raven opens its own context menus on the release, so this one does too.
        if (event.kind == InputKind.Release) menu.open(items(), event.window, event.windowX, event.windowY)
        return event.kind == InputKind.Press || event.kind == InputKind.Release
    }
}

/**
 * The unparented Raven Menu a [ContextMenuArea] opens, built again on each click. The one built
 * last is kept until the next click or until the area leaves the composition, since Raven reports
 * no close.
 */
private class ContextMenu(private val reportError: (Throwable) -> Unit) {
    private var menu: Rid? = null

    fun open(items: List<ContextMenuItem>, window: Rid, x: Float, y: Float) {
        discard()
        if (items.isEmpty()) return
        val built = Rwidget_create(R_WIDGET_MENU)
        menu = built
        for (item in items) {
            val entry = Rmenu_add_item(built, item.label)
            // The entry's reference to its item goes when the entry does, with the menu.
            Rmenu_item_set_clicked_callback(entry, onItemChosen, CallbackRegistry.register(entry, ChosenItem(item, reportError)))
        }
        Rmenu_open(built, window, x, y)
    }

    fun discard() {
        menu?.let { if (Rwidget_is_valid(it) == R_TRUE) Rwidget_destroy(it) }
        menu = null
    }
}

private class ChosenItem(val item: ContextMenuItem, val reportError: (Throwable) -> Unit)

private val onItemChosen = staticCFunction { _: Rid, userData: COpaquePointer? ->
    val chosen = userData!!.callbackTarget<ChosenItem>()
    try {
        chosen.item.onClick()
    } catch (t: Throwable) {
        chosen.reportError(t)
    }
}
