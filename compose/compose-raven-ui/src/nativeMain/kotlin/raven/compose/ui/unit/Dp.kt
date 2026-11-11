package raven.compose.ui.unit

/** A length in Raven logical units: 1.dp is one unit, passed to Raven unchanged. */
public value class Dp(public val value: Float) : Comparable<Dp> {
    public operator fun plus(other: Dp): Dp = Dp(value + other.value)
    public operator fun minus(other: Dp): Dp = Dp(value - other.value)
    public operator fun unaryMinus(): Dp = Dp(-value)
    public operator fun times(other: Float): Dp = Dp(value * other)
    public operator fun div(other: Float): Dp = Dp(value / other)

    override fun compareTo(other: Dp): Int = value.compareTo(other.value)

    override fun toString(): String = if (isSpecified) "$value.dp" else "Dp.Unspecified"

    public companion object {
        /** No length: the constraint or setting is left as it is. */
        public val Unspecified: Dp = Dp(Float.NaN)
    }
}

public val Dp.isSpecified: Boolean get() = !value.isNaN()

public val Int.dp: Dp get() = Dp(toFloat())
public val Float.dp: Dp get() = Dp(this)
public val Double.dp: Dp get() = Dp(toFloat())
