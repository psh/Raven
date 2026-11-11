package raven.compose.foundation

import androidx.compose.runtime.Composable
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_SLIDER
import raven.cinterop.R_WIDGET_TEXT_INPUT
import raven.cinterop.Rslider_set_range
import raven.cinterop.Rslider_set_step
import raven.cinterop.Rtext_input_set_placeholder
import raven.cinterop.Rtext_input_set_read_only
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.SliderChanged
import raven.compose.ui.SliderValue
import raven.compose.ui.TextChanged
import raven.compose.ui.TextInputText
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind

private val TextFieldWidget = WidgetDescriptor("TextField", R_WIDGET_TEXT_INPUT, events = listOf(TextChanged))
private val SliderWidget = WidgetDescriptor("Slider", R_WIDGET_SLIDER, events = listOf(SliderChanged))

private val Placeholder = Property<String> { widget, text -> Rtext_input_set_placeholder(widget, text) }
private val ReadOnly = Property<Boolean> { widget, readOnly -> Rtext_input_set_read_only(widget, if (readOnly) R_TRUE else R_FALSE) }
private val SliderRange = Property<ClosedFloatingPointRange<Float>> { widget, range ->
    Rslider_set_range(widget, range.start, range.endInclusive)
}
private val SliderStep = Property<Float> { widget, step -> Rslider_set_step(widget, step) }

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
