@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.foundation

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import raven.cinterop.RError
import raven.cinterop.RMemoryStats
import raven.cinterop.RSeverity
import raven.cinterop.RKey
import raven.cinterop.R_KEY_DELETE
import raven.cinterop.R_KEY_DOWN
import raven.cinterop.R_KEY_END
import raven.cinterop.R_KEY_ENTER
import raven.cinterop.R_KEY_HOME
import raven.cinterop.R_SEVERITY_ERROR
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_SEVERITY_FATAL
import raven.cinterop.R_TRUE
import raven.cinterop.R_FALSE
import raven.cinterop.R_WIDGET_CHECKBOX
import raven.cinterop.R_WIDGET_COLOR_PICKER
import raven.cinterop.R_METRIC_ROW_HEIGHT
import raven.cinterop.R_ROLE_SEPARATOR
import raven.cinterop.R_WIDGET_COMBO_BOX
import raven.cinterop.R_WIDGET_LABEL
import raven.cinterop.R_WIDGET_MENU
import raven.cinterop.R_WIDGET_MENU_ITEM
import raven.cinterop.R_WIDGET_SPIN_BOX
import raven.cinterop.R_WIDGET_TEXT_INPUT
import raven.cinterop.Rcheckbox_is_checked
import raven.cinterop.Rcombo_box_get_item_count
import raven.cinterop.Rcombo_box_get_selected
import raven.cinterop.Rid
import raven.cinterop.Rlabel_get_text
import raven.cinterop.Rmenu_item_get_text
import raven.cinterop.Rmenu_item_is_checked
import raven.cinterop.Rmenu_open
import raven.cinterop.Rspin_box_get_value
import raven.cinterop.Rtext_input_set_text
import raven.cinterop.Rwidget_get_accessible_name
import raven.cinterop.Rwidget_get_accessible_role
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_metric
import raven.cinterop.Rwidget_get_rect
import raven.cinterop.Rwidget_get_type
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwidget_focus
import raven.cinterop.Rwindow_inject_key
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.cinterop.Rwindow_close_popups
import raven.cinterop.Rwindow_get_popup
import raven.cinterop.Rwindow_get_popup_count
import raven.cinterop.Rwindow_inject_text
import raven.cinterop.Rwindow_inject_drop
import raven.cinterop.Rwidget_is_focused
import raven.cinterop.Rerror_set_callback
import raven.cinterop.Rmemory_get_stats
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.RavenHost
import raven.compose.ui.RavenNode
import raven.compose.ui.Window
import raven.compose.ui.graphics.Color
import raven.native.CallbackRegistry
import raven.native.toRavenString
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Runs [block] against a fresh headless Raven, with a fixed animation time step and live reload
 * off, so nothing reacts to the disk by itself, then shuts it down. The test fails if Raven reported an error, if the host recorded one, or if anything leaked:
 * a block Raven still holds, or a widget reference still alive.
 */
internal fun runRavenTest(block: RavenTestScope.() -> Unit) {
    ravenErrors.clear()
    Rerror_set_callback(collectRavenError)
    val host = RavenHost(headless = true, animationTimeStep = ANIMATION_TIME_STEP, liveReload = false)
    try {
        try {
            RavenTestScope(host).block()
        } finally {
            host.close()
        }
        host.error?.let { throw it }
    } finally {
        Rerror_set_callback(null)
    }
    assertEquals(emptyList(), ravenErrors.toList(), "errors reported by Raven")
    assertEquals(0uL, ravenBlocksHeld(), "blocks Raven still holds after Rshutdown")
    assertEquals(0, CallbackRegistry.liveCount, "widget references left after Rshutdown")
}

internal class RavenTestScope(val host: RavenHost) {
    /** Hosts [content] in a single window, then waits until it is idle. */
    fun setContent(content: @Composable () -> Unit) {
        host.start { Window(onCloseRequest = {}, title = "Test", content = content) }
        waitForIdle()
    }

    /**
     * Draws frames until no recomposition, snapshot apply or dispatched task is pending. Frame
     * awaiters don't count, so a running animation never stalls it.
     */
    fun waitForIdle() {
        repeat(MAX_IDLE_FRAMES) {
            if (host.isIdle()) return
            frame()
        }
        fail("not idle after $MAX_IDLE_FRAMES frames")
    }

    /**
     * Reloads the files Raven loaded (theme files) that changed on disk, as live reload would, and
     * returns how many changed. Live reload is off in tests, so this is the only way they reload.
     */
    fun checkFiles(): Int = host.checkFiles()

    /**
     * Draws one frame. Throws what ended the host, if a failure asked Raven to quit. The frame moves
     * compose-raven's animations on unless [MainTestClock.autoAdvance] is off.
     */
    fun frame() = frame(advance = mainClock.autoAdvance)

    internal fun frame(advance: Boolean) {
        host.holdsFrameTime = !advance
        if (advance) framesDrawn++
        if (!host.frame()) throw host.error ?: AssertionError("Raven quit")
    }

    /** How many frames have moved the clock on, each one animation time step later than the one before. */
    internal var framesDrawn = 0
        private set

    /**
     * The test's clock: time passes only as frames are drawn, one fixed animation time step a frame,
     * for Raven's animations and compose-raven's alike. [waitForIdle] draws frames too, so it moves
     * the clock on, and an animation whose value the composition reads recomposes every frame, so
     * waiting for idle runs it to its end; turn [MainTestClock.autoAdvance] off to look at it mid-way.
     */
    val mainClock: MainTestClock = MainTestClock(this)

    /**
     * Drops [paths] and [text] on the window from another application, at its centre, as the user
     * would, and returns whether the window took them: see `Window(onExternalDrop)`.
     */
    fun performExternalDrop(paths: List<String> = emptyList(), text: String? = null): Boolean {
        waitForIdle()
        val root = host.windowRoots.singleOrNull() ?: fail("${host.windowRoots.size} windows, expected one")
        val rect = widgetRect(root.widget)
        return memScoped {
            val cPaths = paths.map { it.cstr.ptr }.toCValues()
            Rwindow_inject_drop(Rwidget_get_window(root.widget), rect.x + rect.width / 2, rect.y + rect.height / 2, cPaths, paths.size, text)
        } == R_TRUE
    }

    /** The Text or the menu item showing [text], or a part of it with [substring]. */
    fun onNodeWithText(text: String, substring: Boolean = false): NodeInteraction =
        NodeInteraction(this, "text \"$text\"") { node ->
            val shown = labelText(node) ?: menuItemText(node) ?: return@NodeInteraction false
            if (substring) text in shown else shown == text
        }

    /** The node tagged [tag] with `Modifier.testTag`. */
    fun onNodeWithTag(tag: String): NodeInteraction = NodeInteraction(this, "tag \"$tag\"") { it.testTag == tag }

    /**
     * The widget whose accessible name is [description]. Raven names a widget after its text when
     * nothing else names it, so Texts are left out: [onNodeWithText] finds them.
     */
    fun onNodeWithContentDescription(description: String, substring: Boolean = false): NodeInteraction =
        NodeInteraction(this, "content description \"$description\"") { node ->
            if (labelText(node) != null) return@NodeInteraction false
            val name = Rwidget_get_accessible_name(node.widget).toRavenString()
            if (substring) description in name else name == description
        }

    /** The nodes of every open window that [matches] accepts, content nodes left out. */
    internal fun findNodes(matches: (RavenNode) -> Boolean): List<RavenNode> {
        val found = mutableListOf<RavenNode>()
        fun visit(node: RavenNode) {
            if (!node.isContentNode && matches(node)) found += node
            node.children.forEach(::visit)
        }
        host.windowRoots.forEach(::visit)
        return found
    }

    /** The nodes from a window's root down to [node], both included. */
    internal fun pathTo(node: RavenNode): List<RavenNode> {
        fun search(from: RavenNode): List<RavenNode>? =
            if (from === node) listOf(from) else from.children.firstNotNullOfOrNull { search(it) }?.let { listOf(from) + it }
        return host.windowRoots.firstNotNullOfOrNull { search(it) } ?: fail("the node is in no window")
    }

    private fun labelText(node: RavenNode): String? =
        if (Rwidget_get_type(node.widget) == R_WIDGET_LABEL) Rlabel_get_text(node.widget).toRavenString() else null

    private fun menuItemText(node: RavenNode): String? =
        if (Rwidget_get_type(node.widget) == R_WIDGET_MENU_ITEM && !isMenuSeparator(node.widget)) {
            Rmenu_item_get_text(node.widget).toRavenString()
        } else {
            null
        }
}

/** The clock of a [RavenTestScope], which advances as it draws frames. */
internal class MainTestClock(private val scope: RavenTestScope) {
    /** The time of the last frame drawn, in milliseconds since the test started. */
    val currentTime: Long get() = (scope.framesDrawn * ANIMATION_TIME_STEP * 1000).toLong()

    /**
     * Whether every frame moves compose-raven's animations on, [waitForIdle][RavenTestScope.waitForIdle]'s
     * included; true by default. Off, only [advanceTimeByFrame] and [advanceTimeBy] do, and waiting
     * for idle recomposes with the animations held where they are. Raven's own animations go on.
     */
    var autoAdvance: Boolean = true

    /** Draws one frame, one time step on. */
    fun advanceTimeByFrame() = scope.frame(advance = true)

    /** Draws frames until at least [milliseconds] have passed: a whole number of time steps. */
    fun advanceTimeBy(milliseconds: Long) {
        require(milliseconds >= 0) { "time only goes forward, not by $milliseconds ms" }
        val frames = ceil(milliseconds / (ANIMATION_TIME_STEP * 1000) - 1e-9).toInt()
        repeat(frames) { scope.frame(advance = true) }
    }
}

/** One node a finder selects. Every use waits for the UI to be idle, then finds it again. */
internal class NodeInteraction(
    private val scope: RavenTestScope,
    private val selector: String,
    private val matches: (RavenNode) -> Boolean,
) {
    /** The one node that matches; fails if there is none, or more than one. */
    fun fetchNode(): RavenNode {
        scope.waitForIdle()
        val found = scope.findNodes(matches)
        if (found.isEmpty()) fail("no node with $selector")
        if (found.size > 1) fail("${found.size} nodes with $selector, expected one")
        return found.single()
    }

    fun fetchWidget(): Rid = fetchNode().widget

    fun assertExists(): NodeInteraction = apply { fetchNode() }

    fun assertDoesNotExist(): NodeInteraction = apply {
        scope.waitForIdle()
        val found = scope.findNodes(matches)
        if (found.isNotEmpty()) fail("${found.size} nodes with $selector, expected none")
    }

    fun assertIsChecked(): NodeInteraction = apply { assertChecked(true) }

    fun assertIsNotChecked(): NodeInteraction = apply { assertChecked(false) }

    private fun assertChecked(expected: Boolean) {
        val widget = fetchWidget()
        val checked = when (Rwidget_get_type(widget)) {
            R_WIDGET_CHECKBOX -> Rcheckbox_is_checked(widget) == R_TRUE
            R_WIDGET_MENU_ITEM -> Rmenu_item_is_checked(widget) == R_TRUE
            else -> fail("the node with $selector can't be checked")
        }
        if (checked != expected) fail("the node with $selector is ${if (checked) "checked" else "not checked"}")
    }

    /** Fails unless the node is a combo box with the item at [expected] selected, or none at -1. */
    fun assertSelectedIndex(expected: Int): NodeInteraction = apply {
        val selected = Rcombo_box_get_selected(comboBox())
        if (selected != expected) fail("the node with $selector has item $selected selected, expected $expected")
    }

    /** Fails unless the node is a spin box holding [expected]. */
    fun assertValue(expected: Double): NodeInteraction = apply {
        val widget = fetchWidget()
        if (Rwidget_get_type(widget) != R_WIDGET_SPIN_BOX) fail("the node with $selector has no number value")
        val value = Rspin_box_get_value(widget)
        if (value != expected) fail("the node with $selector holds $value, expected $expected")
    }

    /**
     * Picks the item at [index] of the node's combo box as the user would with the keyboard: a
     * click opens its list, Home and Down move to the item, and Enter picks it.
     */
    fun performSelection(index: Int): NodeInteraction = apply {
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
    fun performValueInput(value: Double): NodeInteraction = apply {
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
    fun performClick(): NodeInteraction = apply {
        val node = fetchNode()
        if (Rwidget_get_type(node.widget) == R_WIDGET_MENU_ITEM) chooseMenuItem(node) else click(node.widget)
    }

    /**
     * Types [text] into the node's widget, one character at a time with no frame between them. It
     * clicks the widget first unless it already has focus, so the caret stays where it was.
     */
    fun performTextInput(text: String): NodeInteraction = apply {
        val widget = fetchWidget()
        if (Rwidget_is_focused(widget) != R_TRUE) click(widget)
        Rwindow_inject_text(Rwidget_get_window(widget), text)
    }

    /**
     * Types [color]'s code into the node's colour picker and presses Enter, as the user would; a
     * popup picker is opened first, with the keyboard, and its popup closed after. A compact picker
     * shows no code, so it fails. The code is typed after an `x` that is then deleted, so the
     * picker sees no other colour on the way (`#F00` is one): it reports [color] once.
     */
    fun performColorChange(color: Color): NodeInteraction = apply {
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
     * sub-menu opens as the pointer moves onto its entry. Any menu still open is closed at the end.
     */
    private fun chooseMenuItem(item: RavenNode) {
        val path = scope.pathTo(item)
        val menus = path.dropLast(1).takeLastWhile { Rwidget_get_type(it.widget) == R_WIDGET_MENU }
        if (menus.isEmpty()) fail("the menu item with $selector is in no menu")
        val bar = path[path.size - menus.size - 2]
        val window = Rwidget_get_window(bar.widget)
        val barRect = widgetRect(bar.widget)
        Rmenu_open(menus.first().widget, window, barRect.x, barRect.y + barRect.height)
        for ((i, menu) in menus.withIndex()) {
            val entry = menus.getOrNull(i + 1) ?: item
            val (x, y) = menuEntryCentre(menu, menu.children.indexOf(entry))
            Rwindow_inject_mouse_move(window, x, y)
        }
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
        Rwindow_close_popups(window)
    }

    /**
     * Where the entry at [index] of an open [menu] is, in its window. Raven doesn't report it, so
     * this follows `src/widgets/menu.c`: the entries are stacked inside a padding, each a row
     * high plus 2, and a separator is a fixed height.
     */
    private fun menuEntryCentre(menu: RavenNode, index: Int): Pair<Float, Float> {
        val rect = widgetRect(menu.widget)
        fun height(entry: RavenNode) =
            if (isMenuSeparator(entry.widget)) MENU_SEPARATOR_HEIGHT else Rwidget_get_metric(menu.widget, R_METRIC_ROW_HEIGHT) + 2f
        val top = MENU_PADDING + menu.children.take(index).sumOf { height(it).toDouble() }.toFloat()
        return rect.x + rect.width / 2 to rect.y + top + height(menu.children[index]) / 2
    }
}

/** A menu separator: compose-raven gives its MenuItem the separator's accessible role. */
private fun isMenuSeparator(widget: Rid): Boolean = Rwidget_get_accessible_role(widget) == R_ROLE_SEPARATOR

// The layout of an open menu, from src/widgets/menu.c.
private const val MENU_PADDING = 4f
private const val MENU_SEPARATOR_HEIGHT = 7f

/** Where a widget is in its window, from `Rwidget_get_rect`. */
internal data class WidgetRect(val x: Float, val y: Float, val width: Float, val height: Float)

internal fun widgetRect(widget: Rid): WidgetRect = memScoped {
    val values = List(4) { alloc<FloatVar>() }
    Rwidget_get_rect(widget, values[0].ptr, values[1].ptr, values[2].ptr, values[3].ptr)
    WidgetRect(values[0].value, values[1].value, values[2].value, values[3].value)
}

/** One frame at 60 Hz. */
private const val ANIMATION_TIME_STEP = 1.0 / 60

private const val MAX_IDLE_FRAMES = 100

private val ravenErrors = mutableListOf<String>()

private val collectRavenError = staticCFunction { _: RError, severity: RSeverity, detail: CPointer<ByteVar>? ->
    if (severity == R_SEVERITY_ERROR || severity == R_SEVERITY_FATAL) ravenErrors += detail?.toKString() ?: "?"
}

private fun ravenBlocksHeld(): ULong = memScoped {
    val stats = alloc<RMemoryStats>()
    Rmemory_get_stats(stats.ptr)
    stats.allocation_count
}
