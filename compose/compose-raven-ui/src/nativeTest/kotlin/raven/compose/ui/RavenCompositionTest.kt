package raven.compose.ui

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import raven.cinterop.Rwidget_destroy
import raven.cinterop.Rwidget_find
import raven.native.CallbackRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

/** Real compositions over the Applier: what Compose actually asks of it when state changes. */
class RavenCompositionTest {
    @Composable
    private fun Node(name: String, kind: ChildKind = ChildKind.None, content: @Composable () -> Unit = {}) {
        ComposeNode<RavenNode, RavenApplier>(factory = { node(name, kind) }, update = {}, content = content)
    }

    private class Scene(val root: RavenNode, val frame: suspend () -> Unit)

    /** Composes [content] into an Overlay root, runs [block], then disposes and checks nothing is left. */
    private fun compose(content: @Composable () -> Unit, block: suspend Scene.() -> Unit) = withHeadlessRaven {
        runBlocking {
            val clock = BroadcastFrameClock()
            val recomposer = Recomposer(coroutineContext + clock)
            val recomposing = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
            yield() // let the Recomposer start, so it sees the first state change
            val root = node("root", ChildKind.Many, raven.cinterop.R_WIDGET_OVERLAY)
            val composition = Composition(RavenApplier(root), recomposer)
            composition.setContent(content)
            var time = 0L
            val scene = Scene(root) {
                Snapshot.sendApplyNotifications()
                yield()
                while (recomposer.hasPendingWork) {
                    clock.sendFrame(time++)
                    yield()
                }
            }
            scene.block()
            composition.dispose()
            assertEquals("root", ravenTree(root.widget), "after dispose")
            assertEquals(1, CallbackRegistry.liveCount, "only the root is left after dispose")
            Rwidget_destroy(root.widget)
            recomposer.cancel()
            recomposing.join()
        }
    }

    @Test
    fun switchingTheContentOfASingleChildParent() {
        var showA by mutableStateOf(true)
        compose({
            Node("border", ChildKind.Single) { if (showA) Node("a") else Node("b") }
        }) {
            assertEquals("root[border[a]]", ravenTree(root.widget))
            showA = false
            frame()
            assertEquals("root[border[b]]", ravenTree(root.widget))
            showA = true
            frame()
            assertEquals("root[border[a]]", ravenTree(root.widget))
            assertEquals(3, CallbackRegistry.liveCount, "root, border and a")
        }
    }

    @Test
    fun reorderingKeyedChildrenMovesTheSameWidgets() {
        var order by mutableStateOf(listOf("a", "b", "c", "d", "e"))
        compose({
            Node("list", ChildKind.Many) { for (name in order) key(name) { Node(name) } }
        }) {
            val widgets = order.associateWith { Rwidget_find(root.widget, it) }
            for (next in listOf(listOf("e", "d", "c", "b", "a"), listOf("b", "a", "e", "c", "d"), listOf("c", "d", "e", "a", "b"))) {
                order = next
                frame()
                assertEquals("root[list[${next.joinToString()}]]", ravenTree(root.widget))
            }
            assertEquals(widgets, order.associateWith { Rwidget_find(root.widget, it) }, "the same widgets, moved")
            order = listOf("c", "a")
            frame()
            assertEquals("root[list[c, a]]", ravenTree(root.widget))
            assertEquals(4, CallbackRegistry.liveCount, "root, list, c and a")
        }
    }

    @Test
    fun movableContentKeepsItsWidgetsAcrossParents() {
        var inLeft by mutableStateOf(true)
        compose({
            val card = remember { movableContentOf { Node("card", ChildKind.Many) { Node("title") } } }
            Node("left", ChildKind.Many) { if (inLeft) card() }
            Node("right", ChildKind.Single) { if (!inLeft) card() }
        }) {
            val card = Rwidget_find(root.widget, "card")
            assertEquals("root[left[card[title]], right]", ravenTree(root.widget))
            inLeft = false
            frame()
            assertEquals("root[left, right[card[title]]]", ravenTree(root.widget))
            inLeft = true
            frame()
            assertEquals("root[left[card[title]], right]", ravenTree(root.widget))
            assertEquals(card, Rwidget_find(root.widget, "card"), "the same card widget")
        }
    }
}
