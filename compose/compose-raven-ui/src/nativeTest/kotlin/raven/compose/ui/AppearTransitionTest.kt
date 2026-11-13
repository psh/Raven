package raven.compose.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_WIDGET_LABEL
import raven.cinterop.Rid
import raven.cinterop.Rwidget_get_parent
import raven.compose.animation.TransitionRequester
import raven.compose.animation.transitionRequester
import raven.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** `Modifier.appearTransition`, through the Raven calls it makes: Raven has no getter for them. */
class AppearTransitionTest {
    private val names = mutableMapOf<Rid, String>()

    /** Names the widget in the calls, once, when it is created. */
    private val Name = Property<String> { widget, name -> names[widget] = name }

    private val TestLabel = WidgetDescriptor("TestLabel", R_WIDGET_LABEL)

    // The Modifier is set before the other properties, so the widgets are named when the calls are read.
    private val recorded = mutableListOf<Pair<Rid, String>>()

    /** What the nodes asked of Raven, each `set` saying whether the widget was in the tree yet. */
    private val calls: List<String> get() = recorded.map { (widget, call) -> call.replace("%", names[widget] ?: "?") }

    private val previousCalls = appearCalls

    @BeforeTest
    fun recordCalls() {
        appearCalls = object : AppearCalls() {
            override fun set(widget: Rid, transition: AppearTransition?, delay: Duration) {
                val where = if (Rwidget_get_parent(widget) == R_INVALID_ID) "before insert" else "inserted"
                recorded += widget to "set % $transition $delay $where"
                super.set(widget, transition, delay)
            }

            override fun play(widget: Rid, transition: AppearTransition, delay: Duration) {
                recorded += widget to "play % $transition $delay"
                super.play(widget, transition, delay)
            }

            override fun stop(widget: Rid) {
                recorded += widget to "stop %"
                super.stop(widget)
            }
        }
    }

    @AfterTest
    fun restoreCalls() {
        appearCalls = previousCalls
    }

    @Test
    fun theLastTransitionInTheChainWins() {
        val folded = fold(Modifier.appearTransition(AppearTransition.Fade).appearTransition(AppearTransition.Drop, 50.milliseconds))
        assertEquals(AppearTransitionElement(AppearTransition.Drop, 50.milliseconds), folded.appearTransition)
    }

    @Test
    fun aNegativeDelayIsRefused() {
        assertFailsWith<IllegalArgumentException> { Modifier.appearTransition(AppearTransition.Pop, (-1).milliseconds) }
    }

    @Test
    fun aNodeAddedByRecompositionGetsItsTransitionAndDelayBeforeItIsInserted() {
        var shown by mutableStateOf(false)
        withHost({
            Window(onCloseRequest = {}) {
                RavenWidget(TestLabel, Modifier.appearTransition(AppearTransition.Fade), { bind("first", Name) })
                if (shown) {
                    RavenWidget(TestLabel, Modifier.appearTransition(AppearTransition.FlyUp, 100.milliseconds), { bind("second", Name) })
                }
            }
        }) {
            assertEquals(listOf("set first Fade 0s before insert"), calls)
            recorded.clear()
            shown = true
            runUntilIdle()
            assertEquals(listOf("set second FlyUp 100ms before insert"), calls)
        }
    }

    @Test
    fun aModifierChangeDoesntReplayItOnAWidgetAlreadyShown() {
        var padding by mutableStateOf(0.dp)
        var delay by mutableStateOf(Duration.ZERO)
        var transition: AppearTransition? by mutableStateOf(AppearTransition.Pop)
        withHost({
            Window(onCloseRequest = {}) {
                val appear = transition?.let { Modifier.appearTransition(it, delay) } ?: Modifier
                RavenWidget(TestLabel, appear then PaddingElement(padding, padding, padding, padding), { bind("label", Name) })
            }
        }) {
            recorded.clear()
            padding = 4.dp
            runUntilIdle()
            assertEquals(emptyList(), calls, "an unchanged transition makes no call")

            delay = 200.milliseconds
            runUntilIdle()
            assertEquals(listOf("set label Pop 200ms inserted", "stop label"), calls, "Raven plays a transition as it is set")
            recorded.clear()

            transition = null
            runUntilIdle()
            assertEquals(listOf("set label null 0s inserted"), calls)
        }
    }

    @Test
    fun aMoveDoesntReplayIt() {
        var order by mutableStateOf(listOf("a", "b", "c"))
        withHost({
            Window(onCloseRequest = {}) {
                for (name in order) {
                    key(name) { RavenWidget(TestLabel, Modifier.appearTransition(AppearTransition.SlideLeft), { bind(name, Name) }) }
                }
            }
        }) {
            assertEquals(3, recorded.size)
            recorded.clear()
            order = listOf("c", "a", "b")
            runUntilIdle()
            assertEquals(emptyList(), calls)
        }
    }

    @Test
    fun aRequesterPlaysItsTransitionOnTheWidgetItIsAttachedTo() {
        val requester = TransitionRequester()
        val other = TransitionRequester()
        var attached by mutableStateOf(true)
        var shown by mutableStateOf(true)
        withHost({
            Window(onCloseRequest = {}) {
                if (shown) {
                    val modifier = if (attached) Modifier.transitionRequester(requester).transitionRequester(other) else Modifier
                    RavenWidget(TestLabel, modifier.appearTransition(AppearTransition.Fade), { bind("label", Name) })
                }
            }
        }) {
            recorded.clear()
            requester.play(AppearTransition.Drop, 100.milliseconds)
            other.play(AppearTransition.Pop)
            assertEquals(listOf("play label Drop 100ms", "play label Pop 0s"), calls, "whatever the widget's appearTransition")
            recorded.clear()

            // Detached, it does nothing; attached again, it plays.
            attached = false
            runUntilIdle()
            requester.play(AppearTransition.Drop)
            assertEquals(emptyList(), calls)
            attached = true
            runUntilIdle()
            requester.play(AppearTransition.Zoom)
            assertEquals(listOf("play label Zoom 0s"), calls)
            recorded.clear()

            // Its widget gone, it does nothing.
            shown = false
            runUntilIdle()
            requester.play(AppearTransition.Drop)
            assertEquals(emptyList(), calls)
        }
        assertFailsWith<IllegalArgumentException> { requester.play(AppearTransition.Fade, (-1).milliseconds) }
    }
}
