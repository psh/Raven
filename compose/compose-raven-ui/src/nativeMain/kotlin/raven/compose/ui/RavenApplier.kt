package raven.compose.ui

import androidx.compose.runtime.AbstractApplier
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_type
import raven.cinterop.Rwidget_insert_child
import raven.cinterop.Rwidget_move_child
import raven.cinterop.Rwidget_remove_child

/**
 * Applies Compose's changes to Raven's widget tree, bottom-up. [root] wraps the fixed Overlay the
 * host sets as the window's content, and is never replaced.
 *
 * [remove] only detaches, so `movableContentOf` can insert the same node somewhere else; nodes are
 * destroyed when Compose releases them ([RavenNode.onRelease]). [onChangesApplied] runs after each
 * batch of changes.
 */
internal class RavenApplier(
    root: RavenNode,
    private val onChangesApplied: () -> Unit = {},
) : AbstractApplier<RavenNode>(root) {
    /** Single-child parents changed in this batch, to reattach their child in [onEndChanges]. */
    private val changedSingleParents = mutableSetOf<RavenNode>()

    override fun insertTopDown(index: Int, instance: RavenNode) {
        // Bottom-up only: a subtree is built before it joins the tree.
    }

    override fun insertBottomUp(index: Int, instance: RavenNode) {
        val parent = current
        when (parent.childKind) {
            ChildKind.None -> error("a ${Rwidget_get_type(parent.widget)} widget takes no children")
            ChildKind.Single -> {
                // A second insert would make Raven destroy the attached child, which another node owns.
                detachAllRavenChildren(parent)
                Rwidget_insert_child(parent.widget, instance.widget, 0)
                changedSingleParents += parent
            }
            ChildKind.Many -> Rwidget_insert_child(parent.widget, instance.widget, index)
        }
        parent.children.add(index, instance)
    }

    override fun remove(index: Int, count: Int) {
        val parent = current
        for (i in index until index + count) {
            val child = parent.children[i]
            when (parent.childKind) {
                ChildKind.Many -> Rwidget_remove_child(parent.widget, child.widget)
                // A child that an insert has detached since is no longer in Raven's tree.
                else -> if (child.isAttachedTo(parent)) Rwidget_remove_child(parent.widget, child.widget)
            }
        }
        parent.children.remove(index, count)
        if (parent.childKind == ChildKind.Single) changedSingleParents += parent
    }

    override fun move(from: Int, to: Int, count: Int) {
        val parent = current
        // A single-child parent shows one child at most, so only the mirror list changes.
        if (parent.childKind == ChildKind.Many) {
            for ((moveFrom, moveTo) in ravenMoves(from, to, count)) {
                Rwidget_move_child(parent.widget, moveFrom, moveTo)
            }
        }
        parent.children.move(from, to, count)
    }

    override fun onClear() {
        for (child in root.children) {
            if (child.isAttachedTo(root)) Rwidget_remove_child(root.widget, child.widget)
        }
        root.children.clear()
    }

    override fun onEndChanges() {
        // Once a batch is applied, a single-child parent shows its one remaining child, which an
        // insert that has since been removed may have detached.
        for (parent in changedSingleParents) {
            val only = parent.children.singleOrNull() ?: continue
            if (!only.isAttachedTo(parent)) Rwidget_insert_child(parent.widget, only.widget, 0)
        }
        changedSingleParents.clear()
        onChangesApplied()
    }

    private fun detachAllRavenChildren(parent: RavenNode) {
        for (i in Rwidget_get_child_count(parent.widget) - 1 downTo 0) {
            Rwidget_remove_child(parent.widget, Rwidget_get_child(parent.widget, i))
        }
    }
}

/**
 * The `Rwidget_move_child(parent, from, to)` calls that apply [AbstractApplier]'s move of [count]
 * children from [from] to [to], so Raven's children stay in step with the mirror list that
 * `children.move(from, to, count)` updates. Raven moves one child per call, with `to` counted among
 * the children without it.
 */
internal fun ravenMoves(from: Int, to: Int, count: Int): List<Pair<Int, Int>> {
    // AbstractApplier swaps a single child with its neighbour.
    if (count == 1 && (from == to + 1 || from == to - 1)) return listOf(maxOf(from, to) to minOf(from, to))
    // Where the block starts, among the children without it.
    val dest = if (from > to) to else to - count
    return when {
        dest == from -> emptyList()
        // Each child in turn, from the front of the block.
        dest < from -> List(count) { i -> from + i to dest + i }
        // The block's first child is at `from` each time, and goes to the block's last place.
        else -> List(count) { from to dest + count - 1 }
    }
}
