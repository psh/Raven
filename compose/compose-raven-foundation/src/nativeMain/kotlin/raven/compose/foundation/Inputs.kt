package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
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
import raven.cinterop.ROrientation
import raven.cinterop.R_FALSE
import raven.cinterop.R_HORIZONTAL
import raven.cinterop.R_METRIC_FONT_SIZE
import raven.cinterop.R_TRUE
import raven.cinterop.R_VERTICAL
import raven.cinterop.R_WIDGET_COMBO_BOX
import raven.cinterop.R_WIDGET_SLIDER
import raven.cinterop.R_WIDGET_SPIN_BOX
import raven.cinterop.R_WIDGET_TEXT_INPUT
import raven.cinterop.Rcombo_box_add_item
import raven.cinterop.Rcombo_box_clear_items
import raven.cinterop.Rcombo_box_get_selected
import raven.cinterop.Rcombo_box_set_selected
import raven.cinterop.Rid
import raven.cinterop.Rslider_set_orientation
import raven.cinterop.Rslider_set_range
import raven.cinterop.Rslider_set_step
import raven.cinterop.Rspin_box_set_decimals
import raven.cinterop.Rspin_box_set_range
import raven.cinterop.Rspin_box_set_step
import raven.cinterop.Rtext_input_set_insets
import raven.cinterop.Rtext_input_set_max_length
import raven.cinterop.Rtext_input_set_password
import raven.cinterop.Rtext_input_set_placeholder
import raven.cinterop.Rtext_input_set_read_only
import raven.cinterop.Rwidget_clear_metric
import raven.cinterop.Rwidget_get_rect
import raven.cinterop.Rwidget_is_valid
import raven.cinterop.Rwidget_set_focus_on_click
import raven.cinterop.Rwidget_set_metric
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.padding
import raven.compose.foundation.text.KeyboardActions
import raven.compose.ui.Alignment
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
import raven.compose.ui.TextCommitted
import raven.compose.ui.TextInputText
import raven.compose.ui.WidgetAttachedElement
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.WidgetFollower
import raven.compose.ui.bind
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.Shape
import raven.compose.ui.graphics.isSpecified
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.splitAroundWidget
import raven.compose.ui.text.LocalTextStyle
import raven.compose.ui.text.TextStyle
import raven.compose.ui.text.font.rememberRavenFont
import raven.compose.ui.text.input.PasswordVisualTransformation
import raven.compose.ui.text.input.VisualTransformation
import raven.compose.ui.unit.TextUnit
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.isSpecified

private val TextFieldDescriptor = WidgetDescriptor("TextField", R_WIDGET_TEXT_INPUT, events = listOf(TextChanged, TextCommitted))
private val SliderWidget = WidgetDescriptor("Slider", R_WIDGET_SLIDER, events = listOf(SliderChanged))
private val SliderOrientation = Property<ROrientation> { widget, orientation -> Rslider_set_orientation(widget, orientation) }
private val ComboBoxWidget = WidgetDescriptor("ComboBox", R_WIDGET_COMBO_BOX, events = listOf(ComboBoxChanged))
private val SpinBoxWidget = WidgetDescriptor("SpinBox", R_WIDGET_SPIN_BOX, events = listOf(SpinBoxChanged))
private val IntSpinBoxWidget = WidgetDescriptor("SpinBox", R_WIDGET_SPIN_BOX, events = listOf(IntSpinBoxChanged))

private val Placeholder = Property<String> { widget, text -> Rtext_input_set_placeholder(widget, text) }
private val ReadOnly = Property<Boolean> { widget, readOnly -> Rtext_input_set_read_only(widget, if (readOnly) R_TRUE else R_FALSE) }
private val Password = Property<VisualTransformation> { widget, transformation ->
    if (transformation is PasswordVisualTransformation && transformation.mask != '\u2022') {
        layoutWarningSink("TextField: Raven hides a password with bullets, so the mask '${transformation.mask}' is drawn as one")
    }
    Rtext_input_set_password(widget, if (transformation is PasswordVisualTransformation) R_TRUE else R_FALSE)
}
private val MaxLength = Property<Int?> { widget, maxLength -> Rtext_input_set_max_length(widget, maxLength ?: 0) }
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
 * [visualTransformation] `PasswordVisualTransformation()` hides the text and refuses to copy it.
 * [keyboardActions]' `onDone` is called when the user presses Enter.
 *
 * [maxLength], a compose-raven extension, limits what the user types or pastes to that many
 * characters (code points, so an emoji is one): Raven cuts the insertion inside the field, so the
 * caret stays after it. A [value] the app sets is shown whole, even when it is longer. Null is no
 * limit.
 *
 * [leadingIcon] and [trailingIcon] are composed over the field, centred at its start and its end,
 * and the field keeps its text clear of them (`Rtext_input_set_insets`), following their width.
 * A click on a button among them leaves the focus in the field (`Rwidget_set_focus_on_click`),
 * so a password's show/hide toggle doesn't stop the typing, while Tab still reaches it; an icon
 * that is only an image lets the click through to the field. The screen reader finds them after
 * the field. With icons, [modifier]'s layout and visuals go on a `Box` around the field and the
 * icons, and its test tag, semantics, focus and input handlers on the field itself; adding the
 * first icon or removing the last recreates the field.
 *
 * ```
 * var hidden by remember { mutableStateOf(true) }
 * TextField(password, { password = it },
 *     leadingIcon = { Icon(painterResource("icons/lock.svg"), contentDescription = null) },
 *     visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
 *     trailingIcon = {
 *         TextButton(onClick = { hidden = !hidden }) {
 *             Icon(painterResource(if (hidden) "icons/visibility.svg" else "icons/visibility_off.svg"),
 *                 contentDescription = if (hidden) "Show password" else "Hide password")
 *         }
 *     })
 * ```
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
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    maxLength: Int? = null,
    shape: Shape? = null,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    require(singleLine) { "TextField is single-line only; CodeEditor edits several lines" }
    require(maxLength == null || maxLength > 0) { "a TextField's maxLength must be positive, not $maxLength" }
    if (leadingIcon == null && trailingIcon == null) {
        TextFieldWidget(value, onValueChange, modifier, enabled, readOnly, textStyle, placeholder, visualTransformation, keyboardActions, maxLength, shape, colors, insets = null)
        return
    }
    val (outer, inner) = modifier.splitAroundWidget()
    val insets = remember { TextFieldInsets() }
    Box(outer, contentAlignment = Alignment.CenterStart) {
        TextFieldWidget(value, onValueChange, inner.fillMaxWidth(), enabled, readOnly, textStyle, placeholder, visualTransformation, keyboardActions, maxLength, shape, colors, insets)
        if (leadingIcon != null) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = IconGap).then(IconSlot(insets, leading = true))) { leadingIcon() }
        }
        if (trailingIcon != null) {
            Box(Modifier.align(Alignment.CenterEnd).padding(end = IconGap).then(IconSlot(insets, leading = false))) { trailingIcon() }
        }
    }
}

/** Between an icon and the field's edge, and between the icon and the text. */
private val IconGap = 4.dp

/** The text field's own widget, with [insets] following its icons when it has any. */
@Composable
private fun TextFieldWidget(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    readOnly: Boolean,
    textStyle: TextStyle,
    placeholder: String,
    visualTransformation: VisualTransformation,
    keyboardActions: KeyboardActions,
    maxLength: Int?,
    shape: Shape?,
    colors: TextFieldColors,
    insets: TextFieldInsets?,
) {
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
    RavenWidget(TextFieldDescriptor, modifier, {
        // Raven's field has no font setter of its own: the widget's font, which a subtree font
        // also sets, is its font.
        set(font) { setOwnFont(it) }
        bind(textStyle.fontSize, FontSizeMetric)
        bind(enabled, Enabled)
        bind(readOnly, ReadOnly)
        bind(shape, TextFieldShape)
        bind(roleColors, WidgetColors)
        bind(placeholder, Placeholder)
        bind(visualTransformation, Password)
        bind(maxLength, MaxLength)
        bind(value, TextInputText)
        bind(onValueChange, TextChanged)
        bind(keyboardActions.onDone, TextCommitted)
        bind(insets)
    })
}

/**
 * Keeps a field's text clear of its icons: once a frame, before the frame recomposes, the field's
 * insets become each icon's laid-out width and the gaps around it. Raven ignores an unchanged one.
 */
private class TextFieldInsets : WidgetFollower {
    var leading: Rid? = null
    var trailing: Rid? = null

    override fun attach(widget: Rid) = Unit

    override fun follow(widget: Rid) {
        Rtext_input_set_insets(widget, room(leading), room(trailing))
    }

    override fun detach(widget: Rid) = Unit

    private fun room(icon: Rid?): Float {
        if (icon == null || Rwidget_is_valid(icon) != R_TRUE) return 0f
        val width = memScoped {
            val x = alloc<FloatVar>()
            val y = alloc<FloatVar>()
            val w = alloc<FloatVar>()
            val h = alloc<FloatVar>()
            Rwidget_get_rect(icon, x.ptr, y.ptr, w.ptr, h.ptr)
            w.value
        }
        return if (width > 0f) width + IconGap.value * 2 else 0f
    }
}

/** An icon's box: [insets] reads its width, and a click in it leaves the focus in the field. */
private data class IconSlot(val insets: TextFieldInsets, val leading: Boolean) : WidgetAttachedElement {
    override fun attach(widget: Rid) {
        if (leading) insets.leading = widget else insets.trailing = widget
        Rwidget_set_focus_on_click(widget, R_FALSE)
    }

    override fun detach(widget: Rid) {
        if (leading && insets.leading == widget) insets.leading = null
        if (!leading && insets.trailing == widget) insets.trailing = null
    }
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
    RavenSlider(R_HORIZONTAL, value, onValueChange, modifier, enabled, valueRange, steps, colors)
}

/**
 * A [Slider] standing upright, its minimum at the bottom, as Material 3's `VerticalSlider` is;
 * controlled by [value] as [Slider] is.
 */
@Composable
public fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    colors: SliderColors = SliderDefaults.colors(),
) {
    // (Raven's vertical slider grows upwards already: nothing is flipped)
    RavenSlider(R_VERTICAL, value, onValueChange, modifier, enabled, valueRange, steps, colors)
}

@Composable
private fun RavenSlider(
    orientation: ROrientation,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    colors: SliderColors,
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
        bind(orientation, SliderOrientation)
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
