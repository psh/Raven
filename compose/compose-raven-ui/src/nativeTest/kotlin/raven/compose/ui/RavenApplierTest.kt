package raven.compose.ui

import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Rwidget_destroy
import raven.cinterop.Rwidget_is_valid
import raven.native.CallbackRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The Applier driven directly, checked against Raven's own child lists. */
class RavenApplierTest {
    private fun RavenApplier.insertAll(parent: RavenNode, vararg children: RavenNode) {
        down(parent)
        children.forEachIndexed { index, child -> insertBottomUp(index, child) }
        up()
    }

    @Test
    fun insertsRemovesAndMovesInRaven() = withHeadlessRaven {
        val root = node("root", ChildKind.Many)
        val applier = RavenApplier(root)
        val (a, b, c, d) = listOf("a", "b", "c", "d").map { node(it) }
        applier.insertAll(root, a, b, c, d)
        assertEquals(listOf("a", "b", "c", "d"), ravenChildren(root.widget))

        applier.move(0, 3, 2)
        assertEquals(listOf("c", "a", "b", "d"), ravenChildren(root.widget))
        applier.move(3, 0, 1)
        assertEquals(listOf("d", "c", "a", "b"), ravenChildren(root.widget))
        assertEquals(listOf(d, c, a, b), root.children)

        applier.remove(1, 2)
        assertEquals(listOf("d", "b"), ravenChildren(root.widget))
        assertEquals(listOf(d, b), root.children)
        // Removing only detaches.
        assertEquals(R_TRUE, Rwidget_is_valid(c.widget))
        c.onRelease()
        a.onRelease()
        assertEquals(R_FALSE, Rwidget_is_valid(c.widget))
        Rwidget_destroy(root.widget)
    }

    @Test
    fun aLeafTakesNoChildren() = withHeadlessRaven {
        val label = node("label")
        val applier = RavenApplier(node("root", ChildKind.Many))
        applier.down(label)
        val error = assertFailsWith<IllegalStateException> { applier.insertBottomUp(0, node("child")) }
        assertTrue("takes no children" in error.message.orEmpty(), error.message)
    }

    @Test
    fun aSingleChildParentKeepsTheOldChildAlive() = withHeadlessRaven {
        val root = node("root", ChildKind.Many)
        val applier = RavenApplier(root)
        val border = node("border", ChildKind.Single)
        val (old, new) = listOf(node("old"), node("new"))
        applier.insertAll(root, border)

        applier.down(border)
        applier.insertBottomUp(0, old)
        applier.insertBottomUp(1, new)
        // Raven would have destroyed `old` on the second insert; it is detached instead.
        assertEquals(listOf("new"), ravenChildren(border.widget))
        assertEquals(R_TRUE, Rwidget_is_valid(old.widget))
        assertEquals(listOf(old, new), border.children)

        // Removing the detached child makes no Raven call.
        applier.remove(0, 1)
        applier.up()
        applier.onEndChanges()
        assertEquals(listOf("new"), ravenChildren(border.widget))
        old.onRelease()
        Rwidget_destroy(root.widget)
    }

    @Test
    fun aSingleChildParentShowsItsRemainingChild() = withHeadlessRaven {
        val root = node("root", ChildKind.Many)
        val applier = RavenApplier(root)
        val border = node("border", ChildKind.Single)
        val (kept, gone) = listOf(node("kept"), node("gone"))
        applier.insertAll(root, border)
        applier.insertAll(border, kept, gone)

        applier.down(border)
        applier.remove(1, 1)
        applier.up()
        applier.onEndChanges()
        assertEquals("root[border[kept]]", ravenTree(root.widget))
        gone.onRelease()
        Rwidget_destroy(root.widget)
    }

    @Test
    fun releasingParentsAndChildrenInEitherOrderDestroysEachWidgetOnce() = withHeadlessRaven {
        for (parentFirst in listOf(true, false)) {
            val root = node("root", ChildKind.Many)
            val applier = RavenApplier(root)
            val panel = node("panel", ChildKind.Many)
            val (x, y) = listOf(node("x"), node("y"))
            applier.insertAll(panel, x, y)
            applier.insertAll(root, panel)
            applier.remove(0, 1)
            val released = if (parentFirst) listOf(panel, x, y) else listOf(x, y, panel)
            released.forEach { it.onRelease() }
            assertEquals(1, CallbackRegistry.liveCount, "only the root is left")
            Rwidget_destroy(root.widget)
        }
    }

    @Test
    fun aNodeIsTheTargetOfItsCallbacks() = withHeadlessRaven {
        val label = node("label")
        assertSame(label, CallbackRegistry.targetOf(label.widget))
        label.onRelease()
        assertEquals(0, CallbackRegistry.liveCount)
    }
}
