@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui.graphics

import raven.compose.ui.InternalComposeRavenApi

/**
 * A colour an image is drawn in. Raven multiplies the image's pixels by it, so a white or
 * monochrome icon takes the colour, alpha included.
 */
public class ColorFilter private constructor(
    /** The tint, or [Color.Unspecified] for the text colour where the image sits. */
    @property:InternalComposeRavenApi public val color: Color,
) {
    override fun equals(other: Any?): Boolean = other is ColorFilter && color == other.color

    override fun hashCode(): Int = color.hashCode()

    override fun toString(): String = if (color.isSpecified) "ColorFilter.tint($color)" else "ColorFilter.textColor()"

    public companion object {
        /** Draws the image in [color]: `Rimage_set_tint_color`. */
        public fun tint(color: Color): ColorFilter {
            require(color.isSpecified) { "a tint needs a colour: ColorFilter.textColor() follows the text colour" }
            return ColorFilter(color)
        }

        /**
         * Draws the image in the text colour where it sits (`Rimage_set_tint_text`), a compose-raven
         * extension: an icon follows the theme, a button's content colour or a hover colour,
         * without recomposing.
         */
        public fun textColor(): ColorFilter = ColorFilter(Color.Unspecified)
    }
}
