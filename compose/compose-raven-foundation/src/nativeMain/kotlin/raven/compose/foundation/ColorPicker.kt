package raven.compose.foundation

import androidx.compose.runtime.Composable
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_COLOR_PICKER
import raven.cinterop.Rcolor_picker_add_swatch
import raven.cinterop.Rcolor_picker_clear_swatches
import raven.cinterop.Rcolor_picker_set_alpha_enabled
import raven.cinterop.Rcolor_picker_set_compact
import raven.cinterop.Rcolor_picker_set_popup
import raven.compose.ui.ColorPickerChanged
import raven.compose.ui.ColorPickerColor
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.isSpecified
import raven.compose.ui.graphics.toRaven

private val ColorPickerWidget = WidgetDescriptor("ColorPicker", R_WIDGET_COLOR_PICKER, events = listOf(ColorPickerChanged))

private val Popup = Property<Boolean> { widget, popup -> Rcolor_picker_set_popup(widget, if (popup) R_TRUE else R_FALSE) }
private val AlphaEnabled = Property<Boolean> { widget, alpha -> Rcolor_picker_set_alpha_enabled(widget, if (alpha) R_TRUE else R_FALSE) }
private val Compact = Property<Boolean> { widget, compact -> Rcolor_picker_set_compact(widget, if (compact) R_TRUE else R_FALSE) }
private val Swatches = Property<List<Color>> { widget, swatches ->
    Rcolor_picker_clear_swatches(widget)
    swatches.forEach { swatch -> swatch.toRaven { r, g, b, a -> Rcolor_picker_add_swatch(widget, r, g, b, a) } }
}

/**
 * Raven's colour picker showing [color], controlled as [Slider] is: [onColorChange] gets every
 * change the user makes, during a drag too, and a change the app doesn't take reverts on the next
 * frame. With [popup] it is a swatch the size of a button that opens the picker below it, showing
 * the colour it opened with beside the new one. [alpha] false makes colours opaque, and [compact]
 * hides the code and the numbers. [swatches] are a row of colours one click takes.
 */
@Composable
public fun ColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    popup: Boolean = false,
    alpha: Boolean = true,
    compact: Boolean = false,
    swatches: List<Color> = emptyList(),
    enabled: Boolean = true,
) {
    require(color.isSpecified) { "a ColorPicker needs a colour, not Color.Unspecified" }
    // Without alpha, Raven shows the colour opaque: the composed one is too, so the two agree.
    val shown = if (alpha) color else Color(color.red, color.green, color.blue)
    RavenWidget(ColorPickerWidget, modifier, {
        bind(enabled, Enabled)
        bind(popup, Popup)
        bind(compact, Compact)
        bind(alpha, AlphaEnabled)
        bind(swatches, Swatches)
        bind(shown, ColorPickerColor)
        bind(onColorChange, ColorPickerChanged)
    })
}
