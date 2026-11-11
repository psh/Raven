@file:OptIn(ExperimentalAtomicApi::class, ObsoleteWorkersApi::class)

package raven.spike.nested

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.launch
import platform.posix.usleep
import raven.cinterop.RFileDialogDesc
import raven.cinterop.R_FILE_DIALOG_RAVEN
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_KEY_ESCAPE
import raven.cinterop.R_MESSAGE_OK_CANCEL
import raven.cinterop.R_MESSAGE_RESULT_CANCEL
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_TRUE
import raven.cinterop.R_FALSE
import raven.cinterop.R_WIDGET_LABEL
import raven.cinterop.R_WIDGET_VBOX
import raven.cinterop.Rcheckbox_is_checked
import raven.cinterop.Rdialog_run
import raven.cinterop.Rfile_dialog_get_result_count
import raven.cinterop.Rfile_dialog_open
import raven.cinterop.Rid
import raven.cinterop.Rlabel_get_text
import raven.cinterop.Rlabel_set_text
import raven.cinterop.Rmessage_box
import raven.cinterop.Rquit
import raven.cinterop.Rset_animations_enabled
import raven.cinterop.Rtask_post
import raven.cinterop.Rwake_up
import raven.cinterop.Rwidget_create
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwidget_insert_child
import raven.cinterop.Rwindow_has_dialog
import raven.cinterop.Rwindow_inject_key
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.compose.foundation.Button
import raven.compose.foundation.Checkbox
import raven.compose.foundation.RavenTestScope
import raven.compose.foundation.Text
import raven.compose.foundation.layout.Column
import raven.compose.foundation.runRavenTest
import raven.compose.foundation.widgetRect
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.testTag
import raven.native.toRavenString
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Can Compose recompose safely inside the nested frames that Raven's blocking dialogs run? Each
 * test opens one from a coroutine on RavenDispatcher, as `native = true` would, and checks what
 * happens while it waits. Results: `../README.md`.
 */
class NestedFramesSpike {
    @Test
    fun recomposesInsideAMessageBox() = recomposesInside(BlockingCall.MessageBox)

    @Test
    fun recomposesInsideDialogRun() = recomposesInside(BlockingCall.DialogRun)

    @Test
    fun recomposesInsideAFileDialog() = recomposesInside(BlockingCall.FileDialog)

    /**
     * While the dialog waits, state writes recompose and apply, Text follows, and a Checkbox in
     * another window stays controlled: a refused click reverts, an accepted one stays. Every apply
     * that runs then has no other apply under it on the stack.
     */
    private fun recomposesInside(call: BlockingCall) = runRavenTest {
        val probe = Probe()
        var count by mutableStateOf(0)
        var checked by mutableStateOf(false)
        var acceptChecks = false
        var result: Int? = null
        val steps = listOf(
            { probe.expect(Rwindow_has_dialog(window(0)) == R_TRUE) { "no dialog shown" }; count = 1 },
            { probe.expectText(widget("count"), "count 1"); count = 2 },
            { probe.expectText(widget("count"), "count 2"); count = 3 },
            { probe.expectText(widget("count"), "count 3"); click(widget("check")) },
            {},
            {
                probe.expect(Rcheckbox_is_checked(widget("check")) == R_FALSE) { "a refused check stayed checked" }
                probe.expect(!checked) { "the app's state changed" }
                acceptChecks = true
                click(widget("check"))
            },
            {},
            {
                probe.expect(Rcheckbox_is_checked(widget("check")) == R_TRUE) { "an accepted check was reverted" }
                probe.expectText(widget("checked"), "checked true")
                escape(window(0))
            },
        )
        host.start {
            Window(onCloseRequest = {}, title = "Main") {
                val scope = rememberCoroutineScope()
                Column {
                    Text("count $count", Modifier.testTag("count"))
                    Button(
                        onClick = { scope.launch { result = probe.blocking { call.open(window(0)) } } },
                        Modifier.testTag("open"),
                    ) { Text("Open") }
                }
                Driver(probe, steps)
            }
            Window(onCloseRequest = {}, title = "Other") {
                Column {
                    Checkbox(checked, { if (acceptChecks) checked = it }, Modifier.testTag("check"))
                    Text("checked $checked", Modifier.testTag("checked"))
                    // Reading count recomposes this scope each step: its apply runs inside the dialog.
                    Text("seen $count")
                    SideEffect { probe.onNestedApply() }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("open").performClick()
        frame()
        waitForIdle()

        probe.assertClean()
        assertEquals(steps.size, probe.stepsRun, "steps run inside the dialog")
        assertTrue(probe.nestedApplies >= 3, "applies inside the dialog: ${probe.nestedApplies}")
        assertEquals(if (call == BlockingCall.FileDialog) 0 else R_MESSAGE_RESULT_CANCEL.toInt(), result)
        assertTrue(checked)
        onNodeWithTag("count").let { assertEquals("count 3", Rlabel_get_text(it.fetchWidget()).toRavenString()) }
    }

    @Test
    fun theNestedLoopWakesForQueuedWork() = theNestedLoopWakes(animations = true)

    @Test
    fun theNestedLoopWakesForQueuedWorkWithoutAnimations() = theNestedLoopWakes(animations = false)

    /**
     * With no frame awaiter to keep the loop spinning, the nested loop still wakes for work that
     * Raven can't see. First the click's own write: it is dispatched just after the dialog's
     * launch, so its snapshot apply waits in RavenDispatcher's queue behind the blocked block, and
     * no drain task is posted for it. Then a write from another thread while the dialog waits.
     */
    private fun theNestedLoopWakes(animations: Boolean) = runRavenTest {
        val probe = Probe()
        val shared = Shared()
        var clicks by mutableStateOf(0)
        var result: Int? = null
        host.start {
            Window(onCloseRequest = {}, title = "Main") {
                val scope = rememberCoroutineScope()
                Column {
                    Button(onClick = {
                        scope.launch { result = probe.blocking(shared) { BlockingCall.MessageBox.open(window(0)) } }
                        clicks++
                    }, Modifier.testTag("open")) { Text("Open") }
                    val seenClicks = clicks
                    val fromThread = shared.fromThread.value
                    SideEffect {
                        if (probe.depth > 0 && seenClicks == 1) shared.clickSeen.compareAndSet(0L, shared.now())
                        if (probe.depth > 0 && fromThread == 1) shared.threadWriteSeen.compareAndSet(0L, shared.now())
                    }
                }
            }
        }
        Rset_animations_enabled(if (animations) R_TRUE else R_FALSE)
        waitForIdle()
        escapeWindow.store(window(0).toInt())
        val worker = Worker.start()
        val done = worker.execute(TransferMode.SAFE, { shared }) { it.watchWhileTheDialogWaits() }
        onNodeWithTag("open").performClick()
        frame()
        waitForIdle()
        done.result
        worker.requestTermination().result

        probe.assertClean()
        assertEquals(emptyList(), shared.lostWakeUps(), "work the nested loop slept through")
        assertEquals(R_MESSAGE_RESULT_CANCEL.toInt(), result)
        println("nested-frames (animations ${if (animations) "on" else "off"}): ${shared.timings()}")
    }

    /** Compose removes the window under the dialog: the call returns, and nothing is left behind. */
    @Test
    fun theWindowUnderTheDialogLeavesTheComposition() = runRavenTest {
        val probe = Probe()
        var showMain by mutableStateOf(true)
        var result: Int? = null
        host.start {
            if (showMain) {
                Window(onCloseRequest = {}, title = "Main") {
                    val scope = rememberCoroutineScope()
                    Button(
                        onClick = { scope.launch { result = probe.blocking { BlockingCall.MessageBox.open(window(0)) } } },
                        Modifier.testTag("open"),
                    ) { Text("Open") }
                }
            }
            Window(onCloseRequest = {}, title = "Other") {
                Driver(probe, listOf({ showMain = false }, {}, {}))
            }
        }
        waitForIdle()
        onNodeWithTag("open").performClick()
        frame()
        waitForIdle()

        probe.assertClean()
        assertEquals(R_MESSAGE_RESULT_CANCEL.toInt(), result)
        assertEquals(1, host.windowRoots.size)
    }

    /** exitApplication while the dialog waits: the call returns and the loop ends. */
    @Test
    fun quittingEndsTheDialog() = runRavenTest {
        val probe = Probe()
        var result: Int? = null
        host.start {
            Window(onCloseRequest = {}, title = "Main") {
                val scope = rememberCoroutineScope()
                Button(
                    onClick = { scope.launch { result = probe.blocking { BlockingCall.MessageBox.open(window(0)) } } },
                    Modifier.testTag("open"),
                ) { Text("Open") }
                Driver(probe, listOf({ Rquit(0) }))
            }
        }
        waitForIdle()
        onNodeWithTag("open").performClick()
        assertFalse(host.frame(), "the loop goes on after Rquit")

        probe.assertClean()
        assertEquals(R_MESSAGE_RESULT_CANCEL.toInt(), result)
    }

    /**
     * A second dialog opened while the first waits runs inside the first one's frames. Closing the
     * first doesn't resume its coroutine: that waits until the second has closed too.
     */
    @Test
    fun stackedDialogsReturnInReverseOrder() = runRavenTest {
        val probe = Probe()
        val returned = mutableListOf<String>()
        var first: Int? = null
        host.start {
            Window(onCloseRequest = {}, title = "Main") {
                val scope = rememberCoroutineScope()
                Button(onClick = {
                    scope.launch { probe.blocking { BlockingCall.MessageBox.open(window(0)) }.also { first = it; returned += "first" } }
                }, Modifier.testTag("open")) { Text("Open") }
            }
            Window(onCloseRequest = {}, title = "Other") {
                val scope = rememberCoroutineScope()
                Driver(probe, listOf(
                    { scope.launch { probe.blocking { BlockingCall.MessageBox.open(window(1)) }; returned += "second" } },
                    { probe.expect(probe.depth == 2) { "the second dialog isn't nested: depth ${probe.depth}" } },
                    { escape(window(0)) },
                    {},
                    {
                        probe.expect(Rwindow_has_dialog(window(0)) == R_FALSE) { "the first dialog is still shown" }
                        probe.expect(first == null) { "the first call returned above the second" }
                        escape(window(1))
                    },
                ))
            }
        }
        waitForIdle()
        onNodeWithTag("open").performClick()
        frame()
        waitForIdle()

        probe.assertClean()
        assertEquals(listOf("second", "first"), returned)
        assertEquals(2, probe.maxDepth)
    }
}

/** Raven's calls that wait for the user, each running frames until its dialog closes. */
private enum class BlockingCall(val open: (window: Rid) -> Int) {
    MessageBox({ window -> Rmessage_box(window, "Spike", "Waiting for the user", R_MESSAGE_OK_CANCEL).toInt() }),
    DialogRun({ window ->
        val content = Rwidget_create(R_WIDGET_VBOX)
        val label = Rwidget_create(R_WIDGET_LABEL)
        Rlabel_set_text(label, "Waiting for the user")
        Rwidget_insert_child(content, label, 0)
        Rdialog_run(window, content)
    }),
    FileDialog({ window ->
        memScoped {
            val desc = alloc<RFileDialogDesc>().apply {
                parent = window
                title = null
                directory = null
                file_name = null
                filters = null
                filter_count = 0
                default_extension = null
                flags = R_FILE_DIALOG_RAVEN
            }
            Rfile_dialog_open(desc.ptr)
            Rfile_dialog_get_result_count()
        }
    }),
}

/** What the spike sees from inside the dialogs' nested frames. UI thread only. */
private class Probe {
    /** Blocking calls waiting now. */
    var depth = 0
    var maxDepth = 0
    /** Applies that ran while a call waited. */
    var nestedApplies = 0
    var stepsRun = 0
    private val problems = mutableListOf<String>()

    fun expect(ok: Boolean, problem: () -> String) {
        if (!ok) problems += problem()
    }

    fun expectText(label: Rid, text: String) {
        val shown = Rlabel_get_text(label).toRavenString()
        expect(shown == text) { "a Text shows \"$shown\", expected \"$text\"" }
    }

    /** Runs [call], which waits in nested frames, checking it isn't made inside an apply. */
    fun <T> blocking(shared: Shared? = null, call: () -> T): T {
        expect(appliesOnStack() == 0) { "a blocking call made inside an apply" }
        depth++
        maxDepth = maxOf(maxDepth, depth)
        shared?.let {
            it.opened.store(it.now())
            it.depth.store(depth)
        }
        try {
            return call()
        } finally {
            depth--
            shared?.depth?.store(depth)
        }
    }

    /** Called from a SideEffect: counts an apply made while a call waits, and the applies under it. */
    fun onNestedApply() {
        if (depth == 0) return
        nestedApplies++
        val applies = appliesOnStack()
        expect(applies == 1) { "$applies applies on the stack inside a dialog" }
    }

    fun assertClean() = assertEquals(emptyList(), problems.toList(), "problems inside the nested frames")
}

/** How many applies of a composition are running on this stack. */
private fun appliesOnStack(): Int =
    Throwable().stackTraceToString().lineSequence().count { "CompositionImpl#applyChanges(" in it }

/** Runs one of [steps] a frame, while a dialog waits, until they are all done. */
@Composable
private fun Driver(probe: Probe, steps: List<() -> Unit>) {
    LaunchedEffect(Unit) {
        while (probe.stepsRun < steps.size) {
            withFrameNanos { }
            if (probe.depth > 0) steps[probe.stepsRun++]()
        }
    }
}

/** State shared with the worker thread. Times are in microseconds since it was made. */
private class Shared {
    val fromThread: MutableState<Int> = mutableStateOf(0)
    val depth = AtomicInt(0)
    val opened = AtomicLong(0L)
    val clickSeen = AtomicLong(0L)
    private val written = AtomicLong(0L)
    val threadWriteSeen = AtomicLong(0L)
    private val lostClick = AtomicInt(0)
    private val lostThreadWrite = AtomicInt(0)
    private val start = TimeSource.Monotonic.markNow()

    fun now(): Long = start.elapsedNow().inWholeMicroseconds.coerceAtLeast(1L)

    fun lostWakeUps(): List<String> = listOfNotNull(
        "the click's write, queued behind the dialog".takeIf { lostClick.load() != 0 },
        "a write from another thread".takeIf { lostThreadWrite.load() != 0 },
    )

    fun timings(): String =
        "the click's write composed ${millis(clickSeen.load() - opened.load())} ms after the dialog opened, " +
            "the other thread's write ${millis(threadWriteSeen.load() - written.load())} ms after it was made"

    private fun millis(micros: Long): Double = (micros / 100) / 10.0

    /**
     * Waits for the dialog, then for the composition to see the click's write; then writes state
     * and waits for the composition to see that. A loop that slept through either is woken here
     * and the miss recorded, so the test fails instead of hanging. Then it dismisses the dialog.
     */
    fun watchWhileTheDialogWaits() {
        while (depth.load() == 0) usleep(1_000u)
        awaitSeen(clickSeen, lostClick)
        usleep(50_000u)
        written.store(now())
        fromThread.value = 1
        awaitSeen(threadWriteSeen, lostThreadWrite)
        Rtask_post(pressEscape, null)
    }

    private fun awaitSeen(seen: AtomicLong, lost: AtomicInt) {
        var waited = 0
        while (seen.load() == 0L) {
            usleep(10_000u)
            waited += 10
            if (waited >= 1_000 && waited % 100 == 0) {
                lost.store(1)
                Rwake_up()
            }
        }
    }
}

/** The window whose dialog [pressEscape] dismisses, from a task the worker posts. */
private val escapeWindow = AtomicInt(R_INVALID_ID.toInt())

private val pressEscape = staticCFunction { _: COpaquePointer? -> escape(escapeWindow.load().toUInt()) }

private fun RavenTestScope.window(index: Int): Rid = Rwidget_get_window(host.windowRoots[index].widget)

private fun RavenTestScope.widget(tag: String): Rid = findNodes { it.testTag == tag }.single().widget

private fun click(widget: Rid) {
    val rect = widgetRect(widget)
    val window = Rwidget_get_window(widget)
    Rwindow_inject_mouse_move(window, rect.x + rect.width / 2, rect.y + rect.height / 2)
    Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
    Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
}

/** Escape asks Raven to dismiss the dialog on top of [window]. */
private fun escape(window: Rid) {
    Rwindow_inject_key(window, R_KEY_ESCAPE, R_TRUE, 0u)
    Rwindow_inject_key(window, R_KEY_ESCAPE, R_FALSE, 0u)
}
