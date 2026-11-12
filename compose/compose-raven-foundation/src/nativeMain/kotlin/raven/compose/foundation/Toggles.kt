package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import raven.cinterop.RCheckboxStyle
import raven.cinterop.R_CHECKBOX_CHECK
import raven.cinterop.R_CHECKBOX_RADIO
import raven.cinterop.R_CHECKBOX_SWITCH
import raven.cinterop.R_COLOR_ACCENT
import raven.cinterop.R_COLOR_ACCENT_TEXT
import raven.cinterop.R_COLOR_CONTROL_BORDER
import raven.cinterop.R_COLOR_HANDLE
import raven.cinterop.R_COLOR_SWITCH
import raven.cinterop.R_COLOR_TRACK
import raven.cinterop.R_WIDGET_CHECKBOX
import raven.cinterop.Rcheckbox_set_style
import raven.compose.ui.CheckboxChanged
import raven.compose.ui.CheckboxChecked
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RadioClicked
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.graphics.Color

// Raven's checkbox text is never set: a label is a Text beside it, in a Row.

private val CheckboxWidget = WidgetDescriptor("Checkbox", R_WIDGET_CHECKBOX, events = listOf(CheckboxChanged))
private val SwitchWidget = WidgetDescriptor("Switch", R_WIDGET_CHECKBOX, events = listOf(CheckboxChanged))
private val RadioButtonWidget = WidgetDescriptor("RadioButton", R_WIDGET_CHECKBOX, events = listOf(RadioClicked))

private val CheckboxStyle = Property<RCheckboxStyle> { widget, style -> Rcheckbox_set_style(widget, style) }

/** A [Checkbox]'s colours, from [CheckboxDefaults.colors]. [Color.Unspecified] keeps the theme's. */
@Immutable
public data class CheckboxColors(
    /** The fill of a checked box. */
    public val checkedColor: Color,
    /** The outline of an unchecked box. */
    public val uncheckedColor: Color,
    public val checkmarkColor: Color,
)

public object CheckboxDefaults {
    /**
     * A checked colour also gives the box its hovered and pressed shades, derived as the look in
     * use derives the accent's.
     */
    public fun colors(
        checkedColor: Color = Color.Unspecified,
        uncheckedColor: Color = Color.Unspecified,
        checkmarkColor: Color = Color.Unspecified,
    ): CheckboxColors = CheckboxColors(checkedColor, uncheckedColor, checkmarkColor)
}

/** A [Switch]'s colours, from [SwitchDefaults.colors]. [Color.Unspecified] keeps the theme's. */
@Immutable
public data class SwitchColors(
    public val checkedTrackColor: Color,
    public val uncheckedTrackColor: Color,
    public val thumbColor: Color,
)

public object SwitchDefaults {
    public fun colors(
        checkedTrackColor: Color = Color.Unspecified,
        uncheckedTrackColor: Color = Color.Unspecified,
        thumbColor: Color = Color.Unspecified,
    ): SwitchColors = SwitchColors(checkedTrackColor, uncheckedTrackColor, thumbColor)
}

/** A [RadioButton]'s colours, from [RadioButtonDefaults.colors]. [Color.Unspecified] keeps the theme's. */
@Immutable
public data class RadioButtonColors(
    /** The dot and ring of a selected radio. */
    public val selectedColor: Color,
    /** The ring of an unselected one. */
    public val unselectedColor: Color,
)

public object RadioButtonDefaults {
    /** A selected colour also gives the radio its hovered and pressed shades, as [CheckboxDefaults.colors] does. */
    public fun colors(
        selectedColor: Color = Color.Unspecified,
        unselectedColor: Color = Color.Unspecified,
    ): RadioButtonColors = RadioButtonColors(selectedColor, unselectedColor)
}

/**
 * A box showing [checked], which only the app changes: a click calls [onCheckedChange] with the
 * new value, and if the app doesn't take it, the box shows [checked] again.
 */
@Composable
public fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
) {
    val roleColors = roleColors(
        R_COLOR_ACCENT to colors.checkedColor,
        R_COLOR_CONTROL_BORDER to colors.uncheckedColor,
        R_COLOR_ACCENT_TEXT to colors.checkmarkColor,
    )
    Toggle(CheckboxWidget, R_CHECKBOX_CHECK, checked, onCheckedChange, modifier, enabled, roleColors)
}

/** A switch showing [checked], controlled as [Checkbox] is. */
@Composable
public fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
) {
    val roleColors = roleColors(
        R_COLOR_SWITCH to colors.checkedTrackColor,
        R_COLOR_TRACK to colors.uncheckedTrackColor,
        R_COLOR_HANDLE to colors.thumbColor,
    )
    Toggle(SwitchWidget, R_CHECKBOX_SWITCH, checked, onCheckedChange, modifier, enabled, roleColors)
}

@Composable
private fun Toggle(
    widget: WidgetDescriptor,
    style: RCheckboxStyle,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
    colors: RoleColors,
) {
    RavenWidget(widget, modifier, {
        bind(style, CheckboxStyle)
        bind(enabled, Enabled)
        bind(colors, WidgetColors)
        bind(checked, CheckboxChecked)
        bind(onCheckedChange, CheckboxChanged)
    })
}

/**
 * One choice of a group, showing [selected]. A click calls [onClick]; what is selected is always
 * the app's state, even though Raven unchecks a radio's siblings by itself.
 */
@Composable
public fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
) {
    val roleColors = roleColors(R_COLOR_ACCENT to colors.selectedColor, R_COLOR_CONTROL_BORDER to colors.unselectedColor)
    RavenWidget(RadioButtonWidget, modifier, {
        bind(R_CHECKBOX_RADIO, CheckboxStyle)
        bind(enabled, Enabled)
        bind(roleColors, WidgetColors)
        bind(selected, CheckboxChecked)
        bind(onClick, RadioClicked)
    })
}
