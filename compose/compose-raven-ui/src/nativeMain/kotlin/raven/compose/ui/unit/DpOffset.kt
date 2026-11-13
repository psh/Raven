package raven.compose.ui.unit

/** A distance on each axis in dp, as a `DropdownMenu`'s offset from its anchor. */
public data class DpOffset(val x: Dp, val y: Dp) {
    public operator fun plus(other: DpOffset): DpOffset = DpOffset(x + other.x, y + other.y)
    public operator fun minus(other: DpOffset): DpOffset = DpOffset(x - other.x, y - other.y)

    public companion object {
        public val Zero: DpOffset = DpOffset(0.dp, 0.dp)
    }
}
