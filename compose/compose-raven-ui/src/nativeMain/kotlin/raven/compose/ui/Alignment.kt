package raven.compose.ui

/**
 * Where a child sits in the space its parent gives it, with Compose's names. A Box child takes an
 * [Alignment]; a Column child an [Alignment.Horizontal]; a Row child an [Alignment.Vertical].
 * `Start` and `End` are left and right: right-to-left layout isn't exposed yet.
 */
public class Alignment private constructor(public val horizontal: Horizontal, public val vertical: Vertical) {
    public class Horizontal internal constructor(internal val align: HAlign) {
        override fun toString(): String = "Alignment.Horizontal($align)"
    }

    public class Vertical internal constructor(internal val align: VAlign) {
        override fun toString(): String = "Alignment.Vertical($align)"
    }

    override fun toString(): String = "Alignment(${horizontal.align}, ${vertical.align})"

    public companion object {
        public val Start: Horizontal = Horizontal(HAlign.Start)
        public val CenterHorizontally: Horizontal = Horizontal(HAlign.Center)
        public val End: Horizontal = Horizontal(HAlign.End)

        public val Top: Vertical = Vertical(VAlign.Top)
        public val CenterVertically: Vertical = Vertical(VAlign.Center)
        public val Bottom: Vertical = Vertical(VAlign.Bottom)

        public val TopStart: Alignment = Alignment(Start, Top)
        public val TopCenter: Alignment = Alignment(CenterHorizontally, Top)
        public val TopEnd: Alignment = Alignment(End, Top)
        public val CenterStart: Alignment = Alignment(Start, CenterVertically)
        public val Center: Alignment = Alignment(CenterHorizontally, CenterVertically)
        public val CenterEnd: Alignment = Alignment(End, CenterVertically)
        public val BottomStart: Alignment = Alignment(Start, Bottom)
        public val BottomCenter: Alignment = Alignment(CenterHorizontally, Bottom)
        public val BottomEnd: Alignment = Alignment(End, Bottom)
    }
}

/** A slot's horizontal alignment, as Raven has it. [Fill] stretches the child across the slot. */
internal enum class HAlign { Start, Center, End, Fill }

/** A slot's vertical alignment, as Raven has it. [Fill] stretches the child across the slot. */
internal enum class VAlign { Top, Center, Bottom, Fill }
