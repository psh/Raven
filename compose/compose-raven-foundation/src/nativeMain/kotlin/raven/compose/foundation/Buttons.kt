package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import raven.cinterop.RColorRole
import raven.cinterop.RButtonStyle
import raven.cinterop.R_BUTTON_DEFAULT
import raven.cinterop.R_BUTTON_FLAT
import raven.cinterop.R_BUTTON_PRIMARY
import raven.cinterop.R_COLOR_ACCENT
import raven.cinterop.R_COLOR_ACCENT_TEXT
import raven.cinterop.R_COLOR_CONTROL
import raven.cinterop.R_COLOR_TEXT
import raven.cinterop.R_COLOR_TEXT_DISABLED
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
import raven.compose.ui.PaddingValues
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.Shape
import raven.compose.ui.text.ProvideTextStyle
import raven.compose.ui.theme.RavenTheme

private val ButtonStyle = Property<RButtonStyle> { widget, style -> Rbutton_set_style(widget, style) }

/** Whether the widget takes input; a disabled one is drawn dimmed. */
internal val Enabled = Property<Boolean> { widget, enabled -> Rwidget_set_enabled(widget, if (enabled) R_TRUE else R_FALSE) }

/**
 * One kind of button: its widget, its Raven [style], the roles its container and content colours
 * set, and its shape and padding properties, whose warnings name it.
 */
private class ButtonKind(name: String, val style: RButtonStyle, val containerRole: RColorRole, val contentRole: RColorRole) {
    val widget = WidgetDescriptor(name, R_WIDGET_BUTTON, ChildKind.Single, ContentKind.Row, events = listOf(ButtonClicked))
    val shape = cornerRadiusProperty(name)
    val contentPadding = contentPaddingProperty(name)
}

private val FilledButtonKind = ButtonKind("Button", R_BUTTON_PRIMARY, R_COLOR_ACCENT, R_COLOR_ACCENT_TEXT)
private val OutlinedButtonKind = ButtonKind("OutlinedButton", R_BUTTON_DEFAULT, R_COLOR_CONTROL, R_COLOR_TEXT)
private val TextButtonKind = ButtonKind("TextButton", R_BUTTON_FLAT, R_COLOR_CONTROL, R_COLOR_TEXT)

/**
 * A button's colours, from [ButtonDefaults.buttonColors]. [Color.Unspecified] keeps the theme's.
 * Raven has no colour for a disabled container: a disabled button dims its own.
 */
@Immutable
public data class ButtonColors(
    /** The button's fill: the accent of a [Button], the control colour of the others. */
    public val containerColor: Color,
    /** Its content's text: the text on the accent of a [Button], the text of the others. */
    public val contentColor: Color,
    public val disabledContentColor: Color,
    /** The fill under the pointer; unspecified derives it from [containerColor]. */
    public val hoveredContainerColor: Color = Color.Unspecified,
    /** The fill while pressed; unspecified derives it from [containerColor]. */
    public val pressedContainerColor: Color = Color.Unspecified,
)

public object ButtonDefaults {
    /**
     * Colours for any of the three buttons. A container colour also gives the button its
     * hovered and pressed shades, derived as the look in use derives the accent's. A hovered or
     * pressed container colour given is used as it is instead, each on its own, even without a
     * container colour: a transparent button can keep a faint hover.
     */
    public fun buttonColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified,
        disabledContentColor: Color = Color.Unspecified,
        hoveredContainerColor: Color = Color.Unspecified,
        pressedContainerColor: Color = Color.Unspecified,
    ): ButtonColors = ButtonColors(containerColor, contentColor, disabledContentColor, hoveredContainerColor, pressedContainerColor)
}

@Composable
private fun RavenButton(
    kind: ButtonKind,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape?,
    colors: ButtonColors,
    contentPadding: PaddingValues?,
    content: @Composable RowScope.() -> Unit,
) {
    val roleColors = roleColors(
        kind.containerRole to colors.containerColor,
        kind.contentRole to colors.contentColor,
        R_COLOR_TEXT_DISABLED to colors.disabledContentColor,
        ShadedRoles.getValue(kind.containerRole).first to colors.hoveredContainerColor,
        ShadedRoles.getValue(kind.containerRole).second to colors.pressedContainerColor,
    )
    RavenWidget(kind.widget, modifier, {
        bind(kind.style, ButtonStyle)
        bind(enabled, Enabled)
        bind(shape, kind.shape)
        bind(roleColors, WidgetColors)
        bind(contentPadding, kind.contentPadding)
        bind(onClick, ButtonClicked)
    }) { ProvideTextStyle(RavenTheme.typography.label) { RowScopeInstance.content() } }
}

/**
 * The main action: a filled button. Its [content] is laid out in a Row, centred; a child with
 * `Modifier.weight` makes the Row span the button and takes the room left.
 *
 * [shape] and [contentPadding] null are the theme's. Raven has one corner radius, so a shape with
 * different corners uses its `topStart`, and it pads opposite sides alike, so padding uses `start`
 * and `top`; either logs a warning.
 */
@Composable
public fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) {
    RavenButton(FilledButtonKind, onClick, modifier, enabled, shape, colors, contentPadding, content)
}

/** A secondary action: a button with Raven's default style. Its parameters are [Button]'s. */
@Composable
public fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) {
    RavenButton(OutlinedButtonKind, onClick, modifier, enabled, shape, colors, contentPadding, content)
}

/**
 * A low-emphasis action: a flat button, whose container colour shows only under the pointer. Its
 * parameters are [Button]'s.
 */
@Composable
public fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) {
    RavenButton(TextButtonKind, onClick, modifier, enabled, shape, colors, contentPadding, content)
}
