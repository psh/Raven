package raven.compose.ui.test

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString
import raven.cinterop.RError
import raven.cinterop.RMemoryStats
import raven.cinterop.RSeverity
import raven.cinterop.R_FALSE
import raven.cinterop.R_MOD_ALT
import raven.cinterop.R_MOD_CTRL
import raven.cinterop.R_MOD_SHIFT
import raven.cinterop.R_MOD_SUPER
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_SEVERITY_ERROR
import raven.cinterop.R_SEVERITY_FATAL
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_MENU
import raven.cinterop.Rerror_set_callback
import raven.cinterop.Rid
import raven.cinterop.Rmemory_get_stats
import raven.cinterop.Rmenu_item_get_text
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_type
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_get_popup
import raven.cinterop.Rwindow_get_popup_count
import raven.cinterop.Rwindow_inject_drop
import raven.cinterop.Rwindow_inject_key
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.RavenHost
import raven.compose.ui.RavenNode
import raven.compose.ui.Window
import raven.compose.ui.input.key.Key
import raven.compose.ui.input.key.KeyShortcut
import raven.compose.ui.input.key.ravenKey
import raven.native.CallbackRegistry
import raven.native.toRavenString
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Runs [block] against a fresh headless Raven, with a fixed animation time step and live reload
 * off, so nothing reacts to the disk by itself, then shuts it down. The test fails if Raven
 * reported an error or the host recorded one, and, with [checkLeaks], if anything leaked: a block
 * Raven still holds, or a widget whose composable was never released, named in the failure.
 */
public fun runRavenTest(checkLeaks: Boolean = true, block: RavenTestScope.() -> Unit) {
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
    if (!checkLeaks) return
    assertEquals(0uL, ravenBlocksHeld(), "blocks Raven still holds after Rshutdown")
    val leaked = CallbackRegistry.liveTargets
    if (leaked.isNotEmpty()) fail("widgets left after Rshutdown, of ${leaked.joinToString { describe(it) }}")
}

/** A leaked target, as the composable that made it: its name, and its test tag if it has one. */
private fun describe(target: Any): String =
    if (target is RavenNode) target.name + (target.testTag?.let { " (tag \"$it\")" } ?: "") else target.toString()

/** What a test does to the UI it hosts: [runRavenTest]'s receiver. */
public class RavenTestScope internal constructor(
    /** The host the test runs: for compose-raven's own tests, which reach into Raven. */
    @property:InternalComposeRavenApi public val host: RavenHost,
) {
    /** Hosts [content] in a single window, then waits until it is idle. */
    public fun setContent(content: @Composable () -> Unit) {
        setApplicationContent { Window(onCloseRequest = {}, title = "Test", content = content) }
    }

    /**
     * Hosts [content] as `application { }` does, then waits until it is idle: the content makes
     * its own `Window`, with its title, island, key handling and app-wide theme, as the app's
     * `main` does. The finders search every window, its island and its dialogs.
     */
    public fun setApplicationContent(content: @Composable () -> Unit) {
        host.start(content)
        waitForIdle()
    }

    /**
     * Draws frames until no recomposition, snapshot apply or dispatched task is pending. Frame
     * awaiters don't count, so a running animation never stalls it.
     */
    public fun waitForIdle() {
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
    public fun checkFiles(): Int = host.checkFiles()

    /**
     * Draws one frame. Throws what ended the host, if a failure asked Raven to quit. The frame moves
     * compose-raven's animations on unless [MainTestClock.autoAdvance] is off.
     */
    public fun frame(): Unit = frame(advance = mainClock.autoAdvance)

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
    public val mainClock: MainTestClock = MainTestClock(this)

    /**
     * Drops [paths] and [text] on the window from another application, at its centre, as the user
     * would, and returns whether the window took them: see `Window(onExternalDrop)`.
     */
    public fun performExternalDrop(paths: List<String> = emptyList(), text: String? = null): Boolean {
        waitForIdle()
        val root = host.windowRoots.singleOrNull() ?: fail("${host.windowRoots.size} windows, expected one")
        val rect = widgetRect(root.widget)
        return memScoped {
            val cPaths = paths.map { it.cstr.ptr }.toCValues()
            Rwindow_inject_drop(Rwidget_get_window(root.widget), rect.x + rect.width / 2, rect.y + rect.height / 2, cPaths, paths.size, text)
        } == R_TRUE
    }

    /** The one node [matcher] accepts. */
    public fun onNode(matcher: NodeMatcher): NodeInteraction = NodeInteraction(this, matcher)

    /** Every node [matcher] accepts, in the order of the windows' trees. */
    public fun onAllNodes(matcher: NodeMatcher): NodeInteractionCollection = NodeInteractionCollection(this, matcher)

    /** The Text or the menu item showing [text], or a part of it with [substring]. */
    public fun onNodeWithText(text: String, substring: Boolean = false): NodeInteraction = onNode(hasText(text, substring))

    public fun onAllNodesWithText(text: String, substring: Boolean = false): NodeInteractionCollection = onAllNodes(hasText(text, substring))

    /** The node tagged [tag] with `Modifier.testTag`. */
    public fun onNodeWithTag(tag: String): NodeInteraction = onNode(hasTestTag(tag))

    public fun onAllNodesWithTag(tag: String): NodeInteractionCollection = onAllNodes(hasTestTag(tag))

    /**
     * The widget whose accessible name is [description]. Raven names a widget after its text when
     * nothing else names it, so Texts are left out: [onNodeWithText] finds them.
     */
    public fun onNodeWithContentDescription(description: String, substring: Boolean = false): NodeInteraction =
        onNode(hasContentDescription(description, substring))

    public fun onAllNodesWithContentDescription(description: String, substring: Boolean = false): NodeInteractionCollection =
        onAllNodes(hasContentDescription(description, substring))

    /**
     * Presses and releases [key] in the window, with the modifier keys given held, as the user
     * would: it goes to the focused widget, or to the window when nothing has the focus.
     */
    public fun pressKey(key: Key, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false, meta: Boolean = false) {
        waitForIdle()
        injectKey(singleWindow(), KeyShortcut(key, ctrl, shift, alt, meta))
    }

    /** Presses and releases [shortcut] in the window, as [pressKey] does. */
    public fun pressKey(shortcut: KeyShortcut): Unit = pressKey(shortcut.key, shortcut.ctrl, shortcut.shift, shortcut.alt, shortcut.meta)

    /**
     * Clicks the entry showing [text] in the menu open on top of the window, as the user would: a
     * menu the app built from data, such as a `ContextMenuArea`'s, which no finder sees. Fails
     * when no menu is open, or it has no such entry.
     */
    public fun clickOpenMenuItem(text: String) {
        waitForIdle()
        val window = singleWindow()
        val menu = openMenus(window).lastOrNull() ?: fail("no menu is open")
        val entries = List(Rwidget_get_child_count(menu)) { Rwidget_get_child(menu, it) }
        val index = entries.indexOfFirst { !isMenuSeparator(it) && Rmenu_item_get_text(it).toRavenString() == text }
        if (index < 0) fail("the open menu has no entry \"$text\"")
        val (x, y) = menuEntryCentre(menu, index)
        Rwindow_inject_mouse_move(window, x, y)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
    }

    /** How many menus are open on top of the window now. */
    public fun openMenuCount(): Int {
        waitForIdle()
        return openMenus().size
    }

    /** The menus open in [window] now, bottom to top. */
    @InternalComposeRavenApi
    public fun openMenus(window: Rid = singleWindow()): List<Rid> =
        List(Rwindow_get_popup_count(window)) { Rwindow_get_popup(window, it) }.filter { Rwidget_get_type(it) == R_WIDGET_MENU }

    private fun singleWindow(): Rid {
        val root = host.windowRoots.singleOrNull() ?: fail("${host.windowRoots.size} windows, expected one")
        return Rwidget_get_window(root.widget)
    }

    /** The nodes of every open window, its island and its dialogs that [matches] accepts, content nodes left out. */
    @InternalComposeRavenApi
    public fun findNodes(matches: (RavenNode) -> Boolean): List<RavenNode> {
        val found = mutableListOf<RavenNode>()
        fun visit(node: RavenNode) {
            if (!node.isContentNode && matches(node)) found += node
            node.children.forEach(::visit)
        }
        (host.windowRoots + host.islandRoots + host.dialogRoots).forEach(::visit)
        return found
    }

    /** The nodes from a window's root down to [node], both included. */
    internal fun pathTo(node: RavenNode): List<RavenNode> {
        fun search(from: RavenNode): List<RavenNode>? =
            if (from === node) listOf(from) else from.children.firstNotNullOfOrNull { search(it) }?.let { listOf(from) + it }
        return (host.windowRoots + host.islandRoots + host.dialogRoots).firstNotNullOfOrNull { search(it) } ?: fail("the node is in no window")
    }
}

/** The clock of a [RavenTestScope], which advances as it draws frames. */
public class MainTestClock internal constructor(private val scope: RavenTestScope) {
    /** The time of the last frame drawn, in milliseconds since the test started. */
    public val currentTime: Long get() = (scope.framesDrawn * ANIMATION_TIME_STEP * 1000).toLong()

    /**
     * Whether every frame moves compose-raven's animations on, [waitForIdle][RavenTestScope.waitForIdle]'s
     * included; true by default. Off, only [advanceTimeByFrame] and [advanceTimeBy] do, and waiting
     * for idle recomposes with the animations held where they are. Raven's own animations go on.
     */
    public var autoAdvance: Boolean = true

    /** Draws one frame, one time step on. */
    public fun advanceTimeByFrame(): Unit = scope.frame(advance = true)

    /** Draws frames until at least [milliseconds] have passed: a whole number of time steps. */
    public fun advanceTimeBy(milliseconds: Long) {
        require(milliseconds >= 0) { "time only goes forward, not by $milliseconds ms" }
        val frames = ceil(milliseconds / (ANIMATION_TIME_STEP * 1000) - 1e-9).toInt()
        repeat(frames) { scope.frame(advance = true) }
    }
}

/** Presses and releases [shortcut]'s key in [window], its modifier keys held. */
internal fun injectKey(window: Rid, shortcut: KeyShortcut) {
    var modifiers = 0u
    if (shortcut.ctrl) modifiers = modifiers or R_MOD_CTRL
    if (shortcut.shift) modifiers = modifiers or R_MOD_SHIFT
    if (shortcut.alt) modifiers = modifiers or R_MOD_ALT
    if (shortcut.meta) modifiers = modifiers or R_MOD_SUPER
    Rwindow_inject_key(window, shortcut.key.ravenKey, R_TRUE, modifiers)
    Rwindow_inject_key(window, shortcut.key.ravenKey, R_FALSE, modifiers)
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
