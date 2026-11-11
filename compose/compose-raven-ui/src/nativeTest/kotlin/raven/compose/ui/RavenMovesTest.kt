package raven.compose.ui

import androidx.compose.runtime.AbstractApplier
import kotlin.test.Test
import kotlin.test.assertEquals

/** [ravenMoves] against Compose's own move semantics, with no Raven: every move of up to six children. */
class RavenMovesTest {
    /** Exposes AbstractApplier's protected list helpers, which define what a move means. */
    private class ListApplier : AbstractApplier<Int>(0) {
        fun moveIn(list: MutableList<Int>, from: Int, to: Int, count: Int) = list.move(from, to, count)
        override fun insertTopDown(index: Int, instance: Int) {}
        override fun insertBottomUp(index: Int, instance: Int) {}
        override fun remove(index: Int, count: Int) {}
        override fun move(from: Int, to: Int, count: Int) {}
        override fun onClear() {}
    }

    /** What `Rwidget_move_child(parent, from, to)` does to the children. */
    private fun MutableList<Int>.ravenMove(from: Int, to: Int) = add(to, removeAt(from))

    @Test
    fun ravenMovesMatchComposeMoves() {
        val applier = ListApplier()
        for (size in 1..6) for (count in 1..size) for (from in 0..size - count) {
            for (to in 0..size) {
                // Moves that AbstractApplier itself rejects (a swap past the end) never happen.
                val expected = MutableList(size) { it }
                if (runCatching { applier.moveIn(expected, from, to, count) }.isFailure) continue
                val actual = MutableList(size) { it }
                for ((moveFrom, moveTo) in ravenMoves(from, to, count)) actual.ravenMove(moveFrom, moveTo)
                assertEquals(expected, actual, "move(from = $from, to = $to, count = $count) of $size")
            }
        }
    }

    @Test
    fun aMoveToWhereTheBlockAlreadyIsCallsNothing() {
        assertEquals(emptyList(), ravenMoves(1, 4, 3))
    }
}
