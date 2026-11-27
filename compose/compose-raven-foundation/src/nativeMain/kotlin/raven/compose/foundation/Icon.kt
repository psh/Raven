@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import raven.compose.foundation.layout.size
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.Modifier
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.ColorFilter
import raven.compose.ui.graphics.isSpecified
import raven.compose.ui.graphics.painter.Painter
import raven.compose.ui.layout.ContentScale
import raven.compose.ui.unit.dp

/**
 * An icon in one colour: [painter] drawn in [tint], 24 dp unless [modifier] gives another size,
 * whatever the file's own. [tint] `Color.Unspecified`, the default, is the text colour where the icon
 * sits, so it follows a button's content colour, hover and the disabled fade; an icon in its own
 * colours is an `Image`. [contentDescription] names it for assistive technology; null makes it
 * decoration, as beside a label that says the same.
 *
 * A `painterResource` is drawn as a mask, its alpha only, so the tint gives exactly its colour: any
 * icon downloaded from fonts.google.com/icons, a Material Symbol or a Material Icon, black or the
 * site's grey, and a two-tone icon's lighter layer the tint, lighter. An SVG is drawn again at each
 * density and UI scale, so it stays sharp. Another painter is multiplied by the tint, as `Image`'s
 * colour filter is.
 *
 * ```kotlin
 * Icon(painterResource("icons/search.svg"), contentDescription = "Search")
 * ```
 */
@Composable
public fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val filter = remember(tint) { if (tint.isSpecified) ColorFilter.tint(tint) else ColorFilter.textColor() }
    // The last size in a chain wins, so the modifier's own size replaces the default.
    Image(painter.mask ?: painter, contentDescription, Modifier.size(IconDefaultSize).then(modifier), ContentScale.Fit, filter)
}

/** Compose's icon size. */
private val IconDefaultSize = 24.dp
