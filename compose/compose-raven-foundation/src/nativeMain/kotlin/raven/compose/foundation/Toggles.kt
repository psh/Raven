package raven.compose.foundation

import androidx.compose.runtime.Composable
import raven.cinterop.RCheckboxStyle
import raven.cinterop.R_CHECKBOX_CHECK
import raven.cinterop.R_CHECKBOX_RADIO
import raven.cinterop.R_CHECKBOX_SWITCH
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

// Raven's checkbox text is never set: a label is a Text beside it, in a Row.

private val CheckboxWidget = WidgetDescriptor("Checkbox", R_WIDGET_CHECKBOX, events = listOf(CheckboxChanged))
private val SwitchWidget = WidgetDescriptor("Switch", R_WIDGET_CHECKBOX, events = listOf(CheckboxChanged))
private val RadioButtonWidget = WidgetDescriptor("RadioButton", R_WIDGET_CHECKBOX, events = listOf(RadioClicked))

private val CheckboxStyle = Property<RCheckboxStyle> { widget, style -> Rcheckbox_set_style(widget, style) }

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
) {
    Toggle(CheckboxWidget, R_CHECKBOX_CHECK, checked, onCheckedChange, modifier, enabled)
}

/** A switch showing [checked], controlled as [Checkbox] is. */
@Composable
public fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Toggle(SwitchWidget, R_CHECKBOX_SWITCH, checked, onCheckedChange, modifier, enabled)
}

@Composable
private fun Toggle(
    widget: WidgetDescriptor,
    style: RCheckboxStyle,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
) {
    RavenWidget(widget, modifier, {
        bind(style, CheckboxStyle)
        bind(enabled, Enabled)
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
) {
    RavenWidget(RadioButtonWidget, modifier, {
        bind(R_CHECKBOX_RADIO, CheckboxStyle)
        bind(enabled, Enabled)
        bind(selected, CheckboxChecked)
        bind(onClick, RadioClicked)
    })
}
