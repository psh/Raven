package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.COpaquePointer
import raven.cinterop.RNotificationUrgency
import raven.cinterop.R_FALSE
import raven.cinterop.R_NOTIFICATION_CRITICAL
import raven.cinterop.R_NOTIFICATION_LOW
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Ris_running
import raven.cinterop.Rmenu_item_get_text
import raven.cinterop.Rmenu_open
import raven.cinterop.Rquit
import raven.cinterop.Rtray_is_valid
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.compose.ui.ApplicationScope
import raven.compose.ui.Window
import androidx.compose.runtime.remember
import raven.cinterop.Rwidget_get_window
import raven.compose.ui.Modifier
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.ui.graphics.painter.BitmapPainter
import raven.compose.ui.testTag
import raven.compose.ui.graphics.painter.Pixels
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.res.painterResource
import raven.compose.ui.test.clickOpenMenuItem
import raven.compose.ui.test.runRavenTest
import raven.native.toRavenString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Raven's own calls, recorded: the headless window system takes a tray, but shows nothing of it. */
private class RecordingShellCalls(private val supported: Boolean = true) : ShellCalls() {
    val calls = mutableListOf<String>()
    var tray: Rid? = null
    var menu: Rid? = null
    val notifications = mutableListOf<Triple<String, String, RNotificationUrgency>>()

    override fun trayIsSupported(): Boolean = supported && super.trayIsSupported()

    override fun createTray(): Rid = super.createTray().also { tray = it; calls += "create" }

    override fun destroyTray(tray: Rid) {
        calls += "destroy"
        super.destroyTray(tray)
    }

    override fun setTrayIcon(tray: Rid, icon: Pixels) {
        calls += "icon ${icon.width}x${icon.height}"
        super.setTrayIcon(tray, icon)
    }

    override fun setTrayTooltip(tray: Rid, tooltip: String?) {
        calls += "tooltip $tooltip"
        super.setTrayTooltip(tray, tooltip)
    }

    override fun setTrayMenu(tray: Rid, menu: Rid) {
        this.menu = menu
        calls += "menu"
        super.setTrayMenu(tray, menu)
    }

    override fun setTrayClicked(tray: Rid, userData: COpaquePointer?) {
        calls += if (userData == null) "unclicked" else "clicked"
        super.setTrayClicked(tray, userData)
    }

    override fun showNotification(title: String, message: String, urgency: RNotificationUrgency): Boolean {
        notifications += Triple(title, message, urgency)
        return super.showNotification(title, message, urgency)
    }
}

private object TestApplication : ApplicationScope {
    override fun exitApplication() = Rquit(0)
}

class TrayTest {
    private val calls = RecordingShellCalls()
    private val warnings = mutableListOf<String>()
    private val defaultSink = layoutWarningSink

    @BeforeTest
    fun record() {
        shellCalls = calls
        layoutWarningSink = { warnings += it }
    }

    @AfterTest
    fun restore() {
        shellCalls = ShellCalls()
        layoutWarningSink = defaultSink
    }

    private fun itemsOf(menu: Rid) = List(Rwidget_get_child_count(menu)) { Rmenu_item_get_text(Rwidget_get_child(menu, it)).toRavenString() }

    @Test
    fun theIconTooltipAndMenuReachRaven() = runRavenTest {
        var tooltip by mutableStateOf<String?>("Player")
        setApplicationContent {
            with(TestApplication) {
                Tray(painterResource("$TEST_RESOURCES/tray.png"), tooltip = tooltip) {
                    Item("Play", onClick = {})
                    Separator()
                    Item("Quit", onClick = {})
                }
            }
        }
        assertEquals(listOf("create", "clicked", "menu", "icon 16x16", "tooltip Player"), calls.calls)
        assertEquals(R_TRUE, Rtray_is_valid(calls.tray!!))
        assertEquals(listOf("Play", "", "Quit"), itemsOf(calls.menu!!))
        tooltip = null
        waitForIdle()
        assertEquals("tooltip null", calls.calls.last(), "only what changed")
        assertEquals(emptyList(), warnings)
    }

    @Test
    fun aMenuItemChosenCallsItsHandler() = runRavenTest {
        var chosen = ""
        setApplicationContent {
            with(TestApplication) {
                Tray(painterResource("$TEST_RESOURCES/tray.png")) {
                    Item("Play", onClick = { chosen += "play" })
                    Item("Quit", onClick = { chosen += "quit" })
                }
            }
            Window(onCloseRequest = {}, title = "Test") { Text("window", Modifier.testTag("text")) }
        }
        // The system shows a tray's menu; a window shows it here, as the same Raven menu.
        val window = Rwidget_get_window(onNodeWithTag("text").fetchWidget())
        Rmenu_open(calls.menu!!, window, 10f, 10f)
        waitForIdle()
        clickOpenMenuItem(window, "Quit")
        waitForIdle()
        assertEquals("quit", chosen)
    }

    @Test
    fun theAppKeepsRunningWithATrayAndNoWindow() = runRavenTest {
        var window by mutableStateOf(true)
        var tray by mutableStateOf(true)
        setApplicationContent {
            with(TestApplication) {
                if (tray) Tray(painterResource("$TEST_RESOURCES/tray.png"))
            }
            if (window) Window(onCloseRequest = {}, title = "Test") {}
        }
        window = false
        waitForIdle()
        assertEquals(R_TRUE, Ris_running(), "the tray keeps it running")
        // No window and no tray: Raven ends the app, which ends the frames the test draws.
        tray = false
        assertEquals("Raven quit", assertFailsWith<AssertionError> { waitForIdle() }.message)
        assertEquals("destroy", calls.calls.last())
        assertEquals(R_FALSE, Ris_running())
    }

    @Test
    fun aPainterThatIsntAnImageFileShowsNoIcon() = runRavenTest {
        setApplicationContent {
            with(TestApplication) { Tray(remember { BitmapPainter(ImageBitmap(2, 2, ByteArray(16) { -1 })) }) }
        }
        assertFalse(calls.calls.any { it.startsWith("icon") })
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().startsWith("Tray: a tray icon is read from its image file"), warnings.single())
    }

    @Test
    fun withNoTrayInTheSystemItShowsNothingAndWarns() = runRavenTest {
        val unsupported = RecordingShellCalls(supported = false)
        shellCalls = unsupported
        setApplicationContent { with(TestApplication) { Tray(painterResource("$TEST_RESOURCES/tray.png"), tooltip = "x") } }
        assertEquals(emptyList(), unsupported.calls)
        assertEquals(listOf("Tray: the system shows no tray icon here, so the tray shows nothing"), warnings)
    }

    @Test
    fun aNotificationIsRefusedHeadless() = runRavenTest {
        var state: TrayState? = null
        setApplicationContent {
            with(TestApplication) {
                state = rememberTrayState()
                Tray(painterResource("$TEST_RESOURCES/tray.png"), state = state!!)
            }
        }
        assertFalse(assertNotNull(state).sendNotification(Notification("Done", "The export finished", Notification.Type.Error)))
        assertFalse(TestApplication.sendNotification(Notification("Hello", "No tray needed")))
        assertEquals(
            listOf(
                Triple("Done", "The export finished", R_NOTIFICATION_CRITICAL),
                Triple("Hello", "No tray needed", R_NOTIFICATION_LOW),
            ),
            calls.notifications,
        )
    }
}
