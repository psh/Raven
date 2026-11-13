package raven.compose.ui.unit

import androidx.compose.runtime.Immutable

/**
 * How many pixels a dp is on a screen: [density], with the conversions in its scope, as Compose's
 * `Density` without its font scale. Raven lays out in dp and draws in pixels, so a viewport's size
 * and a pointer position over it are converted with `with(LocalDensity.current) { 10.dp.toPx() }`.
 */
@Immutable
public interface Density {
    /** Pixels per dp: the window's scale, the monitor's times Raven's interface scale. */
    public val density: Float

    /** This length in pixels. */
    public fun Dp.toPx(): Float = value * density

    /** This many pixels as a length. */
    public fun Int.toDp(): Dp = Dp(this / density)

    /** This many pixels as a length. */
    public fun Float.toDp(): Dp = Dp(this / density)
}

/** A [Density] of [density] pixels per dp. */
public fun Density(density: Float): Density = DensityImpl(density)

private data class DensityImpl(override val density: Float) : Density {
    init {
        require(density > 0f) { "density must be positive, not $density" }
    }
}
