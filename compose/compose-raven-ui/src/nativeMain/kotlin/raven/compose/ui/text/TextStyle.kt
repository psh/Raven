package raven.compose.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import raven.cinterop.RTextAlign
import raven.cinterop.R_TEXT_ALIGN_CENTER
import raven.cinterop.R_TEXT_ALIGN_LEFT
import raven.cinterop.R_TEXT_ALIGN_RIGHT
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.isSpecified
import raven.compose.ui.text.font.FontFamily
import raven.compose.ui.text.font.FontStyle
import raven.compose.ui.text.font.FontWeight
import raven.compose.ui.unit.TextUnit
import raven.compose.ui.unit.isSpecified

/** How lines of text sit in their widget. Raven lays text out left to right, so [Start] is [Left]. */
public enum class TextAlign(internal val raven: RTextAlign) {
    Left(R_TEXT_ALIGN_LEFT),
    Right(R_TEXT_ALIGN_RIGHT),
    Center(R_TEXT_ALIGN_CENTER),
    Start(R_TEXT_ALIGN_LEFT),
    End(R_TEXT_ALIGN_RIGHT),
}

/** Raven's alignment for [align]; null is Raven's own, the start. */
@InternalComposeRavenApi
public fun ravenTextAlign(align: TextAlign?): RTextAlign = align?.raven ?: R_TEXT_ALIGN_LEFT

/**
 * How text looks. Each property left unspecified (or null) is the theme's, or what an outer
 * [ProvideTextStyle] gave.
 */
public data class TextStyle(
    val color: Color = Color.Unspecified,
    val fontSize: TextUnit = TextUnit.Unspecified,
    val fontFamily: FontFamily? = null,
    val fontWeight: FontWeight? = null,
    val fontStyle: FontStyle? = null,
    val textAlign: TextAlign? = null,
) {
    /** This style, with what [other] specifies winning. */
    public fun merge(other: TextStyle?): TextStyle = if (other == null) this else TextStyle(
        color = if (other.color.isSpecified) other.color else color,
        fontSize = if (other.fontSize.isSpecified) other.fontSize else fontSize,
        fontFamily = other.fontFamily ?: fontFamily,
        fontWeight = other.fontWeight ?: fontWeight,
        fontStyle = other.fontStyle ?: fontStyle,
        textAlign = other.textAlign ?: textAlign,
    )

    public companion object {
        /** Nothing specified: the theme's text. */
        public val Default: TextStyle = TextStyle()
    }
}

/** The style `Text`, `TextField` and `MarkdownText` take by default. It starts as the theme's. */
public val LocalTextStyle: ProvidableCompositionLocal<TextStyle> = compositionLocalOf { TextStyle.Default }

/** Gives [content] the current [LocalTextStyle] with what [value] specifies winning. */
@Composable
public fun ProvideTextStyle(value: TextStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.merge(value), content = content)
}
