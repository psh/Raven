package raven.compose.ui

import androidx.compose.runtime.AbstractApplier
import raven.cinterop.Rwidget_add_child
import raven.cinterop.Rwidget_get_child
import raven.cinterop.Rwidget_get_child_count
import raven.cinterop.Rwidget_get_parent
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
 *
 * With a [rootSlot], the root's widget is only a placeholder: the root's children go to the slot
 * instead (a window's island), and only the mirror list holds them.
 *
 * A [popup][RavenNode.isPopup] is kept in its parent's mirror list only: Raven's index of a child
 * counts the children before it that aren't popups.
 */
@OptIn(InternalComposeRavenApi::class)
internal class RavenApplier(
    root: RavenNode,
    private val onChangesApplied: () -> Unit = {},
    private val rootSlot: RootSlot? = null,
) : AbstractApplier<RavenNode>(root) {
    /** Single-child parents changed in this batch, to reattach their child in [onEndChanges]. */
    private val changedSingleParents = mutableSetOf<RavenNode>()

    /** Parents whose children changed in this batch, to label their inputs again in [onEndChanges]. */
    private val changedParents = mutableSetOf<RavenNode>()

    override fun insertTopDown(index: Int, instance: RavenNode) {
        // Bottom-up only: a subtree is built before it joins the tree.
    }

    override fun insertBottomUp(index: Int, instance: RavenNode) {
        val parent = current
        changedParents += parent
        if (parent === root && rootSlot != null) {
            rootSlot.attach(instance)
            parent.children.add(index, instance)
            instance.parent = parent
            instance.applyLayout()
            return
        }
        if (instance.isPopup) {
            if (parent.childKind == ChildKind.None) error("a ${Rwidget_get_type(parent.widget)} widget takes no children")
            parent.children.add(index, instance)
            instance.parent = parent
            instance.applyLayout()
            return
        }
        when (parent.childKind) {
            ChildKind.None -> error("a ${Rwidget_get_type(parent.widget)} widget takes no children")
            ChildKind.Single -> {
                // A second insert would make Raven destroy the attached child, which another node owns.
                detachAllRavenChildren(parent)
                Rwidget_insert_child(parent.widget, instance.widget, 0)
                changedSingleParents += parent
            }
            ChildKind.Many -> Rwidget_insert_child(parent.widget, instance.widget, ravenIndex(parent, index))
            ChildKind.Docked -> Rwidget_add_child(parent.widget, instance.widget)
        }
        parent.children.add(index, instance)
        instance.parent = parent
        instance.applyLayout()
    }

    override fun remove(index: Int, count: Int) {
        val parent = current
        changedParents += parent
        for (i in index until index + count) {
            val child = parent.children[i]
            child.parent = null
            if (parent === root && rootSlot != null) {
                rootSlot.detach(child)
                continue
            }
            if (child.isPopup) continue
            when (parent.childKind) {
                ChildKind.Many -> Rwidget_remove_child(parent.widget, child.widget)
                // A torn-off panel is in a window of its own: it goes when the node is released.
                ChildKind.Docked -> if (Rwidget_get_parent(child.widget) == parent.widget) Rwidget_remove_child(parent.widget, child.widget)
                // A child that an insert has detached since is no longer in Raven's tree.
                else -> if (child.isAttachedTo(parent)) Rwidget_remove_child(parent.widget, child.widget)
            }
        }
        parent.children.remove(index, count)
        if (parent.childKind == ChildKind.Single) changedSingleParents += parent
    }

    override fun move(from: Int, to: Int, count: Int) {
        val parent = current
        changedParents += parent
        // A single-child parent shows one child at most, and an island's root holds only a
        // placeholder widget, so only the mirror list changes.
        if (parent.childKind != ChildKind.Many || (parent === root && rootSlot != null)) {
            parent.children.move(from, to, count)
        } else if (parent.children.none { it.isPopup }) {
            for ((moveFrom, moveTo) in ravenMoves(from, to, count)) {
                Rwidget_move_child(parent.widget, moveFrom, moveTo)
            }
            parent.children.move(from, to, count)
        } else {
            parent.children.move(from, to, count)
            reorderRavenChildren(parent)
        }
    }

    /** Raven's index for a child inserted at [index] of [parent]'s mirror list: popups aren't in Raven. */
    private fun ravenIndex(parent: RavenNode, index: Int): Int = (0 until index).count { !parent.children[it].isPopup }

    /** Moves [parent]'s Raven children, one at a time, into the order of its mirror list without the popups. */
    private fun reorderRavenChildren(parent: RavenNode) {
        parent.children.filterNot { it.isPopup }.forEachIndexed { wanted, child ->
            // The children before `wanted` are in place already, so this one is at `wanted` or after.
            val at = (wanted until Rwidget_get_child_count(parent.widget)).first { Rwidget_get_child(parent.widget, it) == child.widget }
            if (at != wanted) Rwidget_move_child(parent.widget, at, wanted)
        }
    }

    override fun onClear() {
        for (child in root.children) {
            if (rootSlot != null) {
                rootSlot.detach(child)
            } else if (child.isAttachedTo(root)) {
                Rwidget_remove_child(root.widget, child.widget)
            }
            child.parent = null
        }
        root.children.clear()
    }

    override fun onEndChanges() {
        // Once a batch is applied, a single-child parent shows its one remaining child, which an
        // insert that has since been removed may have detached.
        for (parent in changedSingleParents) {
            val only = parent.children.filterNot { it.isPopup }.singleOrNull() ?: continue
            if (!only.isAttachedTo(parent)) Rwidget_insert_child(parent.widget, only.widget, 0)
        }
        changedSingleParents.clear()
        changedParents.forEach { it.labelInputs() }
        changedParents.clear()
        onChangesApplied()
    }

    private fun detachAllRavenChildren(parent: RavenNode) {
        for (i in Rwidget_get_child_count(parent.widget) - 1 downTo 0) {
            Rwidget_remove_child(parent.widget, Rwidget_get_child(parent.widget, i))
        }
    }
}

/** Where the children of a [RavenApplier]'s root go, in place of the root's widget. */
@OptIn(InternalComposeRavenApi::class)
internal interface RootSlot {
    /** [node] was inserted at the root: the slot shows it from now on. */
    fun attach(node: RavenNode)

    /** [node] was removed from the root; Compose releases it next, unless it moves. */
    fun detach(node: RavenNode)
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
