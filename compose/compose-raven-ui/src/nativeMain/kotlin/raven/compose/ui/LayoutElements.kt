package raven.compose.ui

import raven.compose.ui.unit.Dp

// The layout elements of a Modifier chain. Foundation's modifier functions create them; the node
// folds them into slot and size settings (Layout.kt).

/** Space around the widget, inside its slot. Several paddings add up. */
@InternalComposeRavenApi
public data class PaddingElement(val start: Dp, val top: Dp, val end: Dp, val bottom: Dp) : Modifier.Element

/** The widget's size on each axis; [Dp.Unspecified] leaves an axis as it is. */
@InternalComposeRavenApi
public data class SizeElement(val width: Dp, val height: Dp) : Modifier.Element

/** Bounds on the widget's size; [Dp.Unspecified] leaves a bound as it is. */
@InternalComposeRavenApi
public data class SizeInElement(val minWidth: Dp, val minHeight: Dp, val maxWidth: Dp, val maxHeight: Dp) : Modifier.Element

/** Fills the space the parent gives on each axis where it is true. */
@InternalComposeRavenApi
public data class FillElement(val horizontal: Boolean, val vertical: Boolean) : Modifier.Element

/** A share of the space left along a Row's or Column's main axis. */
@InternalComposeRavenApi
public data class WeightElement(val weight: Float) : Modifier.Element {
    init {
        require(weight > 0f) { "weight must be positive, not $weight" }
    }
}

/** Alignment in the parent: both axes in a Box, the cross axis in a Row or Column. */
@InternalComposeRavenApi
public data class AlignElement(val horizontal: Alignment.Horizontal?, val vertical: Alignment.Vertical?) : Modifier.Element

/** A tag that tests find the node by. It makes no Raven call. */
internal data class TestTagElement(val tag: String) : Modifier.Element

/** Tags the composable for tests to find with `onNodeWithTag`. */
public fun Modifier.testTag(tag: String): Modifier = this then TestTagElement(tag)

/** A tooltip. The last one in the chain wins. */
internal data class TooltipElement(val text: String) : Modifier.Element

/** Shows [text] when the pointer rests on the widget. Text only: there is no composable tooltip. */
public fun Modifier.tooltip(text: String): Modifier = this then TooltipElement(text)
