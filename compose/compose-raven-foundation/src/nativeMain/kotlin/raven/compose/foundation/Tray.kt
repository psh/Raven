package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.refTo
import kotlinx.cinterop.staticCFunction
import raven.cinterop.RMouseButton
import raven.cinterop.RNotificationUrgency
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_NOTIFICATION_CRITICAL
import raven.cinterop.R_NOTIFICATION_LOW
import raven.cinterop.R_NOTIFICATION_NORMAL
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rnotification_show
import raven.cinterop.Rtaskbar_set_badge
import raven.cinterop.Rtaskbar_set_menu
import raven.cinterop.Rtray_create
import raven.cinterop.Rtray_destroy
import raven.cinterop.Rtray_is_supported
import raven.cinterop.Rtray_set_clicked_callback
import raven.cinterop.Rtray_set_icon
import raven.cinterop.Rtray_set_menu
import raven.cinterop.Rtray_set_tooltip
import raven.compose.ui.ApplicationScope
import raven.compose.ui.LocalRavenHost
import raven.compose.ui.graphics.painter.Painter
import raven.compose.ui.graphics.painter.Pixels
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.rememberDetachedMenu

/**
 * Raven's tray, notification, badge and Dock menu calls. Raven has getters for none of what a tray was given, and
 * the headless window system shows no notification, so the tests replace [shellCalls] to see them.
 */
internal open class ShellCalls {
    open fun trayIsSupported(): Boolean = Rtray_is_supported() == R_TRUE

    open fun createTray(): Rid = Rtray_create()

    open fun destroyTray(tray: Rid) = Rtray_destroy(tray)

    @OptIn(kotlin.ExperimentalUnsignedTypes::class)
    open fun setTrayIcon(tray: Rid, icon: Pixels) = Rtray_set_icon(tray, icon.rgba.asUByteArray().refTo(0), icon.width, icon.height)

    open fun setTrayTooltip(tray: Rid, tooltip: String?) = Rtray_set_tooltip(tray, tooltip)

    open fun setTrayMenu(tray: Rid, menu: Rid) = Rtray_set_menu(tray, menu)

    open fun setTrayClicked(tray: Rid, userData: COpaquePointer?) =
        Rtray_set_clicked_callback(tray, if (userData == null) null else onTrayClicked, userData)

    open fun showNotification(title: String, message: String, urgency: RNotificationUrgency): Boolean =
        Rnotification_show(title, message, urgency) == R_TRUE

    open fun setBadge(text: String?): Boolean = Rtaskbar_set_badge(text) == R_TRUE

    open fun setDockMenu(menu: Rid): Boolean = Rtaskbar_set_menu(menu) == R_TRUE
}

internal var shellCalls: ShellCalls = ShellCalls()

/** What a tray's click reaches: the app's `onAction`, and where its failures go. */
private class TrayTarget(var onAction: () -> Unit, val reportError: (Throwable) -> Unit)

private val onTrayClicked = staticCFunction { _: Rid, button: RMouseButton, userData: COpaquePointer? ->
    val target = userData!!.asStableRef<TrayTarget>().get()
    // The primary action: a left click. The right one opens the menu, which the system shows.
    if (button == R_MOUSE_LEFT) {
        try {
            target.onAction()
        } catch (t: Throwable) {
            target.reportError(t)
        }
    }
}

/**
 * A notification of the system, sent with [TrayState.sendNotification] or
 * [ApplicationScope.sendNotification]: its [title], its [message], and how much its [type] asks
 * for attention.
 */
public class Notification(
    public val title: String,
    public val message: String,
    public val type: Type = Type.None,
) {
    /** How much a notification asks for attention: [None] may go quietly to the list of notifications, [Error] stays until dismissed where the system allows it. */
    public enum class Type(internal val urgency: RNotificationUrgency) {
        None(R_NOTIFICATION_LOW),
        Info(R_NOTIFICATION_NORMAL),
        Warning(R_NOTIFICATION_NORMAL),
        Error(R_NOTIFICATION_CRITICAL),
    }

    override fun equals(other: Any?): Boolean =
        other is Notification && other.title == title && other.message == message && other.type == type

    override fun hashCode(): Int = (title.hashCode() * 31 + message.hashCode()) * 31 + type.hashCode()

    override fun toString(): String = "Notification($title, $message, $type)"
}

private fun send(notification: Notification): Boolean =
    shellCalls.showNotification(notification.title, notification.message, notification.type.urgency)

/** A [Tray]'s state: what sends its notifications, as Compose Desktop's does. */
@Stable
public class TrayState {
    /**
     * Asks the system to show [notification]. Returns whether it took it: false where it shows
     * none (no notification server, an executable outside a bundle on macOS, headless), so the app
     * can show its own message instead. Call it on Raven's UI thread, from an event handler or an effect.
     */
    public fun sendNotification(notification: Notification): Boolean = send(notification)
}

/** A [TrayState] remembered across recompositions. */
@Composable
public fun rememberTrayState(): TrayState = remember { TrayState() }

/**
 * Sends [notification] as [TrayState.sendNotification] does, for an app with no tray: Raven's
 * notifications need none. Returns whether the system took it.
 */
public fun ApplicationScope.sendNotification(notification: Notification): Boolean = send(notification)

/**
 * The app's icon beside the clock: in the notification area (Windows), the menu bar (macOS), or the
 * panel of a Linux desktop. While a tray is in the composition, the app keeps running after its
 * last window closes, until [ApplicationScope.exitApplication], as Raven's rule is.
 *
 * [icon] is pixels, read again from its image file, so it is a `painterResource`; another painter
 * leaves the icon empty, with a warning. [tooltip] shows while the pointer rests on it, [onAction]
 * is called on a left click, and [menu], of [Item]s, [CheckboxItem]s, [Separator]s and sub-[Menu]s,
 * is the menu the system shows for it (a right click; a click on macOS, where it then takes the
 * left click from [onAction]). Where the system shows no tray icon, it shows nothing, with a warning.
 * [state] sends notifications, which in Raven need no tray.
 */
@Composable
public fun ApplicationScope.Tray(
    icon: Painter,
    state: TrayState = rememberTrayState(),
    tooltip: String? = null,
    onAction: () -> Unit = {},
    menu: @Composable MenuScope.() -> Unit = {},
) {
    val host = LocalRavenHost.current
    val currentOnAction by rememberUpdatedState(onAction)
    val tray = remember {
        if (!shellCalls.trayIsSupported()) {
            layoutWarningSink("Tray: the system shows no tray icon here, so the tray shows nothing")
            null
        } else {
            shellCalls.createTray().takeIf { it != R_INVALID_ID }
                ?: null.also { layoutWarningSink("Tray: the system refused the tray icon, so the tray shows nothing") }
        }
    }
    val menuWidget = rememberDetachedMenu { MenuContent(menu) }
    if (tray == null) return
    DisposableEffect(tray) {
        val target = StableRef.create(TrayTarget({ currentOnAction() }, host::reportError))
        shellCalls.setTrayClicked(tray, target.asCPointer())
        onDispose {
            shellCalls.setTrayClicked(tray, null)
            shellCalls.destroyTray(tray)
            target.dispose()
        }
    }
    val shown = remember { TrayShown() }
    SideEffect {
        if (!shown.hasIcon || shown.icon != icon) {
            shown.hasIcon = true
            shown.icon = icon
            val pixels = icon.readPixels()
            if (pixels == null) {
                layoutWarningSink("Tray: a tray icon is read from its image file, so $icon can't be one; use painterResource")
            } else {
                shellCalls.setTrayIcon(tray, pixels)
            }
        }
    }
    SideEffect {
        if (!shown.hasTooltip || shown.tooltip != tooltip) {
            shown.hasTooltip = true
            shown.tooltip = tooltip
            shellCalls.setTrayTooltip(tray, tooltip)
        }
    }
    DisposableEffect(tray, menuWidget) {
        shellCalls.setTrayMenu(tray, menuWidget)
        onDispose { }
    }
}

/** What a [Tray] last gave Raven, so a recomposition calls Raven only for what changed. */
private class TrayShown {
    var hasIcon = false
    var icon: Painter? = null
    var hasTooltip = false
    var tooltip: String? = null
}
