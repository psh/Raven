package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import raven.cinterop.R_HEADER_CLOSE
import raven.cinterop.R_HEADER_MAXIMIZE
import raven.cinterop.R_HEADER_MINIMIZE
import raven.cinterop.R_HEADER_NO_BUTTONS
import raven.cinterop.R_WIDGET_HEADER_BAR
import raven.cinterop.Rheader_bar_set_buttons
import raven.cinterop.Rheader_bar_set_title
import raven.compose.ui.AlignElement
import raven.compose.ui.Alignment
import raven.compose.ui.ChildKind
import raven.compose.ui.LayoutAxis
import raven.compose.ui.MainAxis
import raven.compose.ui.Modifier
import raven.compose.ui.PanelLayout
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.layoutWarningSink
import raven.compose.ui.windowHasCustomHeader

/** A button a [HeaderBar] draws for its window. */
public enum class WindowButton {
    Close,
    Minimize,

    /** Shown only on a resizable window. */
    Maximize,
    ;

    public companion object {
        /** Every button: the default. */
        public fun all(): Set<WindowButton> = entries.toSet()
    }
}

/** What a [HeaderBar]'s children can say about their place in it. */
public interface HeaderBarScope {
    /**
     * [Alignment.End] packs the child on the right, before the window's buttons; [Alignment.Start],
     * the default, packs it on the left. A header bar can't centre a child: the title is there.
     */
    public fun Modifier.align(alignment: Alignment.Horizontal): Modifier = this then AlignElement(alignment, null)
}

private object HeaderBarScopeInstance : HeaderBarScope

private val HeaderBarWidget = WidgetDescriptor("HeaderBar", R_WIDGET_HEADER_BAR, ChildKind.Many)

/** Raven shows the window's title for a null one. */
private val HeaderBarTitle = Property<String?> { widget, title -> Rheader_bar_set_title(widget, title) }

private val HeaderBarButtons = Property<Set<WindowButton>> { widget, buttons ->
    var mask = R_HEADER_NO_BUTTONS
    if (WindowButton.Close in buttons) mask = mask or R_HEADER_CLOSE
    if (WindowButton.Minimize in buttons) mask = mask or R_HEADER_MINIMIZE
    if (WindowButton.Maximize in buttons) mask = mask or R_HEADER_MAXIMIZE
    Rheader_bar_set_buttons(widget, mask)
}

/**
 * The window's title bar, drawn in its content: it shows [title] (`null` shows the window's) and
 * the window [buttons], and the user drags the window by it. Its children are packed from the
 * left, or from the right with `Modifier.align(Alignment.End)`, each centred vertically.
 *
 * It takes the place of the system's title bar only in a `Window(customHeader = true)`; in any
 * other window it logs a warning when it enters the composition.
 */
@Composable
public fun HeaderBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    buttons: Set<WindowButton> = WindowButton.all(),
    content: @Composable HeaderBarScope.() -> Unit = {},
) {
    val customHeader = windowHasCustomHeader()
    DisposableEffect(Unit) {
        if (!customHeader) layoutWarningSink("HeaderBar: the window has no customHeader, so the system's title bar stays above it")
        onDispose {}
    }
    RavenWidget(HeaderBarWidget, modifier, {
        bind(PanelLayout(LayoutAxis.Horizontal, vertical = Alignment.CenterVertically, mainAxis = MainAxis.Packed))
        bind(title, HeaderBarTitle)
        bind(buttons, HeaderBarButtons)
    }) { HeaderBarScopeInstance.content() }
}
