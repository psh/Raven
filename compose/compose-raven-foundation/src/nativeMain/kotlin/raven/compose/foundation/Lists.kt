package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import raven.cinterop.R_FALSE
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_LIST_VIEW
import raven.cinterop.R_WIDGET_TREE_VIEW
import raven.cinterop.Rid
import raven.cinterop.Rtree_item_destroy
import raven.cinterop.Rtree_item_is_expanded
import raven.cinterop.Rtree_item_set_expanded
import raven.cinterop.Rtree_item_set_icon
import raven.cinterop.Rtree_item_set_text
import raven.cinterop.Rtree_view_add_item
import raven.cinterop.Rtree_view_set_multi_select
import raven.cinterop.Rwidget_get_window
import raven.compose.ui.ListViewActivated
import raven.compose.ui.ListViewItemText
import raven.compose.ui.ListViewItems
import raven.compose.ui.ListViewSelected
import raven.compose.ui.ListViewSelectionChanged
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.TreeViewActivated
import raven.compose.ui.TreeViewContext
import raven.compose.ui.TreeViewSelection
import raven.compose.ui.TreeViewSelectionChanged
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.graphics.painter.Painter
import raven.compose.ui.graphics.painter.TextureRegion
import raven.compose.ui.rememberErrorReporter

private val ListViewWidget = WidgetDescriptor(
    "ListView", R_WIDGET_LIST_VIEW, events = listOf(ListViewItemText, ListViewSelectionChanged, ListViewActivated),
)
private val TreeViewWidget = WidgetDescriptor(
    "TreeView", R_WIDGET_TREE_VIEW, events = listOf(TreeViewSelectionChanged, TreeViewActivated, TreeViewContext),
)

/**
 * Raven's virtualised list of text rows, one for each of [items], showing `itemText(item)`: for long
 * lists, as only the rows shown are asked for. Its selection, the row at [selectedIndex] or none at
 * -1, is controlled as [Slider]'s value is: the user's choice calls [onSelectedIndexChange], and a
 * choice the app doesn't take reverts. [state] reveals rows; [onActivate] gets a row double-clicked,
 * or Enter pressed on it.
 * For rows of composables, see `LazyColumn`.
 */
@Composable
public fun <T> ListView(
    items: List<T>,
    itemText: (T) -> String,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: ListViewState = rememberListViewState(),
    onActivate: (Int) -> Unit = {},
) {
    val follower = remember { ListViewFollower() }
    require(selectedIndex in -1..items.lastIndex) { "selectedIndex $selectedIndex is not -1 or an index of the ${items.size} items" }
    val rowText: (Int) -> String = { index -> items.getOrNull(index)?.let(itemText) ?: "" }
    RavenWidget(ListViewWidget, modifier, {
        bind(follower)
        set(state) { follower.state = it }
        bind(rowText, ListViewItemText)
        // The rows before the selection: Raven selects nothing for a row it doesn't have.
        bind(items, ListViewItems)
        bind(selectedIndex, ListViewSelected)
        bind(onSelectedIndexChange, ListViewSelectionChanged)
        bind(onActivate, ListViewActivated)
    })
}

/**
 * Raven's virtualised tree of text rows: [roots] at the top, each item's [children] under it, each
 * showing `itemText(item)` and `itemIcon(item)`, and known by its [key], unique in the whole tree.
 * Each composition matches Raven's items to the data by key: an item whose text or icon changed is
 * updated in place, and what is new is added. Raven only appends items, so from the first item out of
 * place in a list of children, the rest are destroyed and added again, keeping whether they were
 * expanded.
 *
 * - The selection is a controlled set of keys, one at most unless [multiSelect]: the user's change
 *   calls [onSelectionChange], and one the app doesn't take reverts.
 * - Raven expands and collapses items itself and reports neither, so expansion isn't controlled:
 *   [initiallyExpanded] says whether an item new to the tree starts expanded.
 * - [onActivate] gets an item double-clicked, or Enter pressed on it.
 * - [contextMenuItems], given the item right-clicked (selected first) or null for the empty space,
 *   lists the menu opened at the pointer; none opens when it is null or the list is empty.
 */
@Composable
public fun <T> TreeView(
    roots: List<T>,
    children: (T) -> List<T>,
    itemText: (T) -> String,
    key: (T) -> Any,
    selection: Set<Any>,
    onSelectionChange: (Set<Any>) -> Unit,
    multiSelect: Boolean = false,
    initiallyExpanded: (T) -> Boolean = { false },
    onActivate: (T) -> Unit = {},
    contextMenuItems: ((T?) -> List<ContextMenuItem>)? = null,
    itemIcon: ((T) -> Painter?)? = null,
    modifier: Modifier = Modifier,
) {
    require(multiSelect || selection.size <= 1) { "a TreeView without multiSelect selects one item at most, not ${selection.size}" }
    val mirror = remember { TreeMirror<T>() }
    val reportError = rememberErrorReporter()
    val menu = remember(reportError) { ContextMenu(reportError) }
    DisposableEffect(menu) { onDispose { menu.discard() } }
    val data = TreeData(mirror, roots, children, itemText, key, initiallyExpanded, itemIcon)
    val onContext: (Rid, Float, Float) -> Unit = { item, x, y ->
        val entries = contextMenuItems?.invoke(mirror.itemOf(item)).orEmpty()
        menu.open(entries, Rwidget_get_window(mirror.tree), x, y)
    }
    RavenWidget(TreeViewWidget, modifier, {
        bind(multiSelect, MultiSelect)
        // The items before the selection, which is of their handles.
        bind(data, TreeItems)
        bind(data.selectedHandles(selection), TreeViewSelection)
        bind({ handles: Set<Rid> -> onSelectionChange(handles.mapNotNullTo(LinkedHashSet()) { mirror.keyOf(it) }) }, TreeViewSelectionChanged)
        bind({ item: Rid -> mirror.itemOf(item)?.let(onActivate) ?: Unit }, TreeViewActivated)
        bind(if (contextMenuItems != null) onContext else null, TreeViewContext)
    })
}

private val MultiSelect = Property<Boolean> { widget, multi -> Rtree_view_set_multi_select(widget, if (multi) R_TRUE else R_FALSE) }

/** Matches the tree's items to the data. Its value is new on each composition, so it does each time. */
private val TreeItems = Property<TreeData<*>> { widget, data -> data.sync(widget) }

/** What a TreeView is given in one composition, for its [TreeMirror]. */
private class TreeData<T>(
    val mirror: TreeMirror<T>,
    val roots: List<T>,
    val children: (T) -> List<T>,
    val itemText: (T) -> String,
    val key: (T) -> Any,
    val initiallyExpanded: (T) -> Boolean,
    val itemIcon: ((T) -> Painter?)?,
) {
    fun sync(tree: Rid) = mirror.sync(tree, this)

    /** The handles of the items of [keys] the tree has; a key it hasn't is left out. */
    fun selectedHandles(keys: Set<Any>): Set<Rid> = keys.mapNotNullTo(LinkedHashSet()) { mirror.handleOf(it) }
}

/** The tree view's icon call. Raven has no getter for it, so the tests replace [treeCalls] to see it. */
internal open class TreeCalls {
    open fun setIcon(item: Rid, texture: Rid, region: TextureRegion) =
        Rtree_item_set_icon(item, texture, region.minU, region.minV, region.maxU, region.maxV)
}

internal var treeCalls: TreeCalls = TreeCalls()

/** The Raven items of a TreeView, by key and by handle, as the last composition left them. */
private class TreeMirror<T> {
    class Item<T>(val key: Any, var value: T, val handle: Rid, var text: String, var icon: Painter?) {
        val children = mutableListOf<Item<T>>()
    }

    var tree: Rid = R_INVALID_ID
        private set
    private val roots = mutableListOf<Item<T>>()
    private val byKey = HashMap<Any, Item<T>>()
    private val byHandle = HashMap<Rid, Item<T>>()

    fun handleOf(key: Any): Rid? = byKey[key]?.handle

    fun keyOf(handle: Rid): Any? = byHandle[handle]?.key

    fun itemOf(handle: Rid): T? = byHandle[handle]?.value

    fun sync(tree: Rid, data: TreeData<T>) {
        this.tree = tree
        // Whether each item is expanded now, Raven's to change: an item added again keeps it.
        val expanded = byKey.mapValues { (_, item) -> Rtree_item_is_expanded(item.handle) == R_TRUE }
        val keys = HashSet<Any>()
        syncChildren(R_INVALID_ID, roots, data.roots, data, expanded, keys)
    }

    private fun syncChildren(
        parent: Rid,
        existing: MutableList<Item<T>>,
        values: List<T>,
        data: TreeData<T>,
        expanded: Map<Any, Boolean>,
        keys: HashSet<Any>,
    ) {
        val valueKeys = values.map { value -> data.key(value).also { require(keys.add(it)) { "the key $it is used by two items: keys must be unique" } } }
        var kept = 0
        while (kept < existing.size && kept < values.size && existing[kept].key == valueKeys[kept]) kept++
        // Raven only appends: from the first item out of place, the rest are destroyed.
        for (item in existing.subList(kept, existing.size)) forget(item)
        existing.subList(kept, existing.size).forEach { Rtree_item_destroy(it.handle) }
        existing.subList(kept, existing.size).clear()
        for (i in 0 until kept) {
            val item = existing[i]
            item.value = values[i]
            update(item, data)
            syncChildren(item.handle, item.children, data.children(values[i]), data, expanded, keys)
        }
        for (i in kept until values.size) {
            val value = values[i]
            val text = data.itemText(value)
            val item = Item(valueKeys[i], value, Rtree_view_add_item(tree, parent, text), text, null)
            existing += item
            byKey[item.key] = item
            byHandle[item.handle] = item
            update(item, data)
            syncChildren(item.handle, item.children, data.children(value), data, expanded, keys)
            // Once it has its children: an item without any can't be expanded.
            if (expanded[item.key] ?: data.initiallyExpanded(value)) Rtree_item_set_expanded(item.handle, R_TRUE)
        }
    }

    private fun update(item: Item<T>, data: TreeData<T>) {
        val text = data.itemText(item.value)
        if (text != item.text) {
            item.text = text
            Rtree_item_set_text(item.handle, text)
        }
        val icon = data.itemIcon?.invoke(item.value)
        if (icon != item.icon) {
            item.icon = icon
            if (icon == null) treeCalls.setIcon(item.handle, R_INVALID_ID, TextureRegion.Whole) else treeCalls.setIcon(item.handle, icon.texture, icon.region)
        }
        icon?.addViewer(tree)
    }

    /** Drops [item] and its descendants from the maps; Raven destroys them with it. */
    private fun forget(item: Item<T>) {
        if (byKey[item.key] === item) byKey.remove(item.key)
        byHandle.remove(item.handle)
        item.children.forEach(::forget)
    }
}
