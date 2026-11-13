package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import raven.cinterop.RStretch
import raven.cinterop.R_FALSE
import raven.cinterop.R_ROLE_IMAGE
import raven.cinterop.R_ROLE_NONE
import raven.cinterop.R_STRETCH_COVER
import raven.cinterop.R_STRETCH_FILL
import raven.cinterop.R_STRETCH_FIT
import raven.cinterop.R_STRETCH_NONE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_IMAGE
import raven.cinterop.Rid
import raven.cinterop.Rimage_set_corner_radius
import raven.cinterop.Rimage_set_region
import raven.cinterop.Rimage_set_stretch
import raven.cinterop.Rimage_set_texture
import raven.cinterop.Rimage_set_tint_color
import raven.cinterop.Rimage_set_tint_text
import raven.cinterop.Rwidget_set_accessible_name
import raven.cinterop.Rwidget_set_accessible_role
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.clipShape
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.ColorFilter
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.ui.graphics.RectangleShape
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.graphics.Shape
import raven.compose.ui.graphics.isSpecified
import raven.compose.ui.graphics.painter.BitmapPainter
import raven.compose.ui.graphics.painter.Painter
import raven.compose.ui.graphics.painter.TextureRegion
import raven.compose.ui.layout.ContentScale
import raven.compose.ui.layoutWarningSink

private val ImageWidget = WidgetDescriptor("Image", R_WIDGET_IMAGE, role = R_ROLE_IMAGE)

/** The texture and the part of it shown; a bitmap's widgets are redrawn by its update. */
private val ImagePainter = Property<Painter> { widget, painter ->
    imageCalls.setTexture(widget, painter.texture, painter.region)
    painter.addViewer(widget)
}

private val ImageContentScale = Property<ContentScale> { widget, scale ->
    imageCalls.setStretch(
        widget,
        when (scale) {
            ContentScale.Fit -> R_STRETCH_FIT
            ContentScale.Crop -> R_STRETCH_COVER
            ContentScale.FillBounds -> R_STRETCH_FILL
            ContentScale.None -> R_STRETCH_NONE
        },
    )
}

private val ImageColorFilter = Property<ColorFilter?> { widget, filter -> imageCalls.setTint(widget, filter) }

/** A described image is an image to assistive technology; one without a description is decoration. */
private val ImageDescription = Property<String?> { widget, description ->
    Rwidget_set_accessible_role(widget, if (description != null) R_ROLE_IMAGE else R_ROLE_NONE)
    Rwidget_set_accessible_name(widget, description)
}

/** The corners of `Modifier.clip`'s shape. Raven has one radius, so different corners use `topStart`, with a warning. */
private val ImageCorners = Property<Shape?> { widget, shape ->
    val radius = when (shape) {
        null, RectangleShape -> 0f
        is RoundedCornerShape -> {
            if (setOf(shape.topStart, shape.topEnd, shape.bottomEnd, shape.bottomStart).size > 1) {
                layoutWarningSink("Image: Raven rounds every corner alike, so the shape's topStart ${shape.topStart} is used for all four")
            }
            shape.topStart.value
        }
    }
    imageCalls.setCornerRadius(widget, radius)
}

/**
 * Shows [painter], fitted in the widget by [contentScale], drawn in [colorFilter]'s colour when
 * there is one. Without a size of its own, the widget is as large as the image, a pixel a logical
 * unit. [contentDescription] names the image for assistive technology; null makes it decoration.
 * `Modifier.clip(RoundedCornerShape(r))` rounds its corners.
 */
@Composable
public fun Image(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    colorFilter: ColorFilter? = null,
) {
    val corners = modifier.clipShape()
    RavenWidget(ImageWidget, modifier, {
        bind(painter, ImagePainter)
        bind(contentScale, ImageContentScale)
        bind(colorFilter, ImageColorFilter)
        bind(contentDescription, ImageDescription)
        bind(corners, ImageCorners)
    })
}

/** Shows the whole of [bitmap], as [Image] with a [BitmapPainter]. */
@Composable
public fun Image(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    colorFilter: ColorFilter? = null,
) {
    val painter = remember(bitmap) { BitmapPainter(bitmap) }
    Image(painter, contentDescription, modifier, contentScale, colorFilter)
}

/** The Image widget's calls. Raven has no getters for them, so the tests replace [imageCalls] to see them. */
internal open class ImageCalls {
    open fun setTexture(image: Rid, texture: Rid, region: TextureRegion) {
        Rimage_set_texture(image, texture)
        Rimage_set_region(image, region.minU, region.minV, region.maxU, region.maxV)
    }

    open fun setStretch(image: Rid, stretch: RStretch) = Rimage_set_stretch(image, stretch)

    /** No filter is white, which leaves the image as it is. */
    open fun setTint(image: Rid, filter: ColorFilter?) {
        val color = filter?.color ?: Color.White
        Rimage_set_tint_text(image, if (filter != null && !color.isSpecified) R_TRUE else R_FALSE)
        if (color.isSpecified) Rimage_set_tint_color(image, color.red, color.green, color.blue, color.alpha) else Rimage_set_tint_color(image, 1f, 1f, 1f, 1f)
    }

    open fun setCornerRadius(image: Rid, radius: Float) = Rimage_set_corner_radius(image, radius)
}

internal var imageCalls: ImageCalls = ImageCalls()
