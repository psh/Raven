package raven.compose.ui.geometry

/**
 * A point, or a distance on each axis, as a pointer event's position and scroll delta. A position
 * is in Raven logical units, the same as dp, relative to the widget that receives the event.
 */
public data class Offset(val x: Float, val y: Float) {
    public operator fun plus(other: Offset): Offset = Offset(x + other.x, y + other.y)
    public operator fun minus(other: Offset): Offset = Offset(x - other.x, y - other.y)

    public companion object {
        public val Zero: Offset = Offset(0f, 0f)
    }
}
