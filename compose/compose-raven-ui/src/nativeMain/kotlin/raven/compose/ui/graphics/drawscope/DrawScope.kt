@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui.graphics.drawscope

import cnames.structs.RPainter
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import raven.cinterop.RShaderLook
import raven.cinterop.R_INVALID_ID
import raven.cinterop.Rdraw_circle
import raven.cinterop.Rdraw_curve
import raven.cinterop.Rdraw_glass
import raven.cinterop.Rdraw_image
import raven.cinterop.Rdraw_line
import raven.cinterop.Rdraw_pop_clip_rect
import raven.cinterop.Rdraw_push_clip_rect
import raven.cinterop.Rdraw_rect
import raven.cinterop.Rdraw_rect_outline
import raven.cinterop.Rdraw_shader
import raven.cinterop.Rdraw_text
import raven.cinterop.Rdraw_triangle
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.RavenHost
import raven.compose.ui.geometry.Offset
import raven.compose.ui.geometry.Size
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.EffectStyle
import raven.compose.ui.graphics.ShaderEffect
import raven.compose.ui.graphics.painter.Painter
import raven.compose.ui.graphics.toRaven
import raven.compose.ui.text.font.FontFamily
import raven.compose.ui.text.font.font
import raven.compose.ui.unit.TextUnit
import raven.compose.ui.unit.isSpecified

/** How a shape is drawn: filled, or its outline. */
public sealed interface DrawStyle

/** The shape filled. */
public data object Fill : DrawStyle

/** The shape's outline, [width] thick, drawn inside its bounds. */
public data class Stroke(val width: Float = 1f) : DrawStyle {
    init {
        require(width > 0f) { "a stroke's width must be positive, not $width" }
    }
}

/**
 * What a `Canvas` draws with: Raven's painter, over a widget of [size], in logical units from its top
 * left corner. Raven draws no paths: beside Compose's rectangles, circles and lines, it draws cubic
 * curves, triangles, glass and shader effects. Valid only while the `Canvas` draws.
 */
public class DrawScope internal constructor(
    private val painter: CPointer<RPainter>,
    private val host: RavenHost,
    /** The size of the widget drawn on. */
    public val size: Size,
) {
    /** The middle of the widget. */
    public val center: Offset get() = size.center

    /** A rectangle, its top left corner at [topLeft]. */
    public fun drawRect(color: Color, topLeft: Offset = Offset.Zero, size: Size = this.size, style: DrawStyle = Fill): Unit =
        drawRoundRect(color, topLeft, size, 0f, style)

    /** A rectangle with corners of [cornerRadius]. */
    public fun drawRoundRect(
        color: Color,
        topLeft: Offset = Offset.Zero,
        size: Size = this.size,
        cornerRadius: Float = 0f,
        style: DrawStyle = Fill,
    ): Unit = color.toRaven { r, g, b, a ->
        when (style) {
            Fill -> Rdraw_rect(painter, topLeft.x, topLeft.y, size.width, size.height, cornerRadius, r, g, b, a)
            is Stroke -> Rdraw_rect_outline(painter, topLeft.x, topLeft.y, size.width, size.height, cornerRadius, style.width, r, g, b, a)
        }
    }

    /** A circle of [radius] around [center]; its outline is drawn inside it. */
    public fun drawCircle(color: Color, radius: Float = size.minDimension / 2, center: Offset = this.center, style: DrawStyle = Fill): Unit =
        when (style) {
            Fill -> color.toRaven { r, g, b, a -> Rdraw_circle(painter, center.x, center.y, radius, r, g, b, a) }
            is Stroke -> drawRoundRect(color, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), radius, style)
        }

    /** A line from [start] to [end], [strokeWidth] thick, with round caps. */
    public fun drawLine(color: Color, start: Offset, end: Offset, strokeWidth: Float = 1f): Unit =
        color.toRaven { r, g, b, a -> Rdraw_line(painter, start.x, start.y, end.x, end.y, strokeWidth, r, g, b, a) }

    /** A cubic Bézier curve from [start] to [end], pulled by [control1] and [control2]. Raven's. */
    public fun drawCubic(color: Color, start: Offset, control1: Offset, control2: Offset, end: Offset, strokeWidth: Float = 1f): Unit =
        color.toRaven { r, g, b, a ->
            Rdraw_curve(painter, start.x, start.y, control1.x, control1.y, control2.x, control2.y, end.x, end.y, strokeWidth, r, g, b, a)
        }

    /** A filled triangle. Raven's. */
    public fun drawTriangle(color: Color, p0: Offset, p1: Offset, p2: Offset): Unit =
        color.toRaven { r, g, b, a -> Rdraw_triangle(painter, p0.x, p0.y, p1.x, p1.y, p2.x, p2.y, r, g, b, a) }

    /**
     * [text] in [color], its first line's top left corner at [topLeft], at [fontSize] (the theme's when
     * unspecified) in [fontFamily] (the theme's when null).
     */
    public fun drawText(
        text: String,
        topLeft: Offset,
        color: Color,
        fontSize: TextUnit = TextUnit.Unspecified,
        fontFamily: FontFamily? = null,
    ) {
        val font = fontFamily?.let { host.font(it) } ?: R_INVALID_ID
        val size = if (fontSize.isSpecified) fontSize.value else 0f
        color.toRaven { r, g, b, a -> Rdraw_text(painter, font, size, topLeft.x, topLeft.y, text, r, g, b, a) }
    }

    /**
     * [painter]'s whole texture stretched over [size] at [topLeft], multiplied by [tint]. Raven draws
     * a texture whole, so a `BitmapPainter` of part of an image draws all of it.
     */
    public fun drawImage(painter: Painter, topLeft: Offset = Offset.Zero, size: Size = this.size, tint: Color = Color.White): Unit =
        tint.toRaven { r, g, b, a -> Rdraw_image(this.painter, painter.texture, topLeft.x, topLeft.y, size.width, size.height, r, g, b, a) }

    /**
     * A rectangle of glass with corners of [cornerRadius]: what was drawn before it, frosted and bent
     * along its rim, under [tint], whose alpha is its strength. Raven's; it needs a backend with glass.
     */
    public fun drawGlass(topLeft: Offset = Offset.Zero, size: Size = this.size, cornerRadius: Float = 0f, tint: Color = Color.Transparent): Unit =
        tint.toRaven { r, g, b, a -> Rdraw_glass(painter, topLeft.x, topLeft.y, size.width, size.height, cornerRadius, r, g, b, a) }

    /** [effect] drawn over a rectangle, in [style], with corners of [cornerRadius]. Raven's. */
    public fun drawShader(
        effect: ShaderEffect,
        topLeft: Offset = Offset.Zero,
        size: Size = this.size,
        style: EffectStyle = EffectStyle(),
        cornerRadius: Float = 0f,
    ) {
        val shader = effect.shader(host)
        if (shader == R_INVALID_ID) return
        memScoped {
            val look = alloc<RShaderLook>()
            style.fill(look.ptr, shader, cornerRadius)
            Rdraw_shader(painter, shader, topLeft.x, topLeft.y, size.width, size.height, look.ptr)
        }
    }

    /** Draws [block] clipped to the rectangle from ([left], [top]) to ([right], [bottom]). */
    public inline fun clipRect(
        left: Float = 0f,
        top: Float = 0f,
        right: Float = size.width,
        bottom: Float = size.height,
        block: DrawScope.() -> Unit,
    ) {
        pushClip(left, top, right - left, bottom - top)
        try {
            block()
        } finally {
            popClip()
        }
    }

    @PublishedApi
    internal fun pushClip(x: Float, y: Float, width: Float, height: Float): Unit = Rdraw_push_clip_rect(painter, x, y, width, height)

    @PublishedApi
    internal fun popClip(): Unit = Rdraw_pop_clip_rect(painter)
}
