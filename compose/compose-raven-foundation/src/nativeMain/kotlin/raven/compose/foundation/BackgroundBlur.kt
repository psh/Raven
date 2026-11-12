package raven.compose.foundation

import androidx.compose.runtime.Composable
import raven.cinterop.RBlurStyle
import raven.cinterop.R_BLUR_FROSTED
import raven.cinterop.R_BLUR_GLASS
import raven.cinterop.R_BLUR_NATIVE
import raven.cinterop.R_BLUR_PLAIN
import raven.cinterop.R_WIDGET_BACKGROUND_BLUR
import raven.cinterop.Rbackground_blur_clear_tint
import raven.cinterop.Rbackground_blur_set_corner_radius
import raven.cinterop.Rbackground_blur_set_grain
import raven.cinterop.Rbackground_blur_set_radius
import raven.cinterop.Rbackground_blur_set_saturation
import raven.cinterop.Rbackground_blur_set_shadow
import raven.cinterop.Rbackground_blur_set_style
import raven.cinterop.Rbackground_blur_set_tint
import raven.cinterop.Rid
import raven.compose.ui.ChildKind
import raven.compose.ui.ContentKind
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.RectangleShape
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.graphics.Shape
import raven.compose.ui.graphics.isSpecified
import raven.compose.ui.graphics.toRaven
import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.isSpecified

/** How a [BackgroundBlur] looks. */
public enum class BlurStyle {
    /** What is behind, out of focus, and nothing else. */
    Plain,

    /** Blurred, its colours a little stronger, under a veil of the theme's panel colour, a fine grain and a hairline. */
    Frosted,

    /** A slab of glass that bends what is behind at its rim and catches the light; the radius says how frosted it is. */
    Glass,

    /**
     * What the OS puts behind the window (`WindowBackdrop`: the blurred desktop, acrylic, mica),
     * under a light veil, blurred by the OS's own radius. It needs `Window(transparent = true)`;
     * anywhere else it is drawn as [Frosted].
     */
    Native,
}

private val BackgroundBlurWidget = WidgetDescriptor("BackgroundBlur", R_WIDGET_BACKGROUND_BLUR, ChildKind.Single, ContentKind.Box)

/**
 * The Raven setters a [BackgroundBlur]'s parameters go through. Raven reads back only the style
 * and the radius, so tests record the others through [backgroundBlurTarget].
 */
internal interface BlurTarget {
    fun style(style: RBlurStyle)

    /** Negative is the theme's. */
    fun radius(radius: Float)

    /** Null removes the tint. */
    fun tint(tint: Color?)

    /** Negative is the style's. */
    fun saturation(saturation: Float)

    /** Negative is the style's. */
    fun grain(grain: Float)

    fun cornerRadius(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float)

    /** 0 is none. */
    fun shadow(blur: Float)
}

/** The setters of one BackgroundBlur widget. */
internal class RavenBlurTarget(private val widget: Rid) : BlurTarget {
    override fun style(style: RBlurStyle) = Rbackground_blur_set_style(widget, style)

    override fun radius(radius: Float) = Rbackground_blur_set_radius(widget, radius)

    override fun tint(tint: Color?) {
        if (tint == null) Rbackground_blur_clear_tint(widget) else tint.toRaven { r, g, b, a -> Rbackground_blur_set_tint(widget, r, g, b, a) }
    }

    override fun saturation(saturation: Float) = Rbackground_blur_set_saturation(widget, saturation)

    override fun grain(grain: Float) = Rbackground_blur_set_grain(widget, grain)

    override fun cornerRadius(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float) =
        Rbackground_blur_set_corner_radius(widget, topLeft, topRight, bottomRight, bottomLeft)

    override fun shadow(blur: Float) = Rbackground_blur_set_shadow(widget, blur)
}

/** The setters of a BackgroundBlur widget: [RavenBlurTarget]'s, which tests wrap to record them. */
internal var backgroundBlurTarget: (Rid) -> BlurTarget = ::RavenBlurTarget

private val BlurStyleProperty = Property<BlurStyle> { widget, style ->
    backgroundBlurTarget(widget).style(
        when (style) {
            BlurStyle.Plain -> R_BLUR_PLAIN
            BlurStyle.Frosted -> R_BLUR_FROSTED
            BlurStyle.Glass -> R_BLUR_GLASS
            BlurStyle.Native -> R_BLUR_NATIVE
        },
    )
}

/** Raven's corners are left to right, as `Start` and `End` are. */
private val BlurShape = Property<Shape> { widget, shape ->
    when (shape) {
        RectangleShape -> backgroundBlurTarget(widget).cornerRadius(0f, 0f, 0f, 0f)
        is RoundedCornerShape -> backgroundBlurTarget(widget).cornerRadius(
            shape.topStart.value,
            shape.topEnd.value,
            shape.bottomEnd.value,
            shape.bottomStart.value,
        )
    }
}

/** Unspecified removes the tint, so the style's shows again. */
private val BlurTint = Property<Color> { widget, tint -> backgroundBlurTarget(widget).tint(tint.takeIf { it.isSpecified }) }

private val BlurRadius = Property<Dp> { widget, radius -> backgroundBlurTarget(widget).radius(if (radius.isSpecified) radius.value else -1f) }

private val BlurSaturation = Property<Float?> { widget, saturation -> backgroundBlurTarget(widget).saturation(saturation ?: -1f) }

private val BlurGrain = Property<Float?> { widget, grain -> backgroundBlurTarget(widget).grain(grain ?: -1f) }

private val BlurShadow = Property<Dp> { widget, elevation -> backgroundBlurTarget(widget).shadow(elevation.value) }

/**
 * A panel that shows what is behind it out of focus, in [style], with its [content] over it,
 * stacked as in a Box; inner padding is the content's own `Modifier.padding`. It is cut to [shape]
 * and casts a soft shadow [shadowElevation] high, 0 being none.
 *
 * [tint] is laid over the blur, its alpha the strength; [Color.Unspecified] leaves it to the style
 * (none for [BlurStyle.Plain], the theme's panel colour for the others). [radius] is how far what
 * is behind goes out of focus (4.dp softens, 20.dp hides the details, 60.dp leaves only colours),
 * [Dp.Unspecified] being the theme's. [saturation] (1 as it is, 0 grey) and [grain] (0 none to 1)
 * are the style's when null.
 *
 * For an ordinary glass panel, `Modifier.material(Material.FrostedGlass)` is enough; this is for
 * [BlurStyle.Native], [BlurStyle.Plain], or control over the blur.
 */
@Composable
public fun BackgroundBlur(
    modifier: Modifier = Modifier,
    style: BlurStyle = BlurStyle.Frosted,
    shape: Shape = RectangleShape,
    tint: Color = Color.Unspecified,
    radius: Dp = Dp.Unspecified,
    saturation: Float? = null,
    grain: Float? = null,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    RavenWidget(BackgroundBlurWidget, modifier, {
        bind(style, BlurStyleProperty)
        bind(shape, BlurShape)
        bind(tint, BlurTint)
        bind(radius, BlurRadius)
        bind(saturation, BlurSaturation)
        bind(grain, BlurGrain)
        bind(shadowElevation, BlurShadow)
    }, content)
}
