package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_CUSTOM
import raven.cinterop.Rid
import raven.cinterop.Rwidget_invalidate
import raven.cinterop.Rwidget_is_valid
import raven.compose.ui.CustomPaint
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.graphics.drawscope.DrawScope

private val CanvasWidget = WidgetDescriptor("Canvas", R_WIDGET_CUSTOM, events = listOf(CustomPaint))

/** The Canvas's widget, for the drawing's invalidation. */
private val CanvasOf = Property<CanvasState> { widget, state -> state.widget = widget }

/** Draws again when [Canvas]'s onDraw changes: a new lambda, the values it captured having changed. */
private val Redraw = Property<DrawScope.() -> Unit> { widget, _ -> Rwidget_invalidate(widget) }

/** The state a Canvas's last drawing read: a change to any of it draws it again, without recomposing. */
private class CanvasState {
    val observer = SnapshotStateObserver { it() }
    var widget: Rid = R_INVALID_ID

    private val invalidate: (Unit) -> Unit = { if (Rwidget_is_valid(widget) == R_TRUE) Rwidget_invalidate(widget) }

    fun draw(scope: DrawScope, onDraw: DrawScope.() -> Unit) = observer.observeReads(Unit, invalidate) { scope.onDraw() }
}

/**
 * A widget that [onDraw] draws, sized by [modifier] (it asks for no size of its own). [onDraw] runs
 * as Raven paints, which is when the widget needs it; state it reads draws it again when it changes,
 * without recomposing, so an animated value read in [onDraw] keeps it drawing.
 */
@Composable
public fun Canvas(modifier: Modifier, onDraw: DrawScope.() -> Unit) {
    val state = remember { CanvasState() }
    DisposableEffect(state) {
        state.observer.start()
        onDispose {
            state.observer.stop()
            state.observer.clear()
        }
    }
    RavenWidget(CanvasWidget, modifier, {
        bind(state, CanvasOf)
        bind(onDraw, Redraw)
        bind({ scope: DrawScope -> state.draw(scope, onDraw) }, CustomPaint)
    })
}
