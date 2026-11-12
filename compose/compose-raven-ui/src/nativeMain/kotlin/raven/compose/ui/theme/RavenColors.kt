package raven.compose.ui.theme

import androidx.compose.runtime.Immutable
import raven.cinterop.RColorRole
import raven.cinterop.R_COLOR_ACCENT
import raven.cinterop.R_COLOR_ACCENT_HOVERED
import raven.cinterop.R_COLOR_ACCENT_PRESSED
import raven.cinterop.R_COLOR_ACCENT_TEXT
import raven.cinterop.R_COLOR_BORDER
import raven.cinterop.R_COLOR_CODE
import raven.cinterop.R_COLOR_CONTROL
import raven.cinterop.R_COLOR_CONTROL_BORDER
import raven.cinterop.R_COLOR_CONTROL_HOVERED
import raven.cinterop.R_COLOR_CONTROL_PRESSED
import raven.cinterop.R_COLOR_DANGER
import raven.cinterop.R_COLOR_FOCUS
import raven.cinterop.R_COLOR_HANDLE
import raven.cinterop.R_COLOR_HANDLE_HOVERED
import raven.cinterop.R_COLOR_HEADER
import raven.cinterop.R_COLOR_HEADER_HOVERED
import raven.cinterop.R_COLOR_INPUT
import raven.cinterop.R_COLOR_LINK
import raven.cinterop.R_COLOR_PANEL
import raven.cinterop.R_COLOR_POPUP
import raven.cinterop.R_COLOR_POPUP_BORDER
import raven.cinterop.R_COLOR_ROW_HOVERED
import raven.cinterop.R_COLOR_SELECTION
import raven.cinterop.R_COLOR_SEPARATOR
import raven.cinterop.R_COLOR_SHADOW
import raven.cinterop.R_COLOR_SUCCESS
import raven.cinterop.R_COLOR_SWITCH
import raven.cinterop.R_COLOR_TAB_ACTIVE
import raven.cinterop.R_COLOR_TEXT
import raven.cinterop.R_COLOR_TEXT_DISABLED
import raven.cinterop.R_COLOR_TEXT_PLACEHOLDER
import raven.cinterop.R_COLOR_TOOLTIP
import raven.cinterop.R_COLOR_TOOLTIP_TEXT
import raven.cinterop.R_COLOR_TRACK
import raven.cinterop.R_COLOR_WARNING
import raven.cinterop.R_COLOR_WINDOW
import raven.compose.ui.graphics.Color

/**
 * One [Color] per Raven colour role, as the app theme has them: [RavenTheme.colors]. Subtree
 * themes aren't seen here, since Raven reads its theme getters from the app theme only.
 */
@Immutable
public class RavenColors internal constructor(private val values: List<Color>) {
    /** The window's clear colour. */
    public val window: Color get() = role(R_COLOR_WINDOW)

    /** A surface's background. */
    public val panel: Color get() = role(R_COLOR_PANEL)

    /** A surface's outline, and the outlines of lists and inputs. */
    public val border: Color get() = role(R_COLOR_BORDER)

    public val text: Color get() = role(R_COLOR_TEXT)
    public val textDisabled: Color get() = role(R_COLOR_TEXT_DISABLED)
    public val textPlaceholder: Color get() = role(R_COLOR_TEXT_PLACEHOLDER)

    /** Primary buttons, checked boxes, and the fill of sliders and progress bars. */
    public val accent: Color get() = role(R_COLOR_ACCENT)
    public val accentHovered: Color get() = role(R_COLOR_ACCENT_HOVERED)
    public val accentPressed: Color get() = role(R_COLOR_ACCENT_PRESSED)

    /** Text and marks drawn over [accent]. */
    public val accentText: Color get() = role(R_COLOR_ACCENT_TEXT)

    /** The background of buttons, combo boxes and check boxes. */
    public val control: Color get() = role(R_COLOR_CONTROL)
    public val controlHovered: Color get() = role(R_COLOR_CONTROL_HOVERED)
    public val controlPressed: Color get() = role(R_COLOR_CONTROL_PRESSED)
    public val controlBorder: Color get() = role(R_COLOR_CONTROL_BORDER)

    /** The background of text fields, spin boxes, lists and trees. */
    public val input: Color get() = role(R_COLOR_INPUT)

    /** The keyboard focus outline. */
    public val focus: Color get() = role(R_COLOR_FOCUS)

    /** Selected rows and selected text. */
    public val selection: Color get() = role(R_COLOR_SELECTION)
    public val rowHovered: Color get() = role(R_COLOR_ROW_HOVERED)

    /** The track of sliders, progress bars and scrollbars. */
    public val track: Color get() = role(R_COLOR_TRACK)

    /** Scrollbar thumbs and slider handles. */
    public val handle: Color get() = role(R_COLOR_HANDLE)
    public val handleHovered: Color get() = role(R_COLOR_HANDLE_HOVERED)
    public val separator: Color get() = role(R_COLOR_SEPARATOR)

    /** Menu bars, tab bars and expander headers. */
    public val header: Color get() = role(R_COLOR_HEADER)
    public val headerHovered: Color get() = role(R_COLOR_HEADER_HOVERED)

    /** The active tab. */
    public val tabActive: Color get() = role(R_COLOR_TAB_ACTIVE)

    /** Menus and combo box lists. */
    public val popup: Color get() = role(R_COLOR_POPUP)
    public val popupBorder: Color get() = role(R_COLOR_POPUP_BORDER)
    public val shadow: Color get() = role(R_COLOR_SHADOW)
    public val tooltip: Color get() = role(R_COLOR_TOOLTIP)
    public val tooltipText: Color get() = role(R_COLOR_TOOLTIP_TEXT)

    /** The track of a switch that is on. */
    public val switch: Color get() = role(R_COLOR_SWITCH)

    /** What went well, what needs attention, and what is wrong or destroys something. */
    public val success: Color get() = role(R_COLOR_SUCCESS)
    public val warning: Color get() = role(R_COLOR_WARNING)
    public val danger: Color get() = role(R_COLOR_DANGER)

    /** Links in formatted text. */
    public val link: Color get() = role(R_COLOR_LINK)

    /** The background of code in formatted text. */
    public val code: Color get() = role(R_COLOR_CODE)

    private fun role(role: RColorRole): Color = values[(role - R_COLOR_WINDOW).toInt()]

    override fun equals(other: Any?): Boolean = other is RavenColors && other.values == values

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "RavenColors(accent=$accent, panel=$panel, text=$text, ...)"
}
