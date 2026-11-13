package raven.compose.foundation.layout

import raven.compose.ui.FillElement
import raven.compose.ui.Modifier
import raven.compose.ui.OffsetElement
import raven.compose.ui.PaddingElement
import raven.compose.ui.SizeElement
import raven.compose.ui.SizeInElement
import raven.compose.ui.unit.Dp

/**
 * Space around the widget. Paddings in one chain add up. Padding is always outside the widget, and
 * a negative one is an error, as in Compose: [offset] moves a widget.
 */
public fun Modifier.padding(all: Dp): Modifier = this then PaddingElement(all, all, all, all)

public fun Modifier.padding(horizontal: Dp = Dp(0f), vertical: Dp = Dp(0f)): Modifier =
    this then PaddingElement(horizontal, vertical, horizontal, vertical)

public fun Modifier.padding(start: Dp = Dp(0f), top: Dp = Dp(0f), end: Dp = Dp(0f), bottom: Dp = Dp(0f)): Modifier =
    this then PaddingElement(start, top, end, bottom)

/**
 * Moves the widget [x] to the right and [y] down from where it is placed, without changing its size
 * or its neighbours' places, as Compose's `offset` does: `Modifier.align(Alignment.BottomEnd)
 * .offset(10.dp, 8.dp)` hangs a badge over a corner. Offsets add up, and to any padding.
 */
public fun Modifier.offset(x: Dp = Dp(0f), y: Dp = Dp(0f)): Modifier = this then OffsetElement(x, y)

/** A fixed size. The last size in the chain wins on each axis. */
public fun Modifier.size(size: Dp): Modifier = this then SizeElement(size, size)

public fun Modifier.size(width: Dp, height: Dp): Modifier = this then SizeElement(width, height)

public fun Modifier.width(width: Dp): Modifier = this then SizeElement(width, Dp.Unspecified)

public fun Modifier.height(height: Dp): Modifier = this then SizeElement(Dp.Unspecified, height)

/** Bounds on the size; [Dp.Unspecified] leaves a bound as it is. */
public fun Modifier.sizeIn(
    minWidth: Dp = Dp.Unspecified,
    minHeight: Dp = Dp.Unspecified,
    maxWidth: Dp = Dp.Unspecified,
    maxHeight: Dp = Dp.Unspecified,
): Modifier = this then SizeInElement(minWidth, minHeight, maxWidth, maxHeight)

public fun Modifier.widthIn(min: Dp = Dp.Unspecified, max: Dp = Dp.Unspecified): Modifier =
    this then SizeInElement(min, Dp.Unspecified, max, Dp.Unspecified)

public fun Modifier.heightIn(min: Dp = Dp.Unspecified, max: Dp = Dp.Unspecified): Modifier =
    this then SizeInElement(Dp.Unspecified, min, Dp.Unspecified, max)

/**
 * Fills the parent's width. On a Row's main axis it takes the space left, like `weight(1f)`.
 * There is no fraction yet.
 */
public fun Modifier.fillMaxWidth(): Modifier = this then FillElement(horizontal = true, vertical = false)

/** Fills the parent's height. On a Column's main axis it takes the space left, like `weight(1f)`. */
public fun Modifier.fillMaxHeight(): Modifier = this then FillElement(horizontal = false, vertical = true)

public fun Modifier.fillMaxSize(): Modifier = this then FillElement(horizontal = true, vertical = true)
