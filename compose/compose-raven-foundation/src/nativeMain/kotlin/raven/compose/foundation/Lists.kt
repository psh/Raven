package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import raven.cinterop.R_FALSE
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_LIST_VIEW
import raven.cinterop.R_WIDGET_TREE_VIEW
import raven.cinterop.Rid
import raven.cinterop.Rtree_item_destroy
import raven.cinterop.Rtree_item_is_expanded
import raven.cinterop.Rtree_item_set_icon
import raven.cinterop.Rtree_item_set_selected
import raven.cinterop.Rtree_item_set_text
import raven.cinterop.Rtree_view_add_item
import raven.cinterop.Rtree_view_clear_selection
import raven.cinterop.Rtree_view_get_selection
import raven.cinterop.Rtree_view_get_selection_count
import raven.cinterop.Rtree_view_set_multi_select
import raven.cinterop.Rtree_view_scroll_to_item
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
import raven.compose.ui.TreeViewExpandedChanged
import raven.compose.ui.TreeViewExpansion
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
    "TreeView", R_WIDGET_TREE_VIEW, events = listOf(TreeViewSelectionChanged, TreeViewExpandedChanged, TreeViewActivated, TreeViewContext),
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
        // A copy: a list changed in place, a SnapshotStateList say, is equal to itself.
        bind(items.toList(), ListViewItems)
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
 * - The expansion is too: [expanded] holds the keys of the open items, and the user's change (the
 *   arrow, a double click, Left and Right, the branches a selection opens) calls
 *   [onExpandedChange]. Hold it in `rememberSaveable` to keep it. A collapse that moves the
 *   selection to the collapsed item calls [onSelectionChange] too.
 * - [children] is asked for only for the items shown (the roots, and the children of open items),
 *   so their arrows show: a file tree lists a folder once its parent is open. Items Raven has stay
 *   when their branch closes.
 * - [onActivate] gets an item double-clicked, or Enter pressed on it.
 * - [contextMenuItems], given the item right-clicked (selected first) or null for the empty space,
 *   lists the menu opened at the pointer; none opens when it is null or the list is empty.
 * - [state] reveals an item by key, opening the branches above it.
 */
@Composable
public fun <T> TreeView(
    roots: List<T>,
    children: (T) -> List<T>,
    itemText: (T) -> String,
    key: (T) -> Any,
    selection: Set<Any>,
    onSelectionChange: (Set<Any>) -> Unit,
    expanded: Set<Any>,
    onExpandedChange: (Set<Any>) -> Unit,
    multiSelect: Boolean = false,
    onActivate: (T) -> Unit = {},
    contextMenuItems: ((T?) -> List<ContextMenuItem>)? = null,
    itemIcon: ((T) -> Painter?)? = null,
    modifier: Modifier = Modifier,
    state: TreeViewState = rememberTreeViewState(),
) {
    require(multiSelect || selection.size <= 1) { "a TreeView without multiSelect selects one item at most, not ${selection.size}" }
    val mirror = remember { TreeMirror<T>() }
    val reportError = rememberErrorReporter()
    val menu = remember(reportError) { ContextMenu(reportError) }
    DisposableEffect(menu) { onDispose { menu.discard() } }
    val data = TreeData(mirror, roots, children, itemText, key, expanded, itemIcon, selection)
    state.reveal = { target -> mirror.reveal(target, data, onExpandedChange) }
    val onContext: (Rid, Float, Float) -> Unit = { item, x, y ->
        val entries = contextMenuItems?.invoke(mirror.itemOf(item)).orEmpty()
        menu.open(entries, Rwidget_get_window(mirror.tree), x, y)
    }
    RavenWidget(TreeViewWidget, modifier, {
        bind(multiSelect, MultiSelect)
        // The items before the expansion and the selection, which are of their handles.
        bind(data, TreeItems)
        bind(data, TreeViewExpansion) { mirror.handlesOf(expanded) }
        bind(data, TreeReveal)
        bind(data.selectedHandles(selection), TreeViewSelection)
        bind({ handles: Set<Rid> -> onSelectionChange(handles.mapNotNullTo(LinkedHashSet()) { mirror.keyOf(it) }) }, TreeViewSelectionChanged)
        bind({ _: Rid, _: Boolean -> onExpandedChange(mirror.expandedKeys(expanded)) }, TreeViewExpandedChanged)
        bind({ item: Rid -> mirror.itemOf(item)?.let(onActivate) ?: Unit }, TreeViewActivated)
        bind(if (contextMenuItems != null) onContext else null, TreeViewContext)
    })
}

/**
 * What an app can ask of a [TreeView]: [revealItem]. Raven can't report the tree's position, so it
 * isn't saved; on a state not given to a tree, [revealItem] does nothing.
 */
@Stable
public class TreeViewState internal constructor() {
    internal var reveal: ((Any) -> Unit)? = null

    /**
     * Shows the item of [key]: one inside a closed branch calls `onExpandedChange` with the branches
     * above it added, and is scrolled to once they are open; a shown one is scrolled to at once
     * (`Rtree_view_scroll_to_item`).
     */
    public suspend fun revealItem(key: Any) {
        reveal?.invoke(key)
    }
}

/** A [TreeViewState]. */
@Composable
public fun rememberTreeViewState(): TreeViewState = remember { TreeViewState() }

private val MultiSelect = Property<Boolean> { widget, multi -> Rtree_view_set_multi_select(widget, if (multi) R_TRUE else R_FALSE) }

/** Matches the tree's items to the data. Its value is new on each composition, so it does each time. */
private val TreeItems = Property<TreeData<*>> { widget, data -> data.sync(widget) }

/** Scrolls to the item `revealItem` asked for, once the expansion above it is shown. */
private val TreeReveal = Property<TreeData<*>> { _, data -> data.mirror.revealPending() }

/** What a TreeView is given in one composition, for its [TreeMirror]. */
private class TreeData<T>(
    val mirror: TreeMirror<T>,
    val roots: List<T>,
    val children: (T) -> List<T>,
    val itemText: (T) -> String,
    val key: (T) -> Any,
    val expanded: Set<Any>,
    val itemIcon: ((T) -> Painter?)?,
    val selection: Set<Any>,
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

    // The item revealItem asked for, scrolled to once the branches above it are open.
    private var pendingReveal: Any? = null

    fun handleOf(key: Any): Rid? = byKey[key]?.handle

    fun keyOf(handle: Rid): Any? = byHandle[handle]?.key

    fun itemOf(handle: Rid): T? = byHandle[handle]?.value

    /** The handles of the items of [keys] the tree has. */
    fun handlesOf(keys: Set<Any>): Set<Rid> = keys.mapNotNullTo(LinkedHashSet()) { byKey[it]?.handle }

    /** The keys open now: Raven's items as it shows them, and the keys of [composed] it has no items for. */
    fun expandedKeys(composed: Set<Any>): Set<Any> {
        val open = composed.filterTo(LinkedHashSet()) { it !in byKey }
        byKey.values.forEach { if (Rtree_item_is_expanded(it.handle) == R_TRUE) open += it.key }
        return open
    }

    fun sync(tree: Rid, data: TreeData<T>) {
        this.tree = tree
        val keys = HashSet<Any>()
        syncChildren(R_INVALID_ID, roots, data.roots, data, shown = true, keys)
        // The selection's handles were looked up as it composed, before the items new to the tree
        // were added: the selection is shown again now that they are.
        val wanted = data.selectedHandles(data.selection)
        val shown = (0 until Rtree_view_get_selection_count(tree)).mapTo(LinkedHashSet()) { Rtree_view_get_selection(tree, it) }
        if (wanted != shown) {
            Rtree_view_clear_selection(tree)
            wanted.forEach { Rtree_item_set_selected(it, R_TRUE) }
        }
    }

    /**
     * Matches [existing] to [values]. Children are asked of [TreeData.children] only for the items
     * [shown], so their arrows show; the children of an item in a closed branch stay as they were.
     */
    private fun syncChildren(parent: Rid, existing: MutableList<Item<T>>, values: List<T>, data: TreeData<T>, shown: Boolean, keys: HashSet<Any>) {
        val valueKeys = values.map { value -> data.key(value).also { require(keys.add(it)) { "the key $it is used by two items: keys must be unique" } } }
        var kept = 0
        while (kept < existing.size && kept < values.size && existing[kept].key == valueKeys[kept]) kept++
        // Raven only appends: from the first item out of place, the rest are destroyed.
        for (item in existing.subList(kept, existing.size)) forget(item)
        existing.subList(kept, existing.size).forEach { Rtree_item_destroy(it.handle) }
        existing.subList(kept, existing.size).clear()
        for (i in values.indices) {
            val item = if (i < kept) {
                existing[i].also { it.value = values[i] }
            } else {
                val text = data.itemText(values[i])
                Item(valueKeys[i], values[i], Rtree_view_add_item(tree, parent, text), text, null).also {
                    existing += it
                    byKey[it.key] = it
                    byHandle[it.handle] = it
                }
            }
            update(item, data)
            if (shown) syncChildren(item.handle, item.children, data.children(item.value), data, shown = item.key in data.expanded, keys)
        }
    }

    /** [target]'s branches opened through [onExpandedChange], then scrolled to; at once when it is shown. */
    fun reveal(target: Any, data: TreeData<T>, onExpandedChange: (Set<Any>) -> Unit) {
        val above = pathTo(target, data.roots, data) ?: return
        val closed = above.filter { it !in data.expanded }
        if (closed.isEmpty()) {
            byKey[target]?.let { Rtree_view_scroll_to_item(tree, it.handle) }
            return
        }
        pendingReveal = target
        onExpandedChange(data.expanded + closed)
    }

    /** After the expansion is shown: the item asked for, scrolled to; asked for once only. */
    fun revealPending() {
        val target = pendingReveal ?: return
        pendingReveal = null
        byKey[target]?.let { Rtree_view_scroll_to_item(tree, it.handle) }
    }

    /** The keys of the items above [target], outermost first, searching the data; null when it isn't there. */
    private fun pathTo(target: Any, values: List<T>, data: TreeData<T>): List<Any>? {
        for (value in values) {
            val key = data.key(value)
            if (key == target) return emptyList()
            pathTo(target, data.children(value), data)?.let { return listOf(key) + it }
        }
        return null
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
