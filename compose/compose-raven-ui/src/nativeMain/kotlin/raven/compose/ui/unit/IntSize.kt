package raven.compose.ui.unit

/** A size in whole pixels, as a part of an image is measured. */
public data class IntSize(public val width: Int, public val height: Int) {
    override fun toString(): String = "$width x $height"

    public companion object {
        public val Zero: IntSize = IntSize(0, 0)
    }
}

/** A position in whole pixels, from the top left, as a part of an image starts. */
public data class IntOffset(public val x: Int, public val y: Int) {
    override fun toString(): String = "($x, $y)"

    public companion object {
        public val Zero: IntOffset = IntOffset(0, 0)
    }
}
