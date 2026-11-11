package raven.compose.ui.unit

/**
 * A text size in Raven logical units: 1.sp is one unit. Raven scales every text size by the
 * accessibility text scale itself, so a size is passed to it unchanged.
 */
public value class TextUnit(public val value: Float) {
    override fun toString(): String = if (isSpecified) "$value.sp" else "TextUnit.Unspecified"

    public companion object {
        /** No size: the theme's text size is used. */
        public val Unspecified: TextUnit = TextUnit(Float.NaN)
    }
}

public val TextUnit.isSpecified: Boolean get() = !value.isNaN()

public val Int.sp: TextUnit get() = TextUnit(toFloat())
public val Float.sp: TextUnit get() = TextUnit(this)
public val Double.sp: TextUnit get() = TextUnit(toFloat())
