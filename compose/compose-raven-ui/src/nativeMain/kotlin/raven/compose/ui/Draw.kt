package raven.compose.ui

import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rwidget_set_clipping
import raven.cinterop.Rwidget_set_opacity
import raven.compose.ui.graphics.Shape

// How the widget is drawn, whatever it is: its opacity and whether it clips its children. The node
// folds them with the rest of the Modifier (Layout.kt) and sets them on its outer widget.

/** An opacity. The last one in the chain wins. */
internal data class AlphaElement(val alpha: Float) : Modifier.Element {
    init {
        require(alpha in 0f..1f) { "alpha must be between 0 and 1, not $alpha" }
    }
}

/**
 * Draws the widget and its children at [alpha], from 0, unseen, to 1, opaque. The widget still
 * takes its place and its input. Without it, the widget is opaque.
 */
public fun Modifier.alpha(alpha: Float): Modifier = this then AlphaElement(alpha)

/** Clipping to the widget's bounds. It makes no difference how many times it is in the chain. */
internal data object ClipToBoundsElement : Modifier.Element

/** Clips the widget's descendants to its bounds, so a child larger than it is cut at its edges. */
public fun Modifier.clipToBounds(): Modifier = this then ClipToBoundsElement

/** Clipping to a shape. The last one in the chain gives the shape. */
internal data class ClipElement(val shape: Shape) : Modifier.Element

/**
 * Clips the widget to [shape]. Raven clips children only to a widget's bounds, so on most widgets
 * this is [clipToBounds], corners square; an `Image` rounds its own corners to a
 * [RoundedCornerShape][raven.compose.ui.graphics.RoundedCornerShape] (`Rimage_set_corner_radius`).
 */
public fun Modifier.clip(shape: Shape): Modifier = this then ClipElement(shape)

/** The shape of the last `clip` in the chain, null when there is none: for an `Image`'s corners. */
@InternalComposeRavenApi
public fun Modifier.clipShape(): Shape? = foldIn<Shape?>(null) { shape, element -> if (element is ClipElement) element.shape else shape }

/** The Raven calls of `Modifier.alpha` and `clipToBounds`. Raven has no getter, so tests replace them to see them. */
internal open class DrawCalls {
    open fun setAlpha(widget: Rid, alpha: Float) = Rwidget_set_opacity(widget, alpha)

    open fun setClipping(widget: Rid, clip: Boolean) = Rwidget_set_clipping(widget, if (clip) R_TRUE else R_FALSE)
}

internal var drawCalls: DrawCalls = DrawCalls()
