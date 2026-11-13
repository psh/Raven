@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.LocalRavenHost
import raven.compose.ui.text.TextStyle
import raven.compose.ui.text.font.FontFamily
import raven.compose.ui.unit.sp

/**
 * The text roles of an app, from the largest to the smallest, and [code]. `Text` and `TextField`
 * use [body], the content of buttons [label], and `CodeEditor` [code]. Change a role with `copy`.
 */
@Immutable
public data class Typography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val code: TextStyle,
) {
    public companion object {
        /**
         * Roles sized from the app theme's base font size B by Raven's own Markdown heading factors:
         * [display] 1.9 B, [headline] 1.52 B, [title] 1.26 B, [subtitle] 1.1 B and [caption] 0.92 B.
         * [body], [label] and [code] leave the size to Raven, so theme files still set it, and
         * [code] is in the fixed-width font. Readers recompose when the theme or the look changes
         * the base size. Subtree themes aren't seen here.
         */
        @Composable
        @ReadOnlyComposable
        public fun fromTheme(): Typography = typographyFor(LocalRavenHost.current.theme.baseFontSize)
    }
}

/** [Typography.fromTheme] for a base size of [base]. */
internal fun typographyFor(base: Float): Typography = Typography(
    display = TextStyle(fontSize = (base * 1.9f).sp),
    headline = TextStyle(fontSize = (base * 1.52f).sp),
    title = TextStyle(fontSize = (base * 1.26f).sp),
    subtitle = TextStyle(fontSize = (base * 1.1f).sp),
    body = TextStyle.Default,
    label = TextStyle.Default,
    caption = TextStyle(fontSize = (base * 0.92f).sp),
    code = TextStyle(fontFamily = FontFamily.Monospace),
)

/** The typography a `RavenTheme(typography)` gives; null when none does, and it is the theme's. */
internal val LocalTypography = compositionLocalOf<Typography?> { null }
