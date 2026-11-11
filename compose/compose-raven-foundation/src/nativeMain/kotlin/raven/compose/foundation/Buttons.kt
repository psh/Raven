package raven.compose.foundation

import androidx.compose.runtime.Composable
import raven.cinterop.RButtonStyle
import raven.cinterop.R_BUTTON_DEFAULT
import raven.cinterop.R_BUTTON_FLAT
import raven.cinterop.R_BUTTON_PRIMARY
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_BUTTON
import raven.cinterop.Rbutton_set_style
import raven.cinterop.Rwidget_set_enabled
import raven.compose.foundation.layout.RowScope
import raven.compose.foundation.layout.RowScopeInstance
import raven.compose.ui.ButtonClicked
import raven.compose.ui.ChildKind
import raven.compose.ui.ContentKind
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind

private val ButtonStyle = Property<RButtonStyle> { widget, style -> Rbutton_set_style(widget, style) }

/** Whether the widget takes input; a disabled one is drawn dimmed. */
internal val Enabled = Property<Boolean> { widget, enabled -> Rwidget_set_enabled(widget, if (enabled) R_TRUE else R_FALSE) }

private fun buttonWidget(name: String) =
    WidgetDescriptor(name, R_WIDGET_BUTTON, ChildKind.Single, ContentKind.Row, events = listOf(ButtonClicked))

private val ButtonWidget = buttonWidget("Button")
private val OutlinedButtonWidget = buttonWidget("OutlinedButton")
private val TextButtonWidget = buttonWidget("TextButton")

@Composable
private fun RavenButton(
    widget: WidgetDescriptor,
    style: RButtonStyle,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    RavenWidget(widget, modifier, {
        bind(style, ButtonStyle)
        bind(enabled, Enabled)
        bind(onClick, ButtonClicked)
    }) { RowScopeInstance.content() }
}

/** The main action: a filled button. Its [content] is laid out in a Row, centred. */
@Composable
public fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    RavenButton(ButtonWidget, R_BUTTON_PRIMARY, onClick, modifier, enabled, content)
}

/** A secondary action: a button with Raven's default style. */
@Composable
public fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    RavenButton(OutlinedButtonWidget, R_BUTTON_DEFAULT, onClick, modifier, enabled, content)
}

/** A low-emphasis action: a flat button. */
@Composable
public fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    RavenButton(TextButtonWidget, R_BUTTON_FLAT, onClick, modifier, enabled, content)
}
