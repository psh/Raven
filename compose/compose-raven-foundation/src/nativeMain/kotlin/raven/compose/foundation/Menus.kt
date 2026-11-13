package raven.compose.foundation

import androidx.compose.runtime.Composable
import raven.cinterop.R_FALSE
import raven.cinterop.R_ROLE_SEPARATOR
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_MENU
import raven.cinterop.R_WIDGET_MENU_BAR
import raven.cinterop.R_WIDGET_MENU_ITEM
import raven.cinterop.Rmenu_bar_set_native
import raven.cinterop.R_INVALID_ID
import raven.cinterop.Rid
import raven.cinterop.Rmenu_item_set_checkable
import raven.cinterop.Rmenu_item_set_icon
import raven.cinterop.Rmenu_item_set_text
import raven.cinterop.Rmenu_set_title
import raven.cinterop.Rwidget_set_attribute
import raven.compose.ui.ChildKind
import raven.compose.ui.MenuItemChecked
import raven.compose.ui.MenuItemCheckedChanged
import raven.compose.ui.MenuItemClicked
import raven.compose.ui.MenuItemShortcut
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.graphics.painter.Painter
import raven.compose.ui.graphics.painter.TextureRegion
import raven.compose.ui.input.key.KeyShortcut

/** What a [MenuBar] holds: only [Menu]s. */
public interface MenuBarScope

/** What a [Menu] holds: [Item]s, [CheckboxItem]s, [Separator]s and sub-[Menu]s. */
public interface MenuScope

private object MenuBarScopeInstance : MenuBarScope
private object MenuScopeInstance : MenuScope

private val MenuBarWidget = WidgetDescriptor("MenuBar", R_WIDGET_MENU_BAR, ChildKind.Many)
private val MenuWidget = WidgetDescriptor("Menu", R_WIDGET_MENU, ChildKind.Many)
private val ItemWidget = WidgetDescriptor("Item", R_WIDGET_MENU_ITEM, events = listOf(MenuItemClicked))
private val CheckboxItemWidget = WidgetDescriptor("CheckboxItem", R_WIDGET_MENU_ITEM, events = listOf(MenuItemCheckedChanged))
private val SeparatorWidget = WidgetDescriptor("Separator", R_WIDGET_MENU_ITEM, role = R_ROLE_SEPARATOR)

private val Native = Property<Boolean> { widget, native -> Rmenu_bar_set_native(widget, if (native) R_TRUE else R_FALSE) }
private val MenuTitle = Property<String> { widget, title -> Rmenu_set_title(widget, title) }
private val ItemText = Property<String> { widget, text -> Rmenu_item_set_text(widget, text) }
private val Checkable = Property<Boolean> { widget, checkable -> Rmenu_item_set_checkable(widget, if (checkable) R_TRUE else R_FALSE) }

/**
 * Raven's menu separator is a MenuItem with its separator flag, which only the `Separator`
 * attribute sets on an item made apart from its menu (a Separator widget is no menu separator).
 */
private val MenuSeparator = Property<Boolean> { widget, separator ->
    Rwidget_set_attribute(widget, "Separator", if (separator) "true" else "false")
}

/**
 * A bar of [Menu]s, each opened by its title. Unlike Compose Desktop's, it is a composable of the
 * layout, drawn where the app places it (in a Column above the content, or in a header bar).
 *
 * With [native], the first MenuBar of a macOS window is shown in the system's menu bar instead,
 * where it takes no room in the window; elsewhere it is drawn in the window either way. Off by
 * default, unlike Raven's, so the bar is in the window everywhere.
 */
@Composable
public fun MenuBar(
    modifier: Modifier = Modifier,
    native: Boolean = false,
    content: @Composable MenuBarScope.() -> Unit,
) {
    RavenWidget(MenuBarWidget, modifier, { bind(native, Native) }) { MenuBarScopeInstance.content() }
}

/** A menu of the bar, titled [text]. */
@Composable
public fun MenuBarScope.Menu(text: String, content: @Composable MenuScope.() -> Unit) {
    RavenMenu(text, content)
}

/** A sub-menu, titled [text], opened from its entry in this menu. */
@Composable
public fun MenuScope.Menu(text: String, content: @Composable MenuScope.() -> Unit) {
    RavenMenu(text, content)
}

@Composable
private fun RavenMenu(text: String, content: @Composable MenuScope.() -> Unit) {
    RavenWidget(MenuWidget, Modifier, { bind(text, MenuTitle) }) { MenuScopeInstance.content() }
}

/**
 * An entry showing [text], which calls [onClick] when chosen, or when [shortcut] is pressed in its
 * window. [icon] is drawn before the text, as it is.
 */
@Composable
public fun MenuScope.Item(
    text: String,
    onClick: () -> Unit,
    shortcut: KeyShortcut? = null,
    enabled: Boolean = true,
    icon: Painter? = null,
) {
    RavenWidget(ItemWidget, Modifier, {
        bind(text, ItemText)
        bind(shortcut, MenuItemShortcut)
        bind(enabled, Enabled)
        bind(icon, ItemIcon)
        bind(onClick, MenuItemClicked)
    })
}

/** A menu item's icon, not tinted; null removes it. */
private val ItemIcon = Property<Painter?> { widget, icon ->
    if (icon == null) {
        menuCalls.setIcon(widget, R_INVALID_ID, TextureRegion.Whole)
    } else {
        menuCalls.setIcon(widget, icon.texture, icon.region)
        icon.addViewer(widget)
    }
}

/** The menu item's icon call. Raven has no getter for it, so the tests replace [menuCalls] to see it. */
internal open class MenuCalls {
    open fun setIcon(item: Rid, texture: Rid, region: TextureRegion) =
        Rmenu_item_set_icon(item, texture, region.minU, region.minV, region.maxU, region.maxV, R_FALSE)
}

internal var menuCalls: MenuCalls = MenuCalls()

/**
 * An entry with a check mark showing [checked], controlled as [Checkbox] is: choosing it calls
 * [onCheckedChange] with the new value, and if the app doesn't take it, the mark shows [checked]
 * again.
 */
@Composable
public fun MenuScope.CheckboxItem(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shortcut: KeyShortcut? = null,
    enabled: Boolean = true,
) {
    RavenWidget(CheckboxItemWidget, Modifier, {
        bind(text, ItemText)
        bind(true, Checkable)
        bind(shortcut, MenuItemShortcut)
        bind(enabled, Enabled)
        bind(checked, MenuItemChecked)
        bind(onCheckedChange, MenuItemCheckedChanged)
    })
}

/** A line between groups of entries. */
@Composable
public fun MenuScope.Separator() {
    RavenWidget(SeparatorWidget, Modifier, { bind(true, MenuSeparator) })
}
