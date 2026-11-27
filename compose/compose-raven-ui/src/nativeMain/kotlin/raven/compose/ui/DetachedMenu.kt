package raven.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import raven.cinterop.R_WIDGET_MENU
import raven.cinterop.Rid
import raven.cinterop.Rwidget_create
import raven.cinterop.Rwidget_destroy

/**
 * A Raven Menu with no parent, its items composed by [content] in a composition of its own, for
 * a menu the system shows: a tray icon's, the Dock icon's. The composition takes the locals of
 * the place it is remembered in, and is disposed with the menu's widget when it leaves.
 */
@InternalComposeRavenApi
@Composable
public fun rememberDetachedMenu(content: @Composable () -> Unit): Rid {
    val host = LocalRavenHost.current
    val parent = rememberCompositionContext()
    val current by rememberUpdatedState(content)
    val menu = remember(host, parent) { DetachedMenu(host, parent).also { it.composition.setContent { current() } } }
    DisposableEffect(menu) { onDispose { menu.dispose() } }
    return menu.root.widget
}

@OptIn(InternalComposeRavenApi::class)
private class DetachedMenu(host: RavenHost, parent: CompositionContext) {
    val root = RavenNode(Rwidget_create(R_WIDGET_MENU), ChildKind.Many)
    val composition = Composition(RavenApplier(root, host::onChangesApplied), parent)

    /** Disposes the composition, which releases the items' nodes, then destroys the menu. */
    fun dispose() {
        composition.dispose()
        Rwidget_destroy(root.widget)
    }
}
