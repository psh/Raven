package raven.compose.ui.theme

import raven.cinterop.RMetric
import raven.cinterop.R_METRIC_BLUR_RADIUS
import raven.cinterop.R_METRIC_BORDER_WIDTH
import raven.cinterop.R_METRIC_CHECK_SIZE
import raven.cinterop.R_METRIC_CONTROL_HEIGHT
import raven.cinterop.R_METRIC_CORNER_RADIUS
import raven.cinterop.R_METRIC_FONT_SIZE
import raven.cinterop.R_METRIC_GLASS_FROST
import raven.cinterop.R_METRIC_GLASS_REFRACTION
import raven.cinterop.R_METRIC_GLASS_RIM
import raven.cinterop.R_METRIC_GLASS_SATURATION
import raven.cinterop.R_METRIC_GLASS_SHADOW
import raven.cinterop.R_METRIC_INDENT
import raven.cinterop.R_METRIC_PADDING_X
import raven.cinterop.R_METRIC_PADDING_Y
import raven.cinterop.R_METRIC_POPUP_RADIUS
import raven.cinterop.R_METRIC_ROW_HEIGHT
import raven.cinterop.R_METRIC_SCROLLBAR_SIZE
import raven.cinterop.R_METRIC_SLIDER_HANDLE_SIZE
import raven.cinterop.R_METRIC_SPACING
import raven.cinterop.R_METRIC_SPLITTER_SIZE

/**
 * Each of Raven's theme metrics, for `RavenTheme(metrics)`, in Raven's own values: logical units for
 * sizes, and for the glass ones a factor of what the material gives, 1 being unchanged.
 */
public enum class ThemeMetric(internal val raven: RMetric) {
    /** The size of text, from which `Typography.fromTheme()` sizes its roles. */
    FontSize(R_METRIC_FONT_SIZE),
    CornerRadius(R_METRIC_CORNER_RADIUS),
    BorderWidth(R_METRIC_BORDER_WIDTH),

    /** The height of buttons, inputs, combo boxes, tabs and menu bars. */
    ControlHeight(R_METRIC_CONTROL_HEIGHT),

    /** The padding inside controls, across and down. */
    PaddingX(R_METRIC_PADDING_X),
    PaddingY(R_METRIC_PADDING_Y),

    /** The gap between an icon or a check mark and its text. */
    Spacing(R_METRIC_SPACING),
    ScrollbarSize(R_METRIC_SCROLLBAR_SIZE),

    /** The height of list, tree and menu rows. */
    RowHeight(R_METRIC_ROW_HEIGHT),

    /** The indentation of each tree level. */
    Indent(R_METRIC_INDENT),
    SplitterSize(R_METRIC_SPLITTER_SIZE),
    CheckSize(R_METRIC_CHECK_SIZE),
    SliderHandleSize(R_METRIC_SLIDER_HANDLE_SIZE),

    /** The corner radius of menus, drop-down lists and tooltips. */
    PopupRadius(R_METRIC_POPUP_RADIUS),

    /** How much glass blurs what is behind it. */
    GlassFrost(R_METRIC_GLASS_FROST),

    /** How far the rim of glass bends what is behind it. */
    GlassRefraction(R_METRIC_GLASS_REFRACTION),

    /** The saturation of what is seen through glass. */
    GlassSaturation(R_METRIC_GLASS_SATURATION),

    /** The light the rim of glass catches. */
    GlassRim(R_METRIC_GLASS_RIM),

    /** The shadow glass drops. */
    GlassShadow(R_METRIC_GLASS_SHADOW),

    /** The blur radius of a `BackgroundBlur` given none. */
    BlurRadius(R_METRIC_BLUR_RADIUS),
}
