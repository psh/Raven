package raven.compose.foundation.layout

import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.dp

/**
 * How a Row or Column spaces its children, with Compose's shape. Phase 1 offers [Start], [Top] and
 * [spacedBy]: children are packed from the start, [spacing] apart.
 */
public object Arrangement {
    public sealed interface Horizontal {
        public val spacing: Dp
    }

    public sealed interface Vertical {
        public val spacing: Dp
    }

    public sealed interface HorizontalOrVertical : Horizontal, Vertical

    /** Children packed at the start of a Row. */
    public val Start: Horizontal = Spaced(0.dp)

    /** Children packed at the top of a Column. */
    public val Top: Vertical = Spaced(0.dp)

    /** Children packed from the start, [space] apart. */
    public fun spacedBy(space: Dp): HorizontalOrVertical = Spaced(space)

    private data class Spaced(override val spacing: Dp) : HorizontalOrVertical
}
