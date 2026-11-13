@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui.text.font

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.cinterop.refTo
import raven.cinterop.RFontStyle
import raven.cinterop.R_FONT_BOLD
import raven.cinterop.R_FONT_BOLD_ITALIC
import raven.cinterop.R_FONT_ITALIC
import raven.cinterop.R_FONT_REGULAR
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_TRUE
import raven.cinterop.Rfont_create
import raven.cinterop.Rfont_create_from_file
import raven.cinterop.Rfont_get_default
import raven.cinterop.Rfont_get_monospace
import raven.cinterop.Rfont_get_variant
import raven.cinterop.Rfont_is_valid
import raven.cinterop.Rfont_set_variant
import raven.cinterop.Rid
import raven.cinterop.Rtheme_get_font
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.LocalRavenHost
import raven.compose.ui.RavenHost
import raven.compose.ui.res.resolveResource

/**
 * How heavy a font is, from 1 to 1000. Raven has two steps: up to [W500] is regular, from [W600]
 * on is bold.
 */
public class FontWeight(public val weight: Int) : Comparable<FontWeight> {
    init {
        require(weight in 1..1000) { "a font weight is from 1 to 1000, not $weight" }
    }

    internal val isBold: Boolean get() = weight >= 600

    override fun compareTo(other: FontWeight): Int = weight.compareTo(other.weight)

    override fun equals(other: Any?): Boolean = other is FontWeight && weight == other.weight

    override fun hashCode(): Int = weight

    override fun toString(): String = "FontWeight($weight)"

    public companion object {
        public val W100: FontWeight = FontWeight(100)
        public val W200: FontWeight = FontWeight(200)
        public val W300: FontWeight = FontWeight(300)
        public val W400: FontWeight = FontWeight(400)
        public val W500: FontWeight = FontWeight(500)
        public val W600: FontWeight = FontWeight(600)
        public val W700: FontWeight = FontWeight(700)
        public val W800: FontWeight = FontWeight(800)
        public val W900: FontWeight = FontWeight(900)
        public val Thin: FontWeight = W100
        public val ExtraLight: FontWeight = W200
        public val Light: FontWeight = W300
        public val Normal: FontWeight = W400
        public val Medium: FontWeight = W500
        public val SemiBold: FontWeight = W600
        public val Bold: FontWeight = W700
        public val ExtraBold: FontWeight = W800
        public val Black: FontWeight = W900
    }
}

/** Upright or slanted. */
public enum class FontStyle { Normal, Italic }

/** Raven's style for [weight] and [style], each null being the regular one. */
internal fun ravenStyle(weight: FontWeight?, style: FontStyle?): RFontStyle {
    val bold = weight?.isBold == true
    val italic = style == FontStyle.Italic
    return when {
        bold && italic -> R_FONT_BOLD_ITALIC
        bold -> R_FONT_BOLD
        italic -> R_FONT_ITALIC
        else -> R_FONT_REGULAR
    }
}

/**
 * One file of a [FontFamily]: a `.ttf` or `.otf` at a path, or its bytes, with the [weight] and
 * [style] it draws.
 */
public class Font internal constructor(
    internal val path: String?,
    private val bytes: ByteArray?,
    public val weight: FontWeight,
    public val style: FontStyle,
) {
    internal val ravenStyle: RFontStyle get() = ravenStyle(weight, style)

    /** Loads the file into Raven; [R_INVALID_ID] when Raven can't read it, which it reports. */
    internal fun load(host: RavenHost): Rid = when {
        path != null -> Rfont_create_from_file(host.resolveResource(path))
        bytes!!.isEmpty() -> R_INVALID_ID
        // Raven copies the bytes.
        else -> Rfont_create(bytes.refTo(0), bytes.size.toULong())
    }

    override fun equals(other: Any?): Boolean =
        other is Font && path == other.path && (bytes?.contentEquals(other.bytes) ?: (other.bytes == null)) &&
            weight == other.weight && style == other.style

    override fun hashCode(): Int = ((path?.hashCode() ?: bytes!!.contentHashCode()) * 31 + weight.hashCode()) * 31 + style.hashCode()

    override fun toString(): String = "Font(${path ?: "${bytes!!.size} bytes"}, $weight, $style)"
}

/**
 * The font file at [path], which resolves as `painterResource`'s does: among the files given to
 * `registerResource`, then in the resource root.
 */
public fun Font(path: String, weight: FontWeight = FontWeight.Normal, style: FontStyle = FontStyle.Normal): Font =
    Font(path, null, weight, style)

/** A font from the bytes of its file, which are copied. */
public fun Font(bytes: ByteArray, weight: FontWeight = FontWeight.Normal, style: FontStyle = FontStyle.Normal): Font =
    Font(null, bytes.copyOf(), weight, style)

/**
 * Fonts that make one family: a regular one, and the bold, italic and bold italic ones that go
 * with it. Raven draws a style the family has no file for from the regular font, thicker or
 * slanted. Families are loaded on first use and kept by the host until Raven shuts down.
 */
public sealed class FontFamily {
    public companion object {
        /** Roboto, which Raven embeds. */
        public val Default: FontFamily = SystemFontFamily.Default

        /** Roboto Mono, which Raven embeds, as code editors use by default. */
        public val Monospace: FontFamily = SystemFontFamily.Monospace
    }
}

/** A family made of [fonts] ([FontFamily] of fonts). */
internal data class FontListFontFamily(val fonts: List<Font>) : FontFamily()

internal class SystemFontFamily private constructor(private val name: String, val font: () -> Rid) : FontFamily() {
    override fun toString(): String = "FontFamily.$name"

    companion object {
        val Default = SystemFontFamily("Default", ::Rfont_get_default)
        val Monospace = SystemFontFamily("Monospace", ::Rfont_get_monospace)
    }
}

/**
 * A family of [fonts]: the regular one (or the first, when none is regular) is the family, and the
 * others are attached to it as its bold, italic and bold italic styles (`Rfont_set_variant`). The
 * first file of a style wins.
 */
public fun FontFamily(vararg fonts: Font): FontFamily {
    require(fonts.isNotEmpty()) { "a FontFamily needs a font" }
    return FontListFontFamily(fonts.toList())
}

/**
 * The family's regular Raven font, with its other styles attached, loaded once per host. A family
 * whose regular file Raven can't read is Raven's default font, so text still shows.
 */
@InternalComposeRavenApi
public fun RavenHost.font(family: FontFamily): Rid = when (family) {
    is SystemFontFamily -> family.font()
    is FontListFontFamily -> caches.getOrPut(LoadedFamilyKey(family)) { loadFamily(family) }
}

private data class LoadedFamilyKey(val family: FontListFontFamily)

private fun RavenHost.loadFamily(family: FontListFontFamily): Rid {
    val regular = family.fonts.firstOrNull { it.ravenStyle == R_FONT_REGULAR } ?: family.fonts.first()
    val font = regular.load(this)
    if (Rfont_is_valid(font) != R_TRUE) return Rfont_get_default()
    val attached = mutableSetOf(R_FONT_REGULAR)
    for (other in family.fonts) {
        if (other === regular || !attached.add(other.ravenStyle)) continue
        val variant = other.load(this)
        if (Rfont_is_valid(variant) == R_TRUE) Rfont_set_variant(font, other.ravenStyle, variant)
    }
    return font
}

/**
 * The family given by the innermost `RavenTheme(fontFamily)`, at application level or in a
 * window; null when none gives one, and the theme's font is used.
 */
@InternalComposeRavenApi
public val LocalFontFamily: androidx.compose.runtime.ProvidableCompositionLocal<FontFamily?> = staticCompositionLocalOf { null }

/** The font a widget falls back on when nothing gives it a family: the theme's, or the fixed-width one. */
@InternalComposeRavenApi
public enum class BaseFont { Theme, Monospace }

/**
 * The Raven font a text widget is given for [family], [weight] and [style], or [R_INVALID_ID] when
 * it should keep the font it inherits (the theme's, or a `RavenTheme`'s): with no [family] and the
 * regular style. Bold or italic with no [family] is that style of the fixed-width font for a
 * [BaseFont.Monospace] widget, else of the inherited family, else of the theme's font, which is read
 * once, so a later look change doesn't reach it.
 */
@InternalComposeRavenApi
@Composable
public fun rememberRavenFont(family: FontFamily?, weight: FontWeight?, style: FontStyle?, base: BaseFont = BaseFont.Theme): Rid {
    val host = LocalRavenHost.current
    val inherited = LocalFontFamily.current
    return remember(host, family, weight, style, inherited, base) {
        val ravenStyle = ravenStyle(weight, style)
        when {
            family != null -> host.font(family).styled(ravenStyle)
            ravenStyle == R_FONT_REGULAR -> R_INVALID_ID
            base == BaseFont.Monospace -> Rfont_get_monospace().styled(ravenStyle)
            inherited != null -> host.font(inherited).styled(ravenStyle)
            else -> Rtheme_get_font().takeIf { Rfont_is_valid(it) == R_TRUE }.let { it ?: Rfont_get_default() }.styled(ravenStyle)
        }
    }
}

private fun Rid.styled(style: RFontStyle): Rid = if (style == R_FONT_REGULAR) this else Rfont_get_variant(this, style)
