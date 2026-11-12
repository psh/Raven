package raven.compose.ui.unit

/**
 * A width and a height, as a window's size and minimum size, rounded to whole logical units for
 * Raven. Both are kept in one `Long`, as Compose keeps them, so [Unspecified] equals itself.
 */
public value class DpSize internal constructor(private val packed: Long) {
    public val width: Dp get() = Dp(Float.fromBits((packed ushr 32).toInt()))
    public val height: Dp get() = Dp(Float.fromBits(packed.toInt()))

    public operator fun component1(): Dp = width
    public operator fun component2(): Dp = height

    public fun copy(width: Dp = this.width, height: Dp = this.height): DpSize = DpSize(width, height)

    override fun toString(): String = if (isSpecified) "$width x $height" else "DpSize.Unspecified"

    public companion object {
        public val Zero: DpSize = DpSize(0.dp, 0.dp)

        /** No size: the setting is left as it is. */
        public val Unspecified: DpSize = DpSize(Dp.Unspecified, Dp.Unspecified)
    }
}

public fun DpSize(width: Dp, height: Dp): DpSize =
    DpSize((width.value.toRawBits().toLong() shl 32) or (height.value.toRawBits().toLong() and 0xFFFFFFFFL))

public val DpSize.isSpecified: Boolean get() = this != DpSize.Unspecified
public val DpSize.isUnspecified: Boolean get() = this == DpSize.Unspecified
