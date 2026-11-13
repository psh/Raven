package raven.compose.ui.geometry

import androidx.compose.runtime.Immutable

/**
 * A point, or a move, in two dimensions, as Compose's `Offset`: [x] to the right and [y] down. As a
 * pointer event's position and scroll delta, it is in Raven logical units, the same as dp,
 * relative to the widget that receives the event.
 */
@Immutable
public data class Offset(val x: Float, val y: Float) {
    public operator fun plus(other: Offset): Offset = Offset(x + other.x, y + other.y)
    public operator fun minus(other: Offset): Offset = Offset(x - other.x, y - other.y)
    public operator fun unaryMinus(): Offset = Offset(-x, -y)
    public operator fun times(operand: Float): Offset = Offset(x * operand, y * operand)
    public operator fun div(operand: Float): Offset = Offset(x / operand, y / operand)

    public companion object {
        /** The origin, or no move. */
        public val Zero: Offset = Offset(0f, 0f)
    }
}
