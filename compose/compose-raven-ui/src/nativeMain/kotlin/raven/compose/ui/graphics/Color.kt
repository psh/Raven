package raven.compose.ui.graphics

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import raven.compose.ui.InternalComposeRavenApi

/**
 * A colour in sRGB, 8 bits a channel, as Raven stores it: `0xAARRGGBB` in the low 32 bits.
 * [Unspecified] lies outside them, so it is no colour at all, not even [Transparent].
 */
public value class Color internal constructor(internal val value: ULong) {
    /** The alpha channel, 0 to 1. */
    public val alpha: Float get() = channel(24)

    /** The red channel, 0 to 1. */
    public val red: Float get() = channel(16)

    /** The green channel, 0 to 1. */
    public val green: Float get() = channel(8)

    /** The blue channel, 0 to 1. */
    public val blue: Float get() = channel(0)

    private fun channel(shift: Int): Float = ((value shr shift) and 0xFFuL).toFloat() / 255f

    override fun toString(): String =
        if (isSpecified) "Color(0x${(value and 0xFFFFFFFFuL).toString(16).uppercase().padStart(8, '0')})" else "Color.Unspecified"

    public companion object {
        public val Black: Color = Color(0xFF000000)
        public val White: Color = Color(0xFFFFFFFF)
        public val Gray: Color = Color(0xFF888888)
        public val Red: Color = Color(0xFFFF0000)
        public val Green: Color = Color(0xFF00FF00)
        public val Blue: Color = Color(0xFF0000FF)
        public val Yellow: Color = Color(0xFFFFFF00)
        public val Transparent: Color = Color(0x00000000)

        /** No colour: the setting is left to the theme. */
        public val Unspecified: Color = Color(0x1_0000_0000uL)
    }
}

/** The colour `0xAARRGGBB`, as `Color(0xFF3B82F6)`. Bits above the low 32 are ignored. */
public fun Color(color: Long): Color = Color(color.toULong() and 0xFFFFFFFFuL)

/**
 * The colour of these channels, each 0 to 1. A value outside is clamped, and each is rounded to
 * 8 bits, as Raven rounds the colours it is given.
 */
public fun Color(red: Float, green: Float, blue: Float, alpha: Float = 1f): Color {
    fun bits(channel: Float, shift: Int): ULong = ((channel.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toULong()) shl shift
    return Color(bits(alpha, 24) or bits(red, 16) or bits(green, 8) or bits(blue, 0))
}

public val Color.isSpecified: Boolean get() = this != Color.Unspecified
public val Color.isUnspecified: Boolean get() = this == Color.Unspecified

/**
 * Passes the colour to a Raven setter [set], as the four channels Raven takes. Unspecified has no
 * Raven colour: the caller restores the theme's instead.
 */
@InternalComposeRavenApi
public inline fun <T> Color.toRaven(set: (red: Float, green: Float, blue: Float, alpha: Float) -> T): T {
    check(isSpecified) { "Color.Unspecified has no Raven colour" }
    return set(red, green, blue, alpha)
}

/** The colour a Raven getter [get] writes through its four channel pointers. */
@InternalComposeRavenApi
public inline fun ravenColor(
    get: (red: CPointer<FloatVar>, green: CPointer<FloatVar>, blue: CPointer<FloatVar>, alpha: CPointer<FloatVar>) -> Unit,
): Color = memScoped {
    val red = alloc<FloatVar>()
    val green = alloc<FloatVar>()
    val blue = alloc<FloatVar>()
    val alpha = alloc<FloatVar>()
    get(red.ptr, green.ptr, blue.ptr, alpha.ptr)
    Color(red.value, green.value, blue.value, alpha.value)
}
