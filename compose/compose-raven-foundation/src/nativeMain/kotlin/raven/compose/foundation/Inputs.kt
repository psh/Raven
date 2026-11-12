package raven.compose.foundation

import androidx.compose.runtime.Composable
import raven.cinterop.R_FALSE
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

/**
 * A single-line field showing [value], which only the app changes: each edit calls
 * [onValueChange] with the new text, and if the app doesn't take it, the field shows [value]
 * again. Text the app takes as typed leaves the caret where it is; text the app rewrites moves
 * the caret to the end. [placeholder] is shown, dimmed, while the field is empty.
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
    placeholder: String = "",
    singleLine: Boolean = true,
) {
    require(singleLine) { "TextField is single-line only; CodeEditor edits several lines" }
    RavenWidget(TextFieldWidget, modifier, {
        bind(enabled, Enabled)
        bind(readOnly, ReadOnly)
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
) {
    require(steps >= 0) { "steps must not be negative" }
    val step = if (steps == 0) 0f else (valueRange.endInclusive - valueRange.start) / (steps + 1)
    RavenWidget(SliderWidget, modifier, {
        bind(enabled, Enabled)
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
