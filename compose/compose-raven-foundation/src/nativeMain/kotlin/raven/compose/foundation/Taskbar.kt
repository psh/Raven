package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import raven.cinterop.R_INVALID_ID
import raven.cinterop.Rid
import raven.compose.ui.ApplicationScope
import raven.compose.ui.rememberDetachedMenu
import raven.compose.ui.rememberPerHost

/**
 * A short text on the app's icon, a number of unread messages or "!": the Dock's badge on macOS, a
 * small round icon on the taskbar buttons on Windows, a number on a Linux launcher (anything else
 * shows nothing there). It shows while the composable is in the composition, anywhere in the app;
 * with several, the last composed wins, and the badge goes once every one has left. An empty
 * [text] shows no badge.
 */
@Composable
public fun TaskbarBadge(text: String) {
    val badges = rememberPerHost(BadgesKey) { Badges() }
    val badge = remember { Badge(text) }
    DisposableEffect(badges, badge) {
        badges.add(badge)
        onDispose { badges.remove(badge) }
    }
    SideEffect { badges.update(badge, text) }
}

/** The app's badges, in the order they were composed: the last one is shown. */
private class Badges {
    private val shown = mutableListOf<Badge>()
    private var current: String? = null

    fun add(badge: Badge) {
        shown += badge
        show()
    }

    fun remove(badge: Badge) {
        shown -= badge
        show()
    }

    fun update(badge: Badge, text: String) {
        if (badge.text == text) return
        badge.text = text
        show()
    }

    private fun show() {
        val text = shown.lastOrNull()?.text?.ifEmpty { null }
        if (text == current) return
        current = text
        shellCalls.setBadge(text)
    }
}

private class Badge(var text: String)

private object BadgesKey

/**
 * The menu of the app's icon in the Dock on macOS, above the system's entries, made of [Item]s,
 * [CheckboxItem]s, [Separator]s and sub-[Menu]s, which work as in a window. Elsewhere it shows
 * nothing. It is there while this is in the composition; with several, the last composed wins.
 */
@Composable
public fun ApplicationScope.DockMenu(content: @Composable MenuScope.() -> Unit) {
    val menu = rememberDetachedMenu { MenuContent(content) }
    val menus = rememberPerHost(DockMenusKey) { DockMenus() }
    DisposableEffect(menus, menu) {
        menus.add(menu)
        onDispose { menus.remove(menu) }
    }
}

/** The app's Dock menus, in the order they were composed: the last one is the Dock's. */
private class DockMenus {
    private val shown = mutableListOf<Rid>()
    private var current = R_INVALID_ID

    fun add(menu: Rid) {
        shown += menu
        show()
    }

    fun remove(menu: Rid) {
        shown -= menu
        show()
    }

    private fun show() {
        val menu = shown.lastOrNull() ?: R_INVALID_ID
        if (menu == current) return
        current = menu
        shellCalls.setDockMenu(menu)
    }
}

private object DockMenusKey
