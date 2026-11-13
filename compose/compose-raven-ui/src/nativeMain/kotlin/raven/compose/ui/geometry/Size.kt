package raven.compose.ui.geometry

import androidx.compose.runtime.Immutable

/** A width and a height, as Compose's `Size`: a `DrawScope`'s is in Raven logical units, as dp. */
@Immutable
public data class Size(val width: Float, val height: Float) {
    /** The smaller of the two. */
    public val minDimension: Float get() = minOf(width, height)

    /** The point in the middle of a rectangle of this size at the origin. */
    public val center: Offset get() = Offset(width / 2, height / 2)

    public companion object {
        public val Zero: Size = Size(0f, 0f)
    }
}
