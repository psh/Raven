package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import raven.cinterop.R_COLOR_ACCENT
import raven.cinterop.R_COLOR_ACCENT_HOVERED
import raven.cinterop.R_COLOR_ACCENT_PRESSED
import raven.cinterop.R_COLOR_BORDER
import raven.cinterop.R_COLOR_CONTROL_BORDER
import raven.cinterop.R_COLOR_FOCUS
import raven.cinterop.R_COLOR_HANDLE
import raven.cinterop.R_COLOR_INPUT
import raven.cinterop.R_COLOR_SELECTION
import raven.cinterop.R_COLOR_TEXT
import raven.cinterop.R_COLOR_TEXT_PLACEHOLDER
import raven.cinterop.R_COLOR_TRACK
import raven.cinterop.R_FALSE
import raven.cinterop.R_METRIC_FONT_SIZE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_COMBO_BOX
import raven.cinterop.R_WIDGET_SLIDER
import raven.cinterop.R_WIDGET_SPIN_BOX
import raven.cinterop.R_WIDGET_TEXT_INPUT
import raven.cinterop.Rcombo_box_add_item
import raven.cinterop.Rcombo_box_clear_items
import raven.cinterop.Rcombo_box_get_selected
import raven.cinterop.Rcombo_box_set_selected
import raven.cinterop.Rslider_set_range
import raven.cinterop.Rslider_set_step
import raven.cinterop.Rspin_box_set_decimals
import raven.cinterop.Rspin_box_set_range
import raven.cinterop.Rspin_box_set_step
import raven.cinterop.Rtext_input_set_placeholder
import raven.cinterop.Rtext_input_set_read_only
import raven.cinterop.Rwidget_clear_metric
import raven.cinterop.Rwidget_set_metric
import raven.compose.ui.ComboBoxChanged
import raven.compose.ui.ComboBoxSelected
import raven.compose.ui.IntSpinBoxChanged
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.SliderChanged
import raven.compose.ui.SliderValue
import raven.compose.ui.SpinBoxChanged
import raven.compose.ui.SpinBoxValue
import raven.compose.ui.TextChanged
import raven.compose.ui.TextInputText
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.Shape
import raven.compose.ui.graphics.isSpecified
import raven.compose.ui.text.LocalTextStyle
import raven.compose.ui.text.TextStyle
import raven.compose.ui.text.font.rememberRavenFont
import raven.compose.ui.unit.TextUnit
import raven.compose.ui.unit.isSpecified

private val TextFieldWidget = WidgetDescriptor("TextField", R_WIDGET_TEXT_INPUT, events = listOf(TextChanged))
private val SliderWidget = WidgetDescriptor("Slider", R_WIDGET_SLIDER, events = listOf(SliderChanged))
private val ComboBoxWidget = WidgetDescriptor("ComboBox", R_WIDGET_COMBO_BOX, events = listOf(ComboBoxChanged))
private val SpinBoxWidget = WidgetDescriptor("SpinBox", R_WIDGET_SPIN_BOX, events = listOf(SpinBoxChanged))
private val IntSpinBoxWidget = WidgetDescriptor("SpinBox", R_WIDGET_SPIN_BOX, events = listOf(IntSpinBoxChanged))

private val Placeholder = Property<String> { widget, text -> Rtext_input_set_placeholder(widget, text) }
private val ReadOnly = Property<Boolean> { widget, readOnly -> Rtext_input_set_read_only(widget, if (readOnly) R_TRUE else R_FALSE) }
private val SliderRange = Property<ClosedFloatingPointRange<Float>> { widget, range ->
    Rslider_set_range(widget, range.start, range.endInclusive)
}
private val SliderStep = Property<Float> { widget, step -> Rslider_set_step(widget, step) }

/** Raven clears the selection with the items, so the selection is put back after them. */
private val ComboBoxItems = Property<List<String>> { widget, items ->
    val selected = Rcombo_box_get_selected(widget)
    Rcombo_box_clear_items(widget)
    items.forEach { Rcombo_box_add_item(widget, it) }
    Rcombo_box_set_selected(widget, selected)
}
private val SpinBoxRange = Property<ClosedFloatingPointRange<Double>> { widget, range ->
    Rspin_box_set_range(widget, range.start, range.endInclusive)
}
private val SpinBoxStep = Property<Double> { widget, step -> Rspin_box_set_step(widget, step) }
private val Decimals = Property<Int> { widget, decimals -> Rspin_box_set_decimals(widget, decimals) }

private val TextFieldShape = cornerRadiusProperty("TextField")

/** A [TextField]'s colours, from [TextFieldDefaults.colors]. [Color.Unspecified] keeps the theme's. */
@Immutable
public data class TextFieldColors(
    /** The field's background. */
    public val containerColor: Color,
    public val textColor: Color,
    public val placeholderColor: Color,
    /** The outline of the focused field. */
    public val focusedIndicatorColor: Color,
    /** Selected text's background. */
    public val selectionColor: Color,
    /** The outline of the field without the focus: transparent for a field with no frame. */
    public val unfocusedIndicatorColor: Color = Color.Unspecified,
)

public object TextFieldDefaults {
    public fun colors(
        containerColor: Color = Color.Unspecified,
        textColor: Color = Color.Unspecified,
        placeholderColor: Color = Color.Unspecified,
        focusedIndicatorColor: Color = Color.Unspecified,
        selectionColor: Color = Color.Unspecified,
        unfocusedIndicatorColor: Color = Color.Unspecified,
    ): TextFieldColors = TextFieldColors(containerColor, textColor, placeholderColor, focusedIndicatorColor, selectionColor, unfocusedIndicatorColor)
}

/** A [Slider]'s colours, from [SliderDefaults.colors]. [Color.Unspecified] keeps the theme's. */
@Immutable
public data class SliderColors(
    public val thumbColor: Color,
    /** The track from the start to the thumb. */
    public val activeTrackColor: Color,
    /** The track from the thumb to the end. */
    public val inactiveTrackColor: Color,
    /** The active track under the pointer; unspecified derives it from [activeTrackColor]. */
    public val hoveredActiveTrackColor: Color = Color.Unspecified,
    /** The active track while pressed; unspecified derives it from [activeTrackColor]. */
    public val pressedActiveTrackColor: Color = Color.Unspecified,
)

public object SliderDefaults {
    /**
     * An active track colour also gives the slider its hovered and pressed shades, derived as the
     * look in use derives the accent's.
     * A hovered or pressed colour given is used as it is instead, each on its own, even without
     * the base colour.
     */
    public fun colors(
        thumbColor: Color = Color.Unspecified,
        activeTrackColor: Color = Color.Unspecified,
        inactiveTrackColor: Color = Color.Unspecified,
        hoveredActiveTrackColor: Color = Color.Unspecified,
        pressedActiveTrackColor: Color = Color.Unspecified,
    ): SliderColors = SliderColors(thumbColor, activeTrackColor, inactiveTrackColor, hoveredActiveTrackColor, pressedActiveTrackColor)
}

/** A widget's own text size, for one without a size setter; unspecified is the theme's. */
private val FontSizeMetric = Property<TextUnit> { widget, size ->
    if (size.isSpecified) Rwidget_set_metric(widget, R_METRIC_FONT_SIZE, size.value) else Rwidget_clear_metric(widget, R_METRIC_FONT_SIZE)
}

/**
 * A single-line field showing [value], which only the app changes: each edit calls
 * [onValueChange] with the new text, and if the app doesn't take it, the field shows [value]
 * again. Text the app takes as typed leaves the caret where it is; text the app rewrites moves
 * the caret to the end. [placeholder] is shown, dimmed, while the field is empty.
 *
 * [shape] null is the theme's corner radius. Raven has one, so a shape with different corners
 * uses its `topStart` and logs a warning.
 *
 * [textStyle] gives the text its font, size and colour; its colour wins over [colors]'
 * `textColor`. Raven's field has no alignment, so `textAlign` is ignored.
 *
 * Only single-line fields exist: `CodeEditor` edits several lines.
 */
@Composable
public fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    placeholder: String = "",
    singleLine: Boolean = true,
    shape: Shape? = null,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    require(singleLine) { "TextField is single-line only; CodeEditor edits several lines" }
    val roleColors = roleColors(
        R_COLOR_INPUT to colors.containerColor,
        R_COLOR_TEXT to if (textStyle.color.isSpecified) textStyle.color else colors.textColor,
        R_COLOR_TEXT_PLACEHOLDER to colors.placeholderColor,
        R_COLOR_FOCUS to colors.focusedIndicatorColor,
        R_COLOR_SELECTION to colors.selectionColor,
        // Raven's looks draw a field's frame in one or the other.
        R_COLOR_CONTROL_BORDER to colors.unfocusedIndicatorColor,
        R_COLOR_BORDER to colors.unfocusedIndicatorColor,
    )
    val font = rememberRavenFont(textStyle.fontFamily, textStyle.fontWeight, textStyle.fontStyle)
    RavenWidget(TextFieldWidget, modifier, {
        // Raven's field has no font setter of its own: the widget's font, which a subtree font
        // also sets, is its font.
        set(font) { setOwnFont(it) }
        bind(textStyle.fontSize, FontSizeMetric)
        bind(enabled, Enabled)
        bind(readOnly, ReadOnly)
        bind(shape, TextFieldShape)
        bind(roleColors, WidgetColors)
        bind(placeholder, Placeholder)
        bind(value, TextInputText)
        bind(onValueChange, TextChanged)
    })
}

/**
 * A slider showing [value] within [valueRange], controlled as [TextField] is. With [steps] above
 * 0, it snaps to that many evenly spaced values between the two ends.
 */
@Composable
public fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    colors: SliderColors = SliderDefaults.colors(),
) {
    require(steps >= 0) { "steps must not be negative" }
    val step = if (steps == 0) 0f else (valueRange.endInclusive - valueRange.start) / (steps + 1)
    val roleColors = roleColors(
        R_COLOR_HANDLE to colors.thumbColor,
        R_COLOR_ACCENT to colors.activeTrackColor,
        R_COLOR_TRACK to colors.inactiveTrackColor,
        R_COLOR_ACCENT_HOVERED to colors.hoveredActiveTrackColor,
        R_COLOR_ACCENT_PRESSED to colors.pressedActiveTrackColor,
    )
    RavenWidget(SliderWidget, modifier, {
        bind(enabled, Enabled)
        bind(roleColors, WidgetColors)
        // The range and step before the value: Raven snaps the value to them.
        bind(valueRange, SliderRange)
        bind(step, SliderStep)
        bind(value.coerceIn(valueRange), SliderValue)
        bind(onValueChange, SliderChanged)
    })
}

/**
 * A drop-down list of [items] showing the one at [selectedIndex], or none at -1, controlled as
 * [TextField] is: picking an item calls [onSelectedIndexChange] with its index. New [items] (by
 * value) replace the old ones and keep [selectedIndex].
 */
@Composable
public fun ComboBox(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(selectedIndex in -1..items.lastIndex) { "selectedIndex $selectedIndex is not -1 or an index of the ${items.size} items" }
    RavenWidget(ComboBoxWidget, modifier, {
        bind(enabled, Enabled)
        // The items before the selection: Raven selects nothing for an index it doesn't have.
        bind(items, ComboBoxItems)
        bind(selectedIndex, ComboBoxSelected)
        bind(onSelectedIndexChange, ComboBoxChanged)
    })
}

/**
 * A number field showing [value] within [valueRange] with [decimals] digits after the point,
 * controlled as [TextField] is. The user drags it to scrub, steps it by [step] with the arrow keys
 * or the wheel, or clicks it to type; each change calls [onValueChange].
 */
@Composable
public fun SpinBox(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Double> = 0.0..100.0,
    step: Double = 1.0,
    decimals: Int = 2,
    enabled: Boolean = true,
) {
    RavenWidget(SpinBoxWidget, modifier, {
        bind(enabled, Enabled)
        // The range before the value: Raven clamps the value to it.
        bind(valueRange, SpinBoxRange)
        bind(step, SpinBoxStep)
        bind(decimals, Decimals)
        bind(value.coerceIn(valueRange), SpinBoxValue)
        bind(onValueChange, SpinBoxChanged)
    })
}

/**
 * A whole-number [SpinBox]: what the user scrubs or types is rounded with `roundToInt()`, and
 * shown rounded.
 */
@Composable
public fun SpinBox(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: IntRange = 0..100,
    step: Int = 1,
    enabled: Boolean = true,
) {
    RavenWidget(IntSpinBoxWidget, modifier, {
        bind(enabled, Enabled)
        bind(valueRange.first.toDouble()..valueRange.last.toDouble(), SpinBoxRange)
        bind(step.toDouble(), SpinBoxStep)
        bind(0, Decimals)
        bind(value.coerceIn(valueRange).toDouble(), SpinBoxValue)
        bind(onValueChange, IntSpinBoxChanged)
    })
}
