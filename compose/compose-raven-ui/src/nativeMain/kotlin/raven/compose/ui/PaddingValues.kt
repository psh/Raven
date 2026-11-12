package raven.compose.ui

import androidx.compose.runtime.Immutable
import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.dp

/**
 * Space on each side of a widget's content, as a button's `contentPadding`. `Start` and `End` are
 * left and right: right-to-left layout isn't exposed yet.
 */
@Immutable
public class PaddingValues(
    public val start: Dp = 0.dp,
    public val top: Dp = 0.dp,
    public val end: Dp = 0.dp,
    public val bottom: Dp = 0.dp,
) {
    override fun equals(other: Any?): Boolean =
        other is PaddingValues && start == other.start && top == other.top && end == other.end && bottom == other.bottom

    override fun hashCode(): Int = ((start.hashCode() * 31 + top.hashCode()) * 31 + end.hashCode()) * 31 + bottom.hashCode()

    override fun toString(): String = "PaddingValues(start=$start, top=$top, end=$end, bottom=$bottom)"
}

/** [all] on every side. */
public fun PaddingValues(all: Dp): PaddingValues = PaddingValues(all, all, all, all)

/** [horizontal] at the start and end, [vertical] at the top and bottom: `PaddingValues(6.dp, 4.dp)`. */
public fun PaddingValues(horizontal: Dp, vertical: Dp): PaddingValues = PaddingValues(horizontal, vertical, horizontal, vertical)
