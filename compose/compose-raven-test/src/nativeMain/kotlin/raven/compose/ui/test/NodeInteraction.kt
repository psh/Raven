package raven.compose.ui.test

import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_VISIBLE
import raven.cinterop.Rid
import raven.cinterop.Rwidget_get_parent
import raven.cinterop.Rwidget_get_visibility
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.RavenNode
import raven.compose.ui.graphics.Color
import raven.compose.ui.input.key.KeyShortcut
import kotlin.test.fail

/**
 * One node a finder selects: the one [matcher] accepts, or the one at [index] of all it accepts.
 * Every use waits for the UI to be idle, then finds it again. The reads and actions are
 * compose-raven-ui-test's, shared with the UI tools; their failures are reported as test failures.
 */
public class NodeInteraction internal constructor(
    private val scope: RavenTestScope,
    private val matcher: NodeMatcher,
    private val index: Int? = null,
) {
    private val selector: String = if (index == null) matcher.description else "${matcher.description} [$index]"

    /** The node, found again once the UI is idle. */
    private fun found(): FoundNode {
        scope.waitForIdle()
        return reported { scope.host.findNode(matcher, index) }
    }

    /** Runs [action] on the node, found again once the UI is idle. */
    private inline fun <T> on(action: FoundNode.() -> T): T {
        val node = found()
        return reported { node.action() }
    }

    /** The one node that matches; fails if there is none, or more than one. */
    @InternalComposeRavenApi
    public fun fetchNode(): RavenNode = found().node

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
    public fun fetchText(): String = on { fetchText() }

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
        val checked = on { fetchChecked() }
        if (checked != expected) fail("the node with $selector is ${if (checked) "checked" else "not checked"}")
    }

    public fun assertIsEnabled(): NodeInteraction = apply { assertEnabled(true) }

    public fun assertIsNotEnabled(): NodeInteraction = apply { assertEnabled(false) }

    private fun assertEnabled(expected: Boolean) {
        val enabled = on { fetchEnabled() }
        if (enabled != expected) fail("the node with $selector is ${if (enabled) "enabled" else "not enabled"}")
    }

    public fun assertIsFocused(): NodeInteraction = apply { assertFocused(true) }

    public fun assertIsNotFocused(): NodeInteraction = apply { assertFocused(false) }

    private fun assertFocused(expected: Boolean) {
        val focused = on { fetchFocused() }
        if (focused != expected) fail("the node with $selector is ${if (focused) "focused" else "not focused"}")
    }

    /** Fails unless the node is a combo box with the item at [expected] selected, or none at -1. */
    public fun assertSelectedIndex(expected: Int): NodeInteraction = apply {
        val selected = on { fetchSelectedIndex() }
        if (selected != expected) fail("the node with $selector has item $selected selected, expected $expected")
    }

    /** Fails unless the node is a slider, a spin box or a progress bar holding [expected]. */
    public fun assertValueEquals(expected: Double): NodeInteraction = apply {
        val value = on { fetchValue() }
        if (value != expected) fail("the node with $selector holds $value, expected $expected")
    }

    /**
     * Picks the item at [index] of the node's combo box as the user would with the keyboard: a
     * click opens its list, Home and Down move to the item, and Enter picks it.
     */
    public fun performSelection(index: Int): NodeInteraction = apply { on { performSelection(index) } }

    /**
     * Types [value] into the node's spin box as the user would: a click starts typing over the
     * value, and Enter takes what was typed.
     */
    public fun performValueInput(value: Double): NodeInteraction = apply { on { performValueInput(value) } }

    /**
     * Presses and releases the left button at the centre of the node's widget, as the user would.
     * A menu item is chosen from its menu, opened down to it.
     */
    public fun performClick(): NodeInteraction = apply { on { performClick() } }

    /**
     * Types [text] into the node's widget, one character at a time with no frame between them. It
     * clicks the widget first unless it already has focus, so the caret stays where it was.
     */
    public fun performTextInput(text: String): NodeInteraction = apply { on { performTextInput(text) } }

    /**
     * Replaces the text of the node's text field with [text], as the user would: it focuses the
     * field, selects all of it, and types [text] over it, or deletes it for an empty one.
     */
    public fun performTextReplacement(text: String): NodeInteraction = apply { on { performTextReplacement(text) } }

    /** Focuses the node's widget, then presses and releases [shortcut] there. */
    public fun performKeyPress(shortcut: KeyShortcut): NodeInteraction = apply { on { performKeyPress(shortcut) } }

    /**
     * Scrolls the node's scroll container (a `VerticalScroll`, a `HorizontalScroll` or a lazy list)
     * by [dx] and [dy] from where it is, then draws a frame, so the states following it read it.
     */
    public fun performScrollBy(dx: Float = 0f, dy: Float = 0f): NodeInteraction = apply {
        on { performScrollBy(dx, dy) }
        scope.frame()
    }

    /**
     * Types [color]'s code into the node's colour picker and presses Enter, as the user would; a
     * popup picker is opened first, with the keyboard, and its popup closed after. A compact picker
     * shows no code, so it fails.
     */
    public fun performColorChange(color: Color): NodeInteraction = apply { on { performColorChange(color) } }

    /**
     * Scrolls every scroll box above the node's widget until it is in view
     * (`Rwidget_scroll_into_view`), then draws a frame, so the states following them read it.
     */
    public fun performScrollTo(): NodeInteraction = apply {
        on { performScrollTo() }
        scope.frame()
    }

    /**
     * Scrolls the node's lazy list so its item at [index] is at the top (or the start), as
     * `scrollToItem` does, or the node's ListView just enough to show its row at [index]
     * (`Rlist_view_scroll_to_item`), then draws a frame.
     */
    public fun performScrollToIndex(index: Int): NodeInteraction = apply {
        on { performScrollToIndex(index) }
        scope.frame()
    }

    /** Double-clicks the centre of the node's widget with the left button, as the user would. */
    public fun performDoubleClick(): NodeInteraction = apply { on { performDoubleClick() } }

    /** Presses and releases the right button at the centre of the node's widget, as the user would. */
    public fun performRightClick(): NodeInteraction = apply { on { performRightClick() } }

    /** Moves the pointer onto the centre of the node's widget. */
    public fun performMouseEnter(): NodeInteraction = apply { on { performMouseEnter() } }

    /** Moves the pointer out of the node's window, so it leaves the node's widget. */
    public fun performMouseExit(): NodeInteraction = apply { on { performMouseExit() } }
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

/** Runs [block], reporting a [NodeException] as a test failure with its message. */
internal inline fun <T> reported(block: () -> T): T =
    try {
        block()
    } catch (e: NodeException) {
        fail(e.message)
    }
