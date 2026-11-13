package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import raven.cinterop.RColorRole
import raven.cinterop.R_COLOR_ACCENT
import raven.cinterop.R_COLOR_ACCENT_HOVERED
import raven.cinterop.R_COLOR_ACCENT_PRESSED
import raven.cinterop.R_COLOR_CONTROL
import raven.cinterop.R_COLOR_CONTROL_HOVERED
import raven.cinterop.R_COLOR_CONTROL_PRESSED
import raven.cinterop.R_LOOK_RAVEN_DARK
import raven.cinterop.R_METRIC_CORNER_RADIUS
import raven.cinterop.R_METRIC_PADDING_X
import raven.cinterop.R_METRIC_PADDING_Y
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rlook_get
import raven.cinterop.Rtheme_is_dark
import raven.cinterop.Rwidget_clear_color
import raven.cinterop.Rwidget_clear_metric
import raven.cinterop.Rwidget_set_color
import raven.cinterop.Rwidget_set_metric
import raven.compose.ui.PaddingValues
import raven.compose.ui.Property
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.RectangleShape
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.graphics.Shape
import raven.compose.ui.graphics.isSpecified
import raven.compose.ui.graphics.toRaven
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.theme.Look
import raven.compose.ui.theme.RavenColors
import raven.compose.ui.theme.RavenTheme
import kotlin.experimental.ExperimentalNativeApi

// What a composable's colours, shape and padding parameters set on its widget.

/**
 * The colours a composable gives its widget's Raven [roles], and the app theme's colours they are
 * set under: a new look or theme changes [theme], which sets them again, with their shades for it.
 */
internal data class RoleColors(val roles: List<Pair<RColorRole, Color>>, val theme: RavenColors)

/** The colours of [roles] for [WidgetColors]. */
@Composable
@ReadOnlyComposable
internal fun roleColors(vararg roles: Pair<RColorRole, Color>): RoleColors = RoleColors(roles.asList(), RavenTheme.colors)

/**
 * Sets each role's colour, or clears the widget's own when it is Unspecified, so the theme's shows
 * again. Raven derives the hovered and pressed shades only for a theme's accent, so an `ACCENT` or
 * `CONTROL` colour sets them too, as the look in use derives the accent's. A hovered or pressed
 * role given with them is used as given instead, each on its own, even without its base colour.
 */
internal val WidgetColors = Property<RoleColors> { widget, colors ->
    val shades = Shades.ofLookInUse()
    val dark = Rtheme_is_dark() == R_TRUE
    val given = colors.roles.toMap()
    // A shade given with its base colour is set with the base, below.
    val shadesWithBase = ShadedRoles.filterKeys { it in given }.values.flatMap { it.toList() }.toSet()
    colors.roles.forEach { (role, color) ->
        if (role in shadesWithBase) return@forEach
        setOrClear(widget, role, color)
        ShadedRoles[role]?.let { (hovered, pressed) ->
            setOrClear(widget, hovered, given[hovered].orUnspecified().ifUnspecified { if (color.isSpecified) shades.hovered(color, dark) else color })
            setOrClear(widget, pressed, given[pressed].orUnspecified().ifUnspecified { if (color.isSpecified) shades.pressed(color, dark) else color })
        }
    }
}

private fun Color?.orUnspecified(): Color = this ?: Color.Unspecified

private inline fun Color.ifUnspecified(other: () -> Color): Color = if (isSpecified) this else other()

private fun setOrClear(widget: Rid, role: RColorRole, color: Color) {
    if (color.isSpecified) setColor(widget, role, color) else Rwidget_clear_color(widget, role)
}

/** The hovered and pressed roles of an `ACCENT` or `CONTROL` colour, whose shades compose-raven derives. */
internal val ShadedRoles: Map<RColorRole, Pair<RColorRole, RColorRole>> = mapOf(
    R_COLOR_ACCENT to (R_COLOR_ACCENT_HOVERED to R_COLOR_ACCENT_PRESSED),
    R_COLOR_CONTROL to (R_COLOR_CONTROL_HOVERED to R_COLOR_CONTROL_PRESSED),
)

private fun setColor(widget: Rid, role: RColorRole, color: Color) =
    color.toRaven { r, g, b, a -> Rwidget_set_color(widget, role, r, g, b, a) }

/**
 * How each look family shades a colour under the pointer and pressed, as Raven's
 * `rvn_theme_set_accent` (`src/widget/theme.c`) shades the accent, to the same 8 bits.
 */
internal enum class Shades {
    /** Raven, Glass and HighContrast: lighter under the pointer, darker pressed. */
    Raven {
        override fun hovered(color: Color, dark: Boolean) = color.mix(Color.White, 0.18f)
        override fun pressed(color: Color, dark: Boolean) = color.mix(Color.Black, 0.18f)
    },

    /** Windows: the colour seen through, at 90 % and 80 %. */
    Windows {
        override fun hovered(color: Color, dark: Boolean) = color.fade(0.9f)
        override fun pressed(color: Color, dark: Boolean) = color.fade(0.8f)
    },

    /** macOS and macOS Glass: no change under the pointer; darker pressed, lighter in the dark. */
    Macos {
        override fun hovered(color: Color, dark: Boolean) = color
        override fun pressed(color: Color, dark: Boolean) = color.mix(if (dark) Color.White else Color.Black, 0.2f)
    },

    /** GNOME: a little lighter under the pointer, darker pressed. */
    Gnome {
        override fun hovered(color: Color, dark: Boolean) = color.mix(Color.White, 0.1f)
        override fun pressed(color: Color, dark: Boolean) = color.mix(Color.Black, 0.2f)
    };

    abstract fun hovered(color: Color, dark: Boolean): Color

    abstract fun pressed(color: Color, dark: Boolean): Color

    companion object {
        /**
         * The shades of the look given to `Rlook_set`, Native being the platform's. A theme file
         * naming a look of its own, and subtree themes, aren't seen: Raven doesn't tell their family.
         */
        @OptIn(ExperimentalNativeApi::class)
        fun ofLookInUse(): Shades = when (Look.entries[(Rlook_get() - R_LOOK_RAVEN_DARK).toInt() / 2]) {
            Look.Raven, Look.Glass, Look.HighContrast -> Raven
            Look.Windows -> Windows
            Look.Macos, Look.MacosGlass -> Macos
            Look.Gnome -> Gnome
            Look.Native -> when (Platform.osFamily) {
                OsFamily.WINDOWS -> Windows
                OsFamily.MACOSX -> Macos
                else -> Gnome
            }
        }
    }
}

/** Each channel, alpha included, [t] of the way to [other]'s, rounded to 8 bits as `rvn_color_mix` does. */
private fun Color.mix(other: Color, t: Float): Color {
    fun channel(from: Float, to: Float): Float {
        val a = bits(from)
        return (a + (bits(to) - a) * t + 0.5f).toInt() / 255f
    }
    return Color(channel(red, other.red), channel(green, other.green), channel(blue, other.blue), channel(alpha, other.alpha))
}

/** The colour with its alpha times [opacity], rounded to 8 bits as `rvn_color_fade` does. */
private fun Color.fade(opacity: Float): Color = Color(red, green, blue, (bits(alpha) * opacity + 0.5f).toInt() / 255f)

/** A channel as Raven's 8-bit value. */
private fun bits(channel: Float): Float = (channel * 255f + 0.5f).toInt().toFloat()

/**
 * A widget's corner radius from its shape, null being the theme's. Raven has one radius, so a
 * shape with different corners uses its `topStart` and logs a warning naming the composable [name].
 */
internal fun cornerRadiusProperty(name: String): Property<Shape?> = Property { widget, shape ->
    when (shape) {
        null -> Rwidget_clear_metric(widget, R_METRIC_CORNER_RADIUS)
        RectangleShape -> Rwidget_set_metric(widget, R_METRIC_CORNER_RADIUS, 0f)
        is RoundedCornerShape -> {
            if (setOf(shape.topStart, shape.topEnd, shape.bottomEnd, shape.bottomStart).size > 1) {
                layoutWarningSink("$name: Raven rounds every corner alike, so the shape's topStart ${shape.topStart} is used for all four")
            }
            Rwidget_set_metric(widget, R_METRIC_CORNER_RADIUS, shape.topStart.value)
        }
    }
}

/**
 * A widget's padding inside, null being the theme's. Raven pads opposite sides alike, so padding
 * that differs between them uses `start` and `top` and logs a warning naming the composable [name].
 */
internal fun contentPaddingProperty(name: String): Property<PaddingValues?> = Property { widget, padding ->
    if (padding == null) {
        Rwidget_clear_metric(widget, R_METRIC_PADDING_X)
        Rwidget_clear_metric(widget, R_METRIC_PADDING_Y)
    } else {
        if (padding.start != padding.end || padding.top != padding.bottom) {
            layoutWarningSink("$name: Raven pads opposite sides alike, so contentPadding uses start ${padding.start} and top ${padding.top}")
        }
        Rwidget_set_metric(widget, R_METRIC_PADDING_X, padding.start.value)
        Rwidget_set_metric(widget, R_METRIC_PADDING_Y, padding.top.value)
    }
}
