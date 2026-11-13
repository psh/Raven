package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import raven.cinterop.R_FALSE
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_ROLE_NONE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_OVERLAY
import raven.cinterop.R_WIDGET_TAB_VIEW
import raven.cinterop.Rid
import raven.cinterop.Rslot_set_title
import raven.cinterop.Rtab_view_get_active
import raven.cinterop.Rtab_view_set_active
import raven.cinterop.Rtab_view_set_documents
import raven.cinterop.Rtab_view_set_tab_closable
import raven.cinterop.Rtab_view_set_tab_icon
import raven.cinterop.Rwidget_get_parent
import raven.cinterop.Rwidget_get_type
import raven.cinterop.Rwidget_is_valid
import raven.compose.foundation.layout.BoxScope
import raven.compose.foundation.layout.BoxScopeInstance
import raven.compose.ui.ChildKind
import raven.compose.ui.LayoutAxis
import raven.compose.ui.MainAxis
import raven.compose.ui.Modifier
import raven.compose.ui.PanelLayout
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.TabViewActive
import raven.compose.ui.TabViewChanged
import raven.compose.ui.TabViewClose
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.graphics.painter.Painter
import raven.compose.ui.graphics.painter.TextureRegion

private val TabViewWidget = WidgetDescriptor("TabView", R_WIDGET_TAB_VIEW, ChildKind.Many, events = listOf(TabViewChanged, TabViewClose))

/** The internal Box a [TabViewScope.Tab]'s page is in: no role, so the screen reader sees the page. */
private val TabPageWidget = WidgetDescriptor("Tab", R_WIDGET_OVERLAY, ChildKind.Many, role = R_ROLE_NONE)

private val Documents = Property<Boolean> { widget, documents -> Rtab_view_set_documents(widget, if (documents) R_TRUE else R_FALSE) }
private val TabTitle = Property<String> { widget, title -> Rslot_set_title(widget, title) }
private val TabPageOf = Property<TabPage> { widget, page -> page.widget = widget }
private val TabViewOf = Property<TabViewScope> { widget, scope -> scope.tabView = widget }

/** What the tabs of a [TabView] are declared in. */
public class TabViewScope internal constructor() {
    // The page of each tab, by its Box, for the close callback.
    internal val pages = HashMap<Rid, TabPage>()

    internal var tabView: Rid = R_INVALID_ID
    internal var selectedIndex: Int = -1

    /**
     * Shows the tab the app selected. Raven activates no tab it doesn't have, and moves the active
     * index as tabs come and go, so this follows each change of the tabs, not only the TabView's.
     */
    internal fun showSelected() {
        if (Rwidget_is_valid(tabView) != R_TRUE || selectedIndex < 0) return
        if (Rtab_view_get_active(tabView) != selectedIndex) Rtab_view_set_active(tabView, selectedIndex)
    }

    /**
     * A tab titled [title], its page [content]. In the documents form, a [closable] tab has a close
     * button, and a middle click closes it too: Raven keeps the tab and calls [onClose], and the tab
     * goes when the app leaves it out of the composition. [icon] goes before the title.
     */
    @Composable
    public fun Tab(
        title: String,
        closable: Boolean = true,
        onClose: () -> Unit = {},
        icon: Painter? = null,
        content: @Composable BoxScope.() -> Unit,
    ) {
        val page = remember { TabPage(this) }
        RavenWidget(TabPageWidget, Modifier, {
            bind(page, TabPageOf)
            bind(PanelLayout.Box)
            bind(title, TabTitle)
        }) { BoxScopeInstance.content() }
        // Raven sets these on the tab view, so once the page is in it.
        SideEffect {
            page.update(closable, onClose, icon)
            showSelected()
        }
        DisposableEffect(page) {
            onDispose {
                pages.remove(page.widget)
                showSelected()
            }
        }
    }
}

/** A tab's page, and what is set on its tab: Raven takes it only once the page is in the tab view. */
internal class TabPage(private val scope: TabViewScope) {
    var widget: Rid = R_INVALID_ID
        set(value) {
            field = value
            scope.pages[value] = this
        }
    var onClose: () -> Unit = {}
        private set
    private var closable: Boolean? = null
    private var icon: Painter? = null
    private var hasIcon = false

    fun update(closable: Boolean, onClose: () -> Unit, icon: Painter?) {
        this.onClose = onClose
        val tabView = Rwidget_get_parent(widget)
        if (tabView == R_INVALID_ID || Rwidget_get_type(tabView) != R_WIDGET_TAB_VIEW) return
        if (closable != this.closable) {
            this.closable = closable
            Rtab_view_set_tab_closable(tabView, widget, if (closable) R_TRUE else R_FALSE)
        }
        if (icon != this.icon || (icon == null && hasIcon)) {
            this.icon = icon
            hasIcon = icon != null
            if (icon == null) {
                tabCalls.setIcon(tabView, widget, R_INVALID_ID, TextureRegion.Whole)
            } else {
                tabCalls.setIcon(tabView, widget, icon.texture, icon.region)
                icon.addViewer(tabView)
            }
        }
    }
}

/** The tab view's icon call. Raven has no getter for it, so the tests replace [tabCalls] to see it. */
internal open class TabCalls {
    open fun setIcon(tabView: Rid, page: Rid, texture: Rid, region: TextureRegion) =
        Rtab_view_set_tab_icon(tabView, page, texture, region.minU, region.minV, region.maxU, region.maxV, R_FALSE)
}

internal var tabCalls: TabCalls = TabCalls()

/**
 * Raven's tabs, one for each [TabViewScope.Tab] in [content], showing the page of the tab at
 * [selectedIndex]. The selection is controlled as [Slider]'s value is: the user's choice calls
 * [onSelectedIndexChange], and one the app doesn't take reverts. With [documents], the tabs are an
 * application's documents: on the left, with icons and close buttons, as Raven's documents mode.
 * Each page fills the tab view.
 */
@Composable
public fun TabView(
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    documents: Boolean = false,
    content: @Composable TabViewScope.() -> Unit,
) {
    require(selectedIndex >= -1) { "selectedIndex must be -1 or an index, not $selectedIndex" }
    val scope = remember { TabViewScope() }
    val onClose: (Rid) -> Unit = { page -> scope.pages[page]?.onClose?.invoke() }
    RavenWidget(TabViewWidget, modifier, {
        bind(scope, TabViewOf)
        bind(documents, Documents)
        bind(PanelLayout(LayoutAxis.None, mainAxis = MainAxis.Cells))
        bind(selectedIndex, TabViewActive)
        bind(onSelectedIndexChange, TabViewChanged)
        bind(onClose, TabViewClose)
    }) { scope.content() }
    // Once the tabs are in: Raven activates no tab it doesn't have.
    SideEffect {
        scope.selectedIndex = selectedIndex
        scope.showSelected()
    }
}
