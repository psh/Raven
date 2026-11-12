package raven.compose.ui.graphics

import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.dp

/**
 * The outline of a widget that draws a surface. Raven draws rectangles with rounded corners, so
 * every shape is one: a [RoundedCornerShape], or [RectangleShape] with none.
 */
public sealed class Shape

/** Square corners. */
public data object RectangleShape : Shape()

/**
 * Corners rounded by a radius each. `Start` and `End` are left and right: right-to-left layout
 * isn't exposed yet. Raven clamps a radius to half the shorter side.
 */
public data class RoundedCornerShape(
    public val topStart: Dp,
    public val topEnd: Dp,
    public val bottomEnd: Dp,
    public val bottomStart: Dp,
) : Shape() {
    /** Every corner rounded by [all]. */
    public constructor(all: Dp) : this(all, all, all, all)
}

/** Round ends: a radius larger than any widget, which Raven clamps to half the shorter side. */
public val CircleShape: Shape = RoundedCornerShape(100_000.dp)
