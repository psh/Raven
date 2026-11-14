package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import raven.cinterop.R_FALSE
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_ROLE_NONE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_DOCK_AREA
import raven.cinterop.R_WIDGET_OVERLAY
import raven.cinterop.Rdock_area_hide_panel
import raven.cinterop.Rdock_area_is_panel_open
import raven.cinterop.Rdock_area_load_layout
import raven.cinterop.Rdock_area_open_panel
import raven.cinterop.Rdock_area_save_layout
import raven.cinterop.Rdock_area_set_movable
import raven.cinterop.Rdock_area_set_panel_icon
import raven.cinterop.Rdock_area_set_tearable
import raven.cinterop.Rdock_area_show_panel
import raven.cinterop.Rid
import raven.cinterop.Rslot_set_title
import raven.cinterop.Rwidget_is_valid
import raven.cinterop.Rwidget_set_attribute
import raven.cinterop.Rwidget_set_name
import raven.compose.foundation.layout.BoxScope
import raven.compose.foundation.layout.BoxScopeInstance
import raven.compose.ui.ChildKind
import raven.compose.ui.DockAreaChanged
import raven.compose.ui.DockAreaClose
import raven.compose.ui.LayoutAxis
import raven.compose.ui.MainAxis
import raven.compose.ui.Modifier
import raven.compose.ui.PanelLayout
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.graphics.painter.Painter
import raven.compose.ui.graphics.painter.TextureRegion
import raven.compose.ui.layoutWarningSink
import raven.native.toRavenString

/**
 * Where a [DockAreaScope.Panel] goes when it is first added: along a side of the area, or of the
 * panel named `of`, taking `size` of it (0 to 1; 0 is Raven's, a quarter of the area or half of a
 * panel), or behind the tabs of another panel.
 */
public sealed interface Dock {
    public data class Left(val size: Float = 0f, val of: String? = null) : Dock
    public data class Right(val size: Float = 0f, val of: String? = null) : Dock
    public data class Top(val size: Float = 0f, val of: String? = null) : Dock
    public data class Bottom(val size: Float = 0f, val of: String? = null) : Dock

    /** Behind the tabs of the panel named [of]; null is the area's first group of tabs. */
    public data class Tab(val of: String? = null) : Dock
}

private val Dock.side: String
    get() = when (this) {
        is Dock.Left -> "Left"
        is Dock.Right -> "Right"
        is Dock.Top -> "Top"
        is Dock.Bottom -> "Bottom"
        is Dock.Tab -> "Tab"
    }

private val Dock.of: String?
    get() = when (this) {
        is Dock.Left -> of
        is Dock.Right -> of
        is Dock.Top -> of
        is Dock.Bottom -> of
        is Dock.Tab -> of
    }

private val Dock.size: Float
    get() = when (this) {
        is Dock.Left -> size
        is Dock.Right -> size
        is Dock.Top -> size
        is Dock.Bottom -> size
        is Dock.Tab -> 0f
    }

/**
 * A [DockArea]'s arrangement, which is Raven's: the user moves, resizes, tears off and closes the
 * panels. [layout] is it as text, kept up to date as snapshot state; [saveLayout] and [loadLayout]
 * keep it in settings, and [showPanel] brings a panel's tab to the front. Panels are known by their
 * names.
 */
@Stable
public class DockAreaState internal constructor() {
    internal var scope: DockAreaScope? = null
    private var pendingLayout: String? = null

    /** The arrangement as [saveLayout] gives it, after each change the user or the app made. */
    public var layout: String by mutableStateOf("")
        private set

    /** The arrangement as text, for [loadLayout]; empty before the area is shown. */
    public fun saveLayout(): String = attachedArea()?.let { Rdock_area_save_layout(it).toRavenString() } ?: ""

    /**
     * Arranges the panels as [text] says, and returns whether it is a layout; panels it doesn't
     * name keep a place. Given before the area is shown, it is kept and loaded once the panels are
     * in, and is taken to be one.
     */
    public fun loadLayout(text: String): Boolean {
        val area = attachedArea() ?: run {
            pendingLayout = text
            return true
        }
        val loaded = Rdock_area_load_layout(area, text) == R_TRUE
        refresh()
        return loaded
    }

    /** Brings the tab of the panel named [name] to the front; a panel hidden or unknown logs a warning. */
    public fun showPanel(name: String) {
        val area = attachedArea()
        val panel = scope?.panelNamed(name)
        when {
            area == null || panel == null -> layoutWarningSink("DockArea: no panel is named $name")
            Rdock_area_is_panel_open(area, panel) != R_TRUE -> layoutWarningSink("DockArea: the panel $name is hidden: open it to show it")
            else -> Rdock_area_show_panel(area, panel)
        }
    }

    /** Loads the layout given before the area was shown, now that its panels are in. */
    internal fun loadPending() {
        val text = pendingLayout ?: return
        pendingLayout = null
        val area = attachedArea() ?: return
        if (Rdock_area_load_layout(area, text) != R_TRUE) layoutWarningSink("DockArea: the layout given to loadLayout is not one")
    }

    internal fun refresh() {
        layout = saveLayout()
    }

    private fun attachedArea(): Rid? = scope?.area?.takeIf { Rwidget_is_valid(it) == R_TRUE }
}

/** A [DockAreaState] for a [DockArea]. */
@Composable
public fun rememberDockAreaState(): DockAreaState = remember { DockAreaState() }

private val DockAreaWidget = WidgetDescriptor("DockArea", R_WIDGET_DOCK_AREA, ChildKind.Docked, events = listOf(DockAreaClose, DockAreaChanged))

/** The internal Box a [DockAreaScope.Panel]'s content is in: no role, so the screen reader sees the content. */
private val PanelWidget = WidgetDescriptor("Panel", R_WIDGET_OVERLAY, ChildKind.Many, role = R_ROLE_NONE)

private val Movable = Property<Boolean> { widget, movable -> Rdock_area_set_movable(widget, if (movable) R_TRUE else R_FALSE) }
private val Tearable = Property<Boolean> { widget, tearable -> Rdock_area_set_tearable(widget, if (tearable) R_TRUE else R_FALSE) }
private val AreaOf = Property<DockAreaScope> { widget, scope -> scope.area = widget }
private val PanelOf = Property<DockPanel> { widget, panel -> panel.widget = widget }
private val PanelName = Property<String> { widget, name -> Rwidget_set_name(widget, name) }
private val PanelTitle = Property<String> { widget, title -> Rslot_set_title(widget, title) }
private val PanelClosable = Property<Boolean> { widget, closable -> Rwidget_set_attribute(widget, "Slot.Closable", closable.toString()) }

/** Where the panel goes when it is added, in its slot: Raven docks a child as its slot says. */
private val InitialDock = Property<Dock> { widget, dock ->
    Rwidget_set_attribute(widget, "Slot.Dock", dock.side)
    Rwidget_set_attribute(widget, "Slot.DockTo", dock.of ?: "")
    Rwidget_set_attribute(widget, "Slot.DockSize", dock.size.toString())
}

/** What the panels of a [DockArea] are declared in. */
public class DockAreaScope internal constructor(internal val state: DockAreaState) {
    internal var area: Rid = R_INVALID_ID

    // Each panel by its Box, for the close callback, and by its name, for showPanel.
    internal val panels = HashMap<Rid, DockPanel>()

    internal fun panelNamed(name: String): Rid? = panels.values.firstOrNull { it.name == name }?.widget

    /**
     * A panel named [name], unique in the area, with a tab titled [title], showing [content]. It is
     * docked as [initialDock] says when it is first added; from then on, where it is is Raven's.
     * [open] and [onClose] are controlled: the tab's close button, when [closable], calls [onClose]
     * and keeps the panel, which is hidden only when [open] becomes false, and put back where it
     * was, its tab in front, when it becomes true again. [icon] goes before the title.
     */
    @Composable
    public fun Panel(
        name: String,
        title: String,
        initialDock: Dock = Dock.Tab(),
        closable: Boolean = true,
        icon: Painter? = null,
        open: Boolean = true,
        onClose: () -> Unit = {},
        content: @Composable BoxScope.() -> Unit,
    ) {
        val panel = remember { DockPanel(this) }
        RavenWidget(PanelWidget, Modifier, {
            bind(panel, PanelOf)
            bind(PanelLayout.Box)
            bind(name, PanelName)
            bind(title, PanelTitle)
            bind(closable, PanelClosable)
            bind(initialDock, InitialDock)
        }) { BoxScopeInstance.content() }
        // Raven sets these on the area, so once the panel is in it.
        SideEffect {
            panel.name = name
            panel.update(icon, open, onClose)
        }
        DisposableEffect(panel) {
            // In the area now: the arrangement has it.
            state.refresh()
            onDispose {
                panels.remove(panel.widget)
                state.refresh()
            }
        }
    }
}

/** A panel of a dock area, and what is set on it through the area. */
internal class DockPanel(private val scope: DockAreaScope) {
    var widget: Rid = R_INVALID_ID
        set(value) {
            field = value
            scope.panels[value] = this
        }
    var name: String = ""
    var onClose: () -> Unit = {}
        private set
    private var icon: Painter? = null
    private var hasIcon = false

    fun update(icon: Painter?, open: Boolean, onClose: () -> Unit) {
        this.onClose = onClose
        val area = scope.area
        if (Rwidget_is_valid(area) != R_TRUE) return
        if (icon != this.icon || (icon == null && hasIcon)) {
            this.icon = icon
            hasIcon = icon != null
            if (icon == null) {
                dockCalls.setIcon(area, widget, R_INVALID_ID, TextureRegion.Whole)
            } else {
                dockCalls.setIcon(area, widget, icon.texture, icon.region)
                icon.addViewer(area)
            }
        }
        val isOpen = Rdock_area_is_panel_open(area, widget) == R_TRUE
        if (open != isOpen) {
            if (open) Rdock_area_open_panel(area, widget) else Rdock_area_hide_panel(area, widget)
            scope.state.refresh()
        }
    }
}

/** The dock area's icon call. Raven has no getter for it, so the tests replace [dockCalls] to see it. */
internal open class DockCalls {
    open fun setIcon(area: Rid, panel: Rid, texture: Rid, region: TextureRegion) =
        Rdock_area_set_panel_icon(area, panel, texture, region.minU, region.minV, region.maxU, region.maxV, R_FALSE)
}

internal var dockCalls: DockCalls = DockCalls()

/**
 * Raven's docked panels, one for each [DockAreaScope.Panel] in [content]: side by side, one above
 * the other, or behind the same tabs, as the user arranges them. With [movable] false the user can't
 * move them, only resize them; with [tearable] a tab dragged out takes its panel to a window of its
 * own. A torn-off panel stays in this window's composition, so window-level settings still refer to
 * this window. [state] holds the arrangement.
 */
@Composable
public fun DockArea(
    state: DockAreaState = rememberDockAreaState(),
    modifier: Modifier = Modifier,
    movable: Boolean = true,
    tearable: Boolean = true,
    content: @Composable DockAreaScope.() -> Unit,
) {
    val scope = remember(state) { DockAreaScope(state).also { state.scope = it } }
    val onClose: (Rid) -> Unit = { panel -> scope.panels[panel]?.onClose?.invoke() }
    val onChanged: () -> Unit = { state.refresh() }
    RavenWidget(DockAreaWidget, modifier, {
        bind(scope, AreaOf)
        bind(PanelLayout(LayoutAxis.None, mainAxis = MainAxis.Cells))
        bind(movable, Movable)
        bind(tearable, Tearable)
        bind(onClose, DockAreaClose)
        bind(onChanged, DockAreaChanged)
    }) { scope.content() }
    // Once the panels are in.
    SideEffect {
        state.loadPending()
        state.refresh()
    }
}
