@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.foundation.relocation

import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rwidget_is_valid
import raven.cinterop.Rwidget_scroll_into_view
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.Modifier
import raven.compose.ui.WidgetAttachedElement

/**
 * Scrolls a widget into view from code: give the widget `Modifier.bringIntoViewRequester(it)`,
 * then call [bringIntoView] from a coroutine on the UI thread (`rememberCoroutineScope`, a
 * `LaunchedEffect`). Remember it with `remember { BringIntoViewRequester() }`.
 */
public sealed interface BringIntoViewRequester {
    /**
     * Scrolls every `VerticalScroll`, `HorizontalScroll`, `LazyColumn`, `LazyRow` and
     * `LazyVerticalGrid` around the widget, innermost first, just far enough that the widget is in
     * its view (`Rwidget_scroll_into_view`), laying the window out first if it needs it. Each
     * scrolls along its own axis only. Their `ScrollState`s read the new position on the next
     * frame. Raven doesn't animate scrolling, so it jumps and returns at once. A
     * requester attached to no widget does nothing, as Compose's does.
     *
     * Compose's takes a `rect` to bring a part of the widget into view; compose-raven has no
     * `Rect`, and Raven brings the whole widget.
     */
    public suspend fun bringIntoView()
}

/** A new [BringIntoViewRequester], attached to nothing. */
public fun BringIntoViewRequester(): BringIntoViewRequester = RavenBringIntoViewRequester()

private class RavenBringIntoViewRequester : BringIntoViewRequester {
    // The widgets of the nodes whose chains hold this requester, in the order they took it.
    val widgets = mutableListOf<Rid>()

    override suspend fun bringIntoView() {
        for (widget in widgets.toList()) {
            if (Rwidget_is_valid(widget) == R_TRUE) Rwidget_scroll_into_view(widget)
        }
    }
}

private data class BringIntoViewRequesterElement(val requester: RavenBringIntoViewRequester) : WidgetAttachedElement {
    override fun attach(widget: Rid) {
        requester.widgets += widget
    }

    override fun detach(widget: Rid) {
        requester.widgets -= widget
    }
}

/** Lets [bringIntoViewRequester] scroll this widget into view. */
public fun Modifier.bringIntoViewRequester(bringIntoViewRequester: BringIntoViewRequester): Modifier =
    this then BringIntoViewRequesterElement(bringIntoViewRequester as RavenBringIntoViewRequester)
