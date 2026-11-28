@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.staticCFunction
import raven.cinterop.Rcarousel_set_page_changed_callback
import raven.cinterop.Rid

/** The user (a swipe, the keys, a dot, an arrow) or the autoplay went to another page: the page shown now. */
@InternalComposeRavenApi
public val CarouselPageChanged: Event<(page: Int) -> Unit> = Event { widget, userData ->
    Rcarousel_set_page_changed_callback(widget, onCarouselPageChanged, userData)
}

private val onCarouselPageChanged = staticCFunction { _: Rid, page: Int, userData: COpaquePointer? ->
    dispatchEvent(userData, CarouselPageChanged) { it(page) }
}
