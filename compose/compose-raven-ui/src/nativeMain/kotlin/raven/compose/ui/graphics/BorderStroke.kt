package raven.compose.ui.graphics

import raven.compose.ui.unit.Dp

/** An outline [width] wide, drawn in [color], as Raven's `Rborder_set_outline` draws it. */
public data class BorderStroke(public val width: Dp, public val color: Color)
