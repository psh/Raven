package raven.compose.foundation

import androidx.compose.runtime.Composable
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import raven.cinterop.RShaderLook
import raven.cinterop.R_COLOR_BORDER
import raven.cinterop.R_COLOR_PANEL
import raven.cinterop.R_COLOR_TEXT
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_WIDGET_BORDER
import raven.cinterop.R_WIDGET_EXPANDER
import raven.cinterop.Rborder_set_background
import raven.cinterop.Rborder_set_corner_radius
import raven.cinterop.Rborder_set_effect
import raven.cinterop.Rborder_set_outline
import raven.cinterop.Rborder_set_shadow
import raven.cinterop.Rexpander_set_title
import raven.cinterop.Rid
import raven.cinterop.Rwidget_clear_color
import raven.cinterop.Rwidget_get_color
import raven.compose.ui.ChildKind
import raven.compose.ui.ContentKind
import raven.compose.ui.ExpanderChanged
import raven.compose.ui.ExpanderExpanded
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.graphics.BorderStroke
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.EffectStyle
import raven.compose.ui.graphics.RectangleShape
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.graphics.ShaderEffect
import raven.compose.ui.graphics.Shape
import raven.compose.ui.graphics.isSpecified
import raven.compose.ui.graphics.ravenColor
import raven.compose.ui.graphics.rememberShader
import raven.compose.ui.graphics.toRaven
import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.dp

private val SurfaceWidget = WidgetDescriptor("Surface", R_WIDGET_BORDER, ChildKind.Single, ContentKind.Box)
private val ExpanderWidget =
    WidgetDescriptor("Expander", R_WIDGET_EXPANDER, ChildKind.Single, ContentKind.Box, events = listOf(ExpanderChanged))

/** Unspecified clears the border's own panel colour, so the theme's shows again. */
private val SurfaceColor = Property<Color> { widget, color ->
    if (color.isSpecified) color.toRaven { r, g, b, a -> Rborder_set_background(widget, r, g, b, a) } else Rwidget_clear_color(widget, R_COLOR_PANEL)
}

/** Raven's corners are left to right, as `Start` and `End` are. */
private val SurfaceShape = Property<Shape> { widget, shape ->
    when (shape) {
        RectangleShape -> Rborder_set_corner_radius(widget, 0f, 0f, 0f, 0f)
        is RoundedCornerShape -> Rborder_set_corner_radius(
            widget,
            shape.topStart.value,
            shape.topEnd.value,
            shape.bottomEnd.value,
            shape.bottomStart.value,
        )
    }
}

/** No stroke is Raven's width 0, no outline. An unspecified stroke colour is the theme's border colour. */
private val SurfaceBorder = Property<BorderStroke?> { widget, border ->
    if (border == null) {
        Rborder_set_outline(widget, 0f, 0f, 0f, 0f, 0f)
    } else {
        val color = if (border.color.isSpecified) border.color else ravenColor { r, g, b, a -> Rwidget_get_color(widget, R_COLOR_BORDER, r, g, b, a) }
        color.toRaven { r, g, b, a -> Rborder_set_outline(widget, border.width.value, r, g, b, a) }
    }
}

/** An approximation of Compose's elevation: blur = elevation, offset down by half of it. 0 is none. */
private val SurfaceShadow = Property<Dp> { widget, elevation -> Rborder_set_shadow(widget, elevation.value, 0f, elevation.value / 2) }

/** An effect as the background, its look filled from the style; none removes it. */
private class SurfaceEffect(val shader: Rid, val style: EffectStyle, val radius: Float) {
    override fun equals(other: Any?): Boolean = other is SurfaceEffect && shader == other.shader && style == other.style && radius == other.radius

    override fun hashCode(): Int = (shader.hashCode() * 31 + style.hashCode()) * 31 + radius.hashCode()
}

/** Set again, the effect keeps its time: Raven only replaces the look, so it moves on unbroken. */
private val SurfaceEffectProperty = Property<SurfaceEffect> { widget, effect ->
    if (effect.shader == R_INVALID_ID) {
        effectCalls.setEffect(widget, R_INVALID_ID, null)
    } else {
        memScoped {
            val look = alloc<RShaderLook>()
            effect.style.fill(look.ptr, effect.shader, effect.radius)
            effectCalls.setEffect(widget, effect.shader, look.ptr)
        }
    }
}

/** The border's effect call. Raven has no getter for it, so the tests replace [effectCalls] to see it. */
internal open class EffectCalls {
    open fun setEffect(border: Rid, shader: Rid, look: CPointer<RShaderLook>?) = Rborder_set_effect(border, shader, look)
}

internal var effectCalls: EffectCalls = EffectCalls()

private val ExpanderTitle = Property<String> { widget, title -> Rexpander_set_title(widget, title) }

/**
 * A panel in [color], cut to [shape], outlined by [border] and casting a shadow [shadowElevation]
 * high. [Color.Unspecified] is the theme's panel colour. Its [content] is stacked as in a Box;
 * inner padding is the content's own `Modifier.padding`. [contentColor] is its children's text
 * colour, unless they have their own; [Color.Unspecified] is the theme's. [effect], a shader, is
 * drawn as its background, in [effectStyle], over [color] and under the content; a changed style
 * only replaces its look, so an effect whose colours follow state keeps moving unbroken.
 */
@Composable
public fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    effect: ShaderEffect? = null,
    effectStyle: EffectStyle = EffectStyle(),
    content: @Composable () -> Unit,
) {
    // Raven passes a widget's text colour down to its descendants.
    val roleColors = roleColors(R_COLOR_TEXT to contentColor)
    val shader = rememberShader(effect)
    val radius = (shape as? RoundedCornerShape)?.topStart?.value ?: 0f
    RavenWidget(SurfaceWidget, modifier, {
        bind(color, SurfaceColor)
        bind(roleColors, WidgetColors)
        bind(shape, SurfaceShape)
        bind(border, SurfaceBorder)
        bind(shadowElevation, SurfaceShadow)
        bind(SurfaceEffect(shader, effectStyle, radius), SurfaceEffectProperty)
    }, content)
}

/**
 * A header titled [title] that shows or hides its [content], stacked as in a Box. It shows
 * [expanded], which only the app changes: a click on the header calls [onExpandedChange] with the
 * new value, and if the app doesn't take it, the expander shows [expanded] again.
 */
@Composable
public fun Expander(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    RavenWidget(ExpanderWidget, modifier, {
        bind(title, ExpanderTitle)
        bind(expanded, ExpanderExpanded)
        bind(onExpandedChange, ExpanderChanged)
    }, content)
}
