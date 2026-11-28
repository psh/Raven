package raven.compose.foundation.lazy

import androidx.compose.runtime.Immutable

/**
 * An item of a lazy list composed and in view, as [LazyListLayoutInfo.visibleItemsInfo] gives it:
 * its [index], its [key] (its place when the DSL gave none), where it starts from the viewport's
 * start ([offset], negative when it is scrolled partly past) and its [size] along the axis, in dp.
 */
@Immutable
public data class LazyListItemInfo(val index: Int, val key: Any, val offset: Int, val size: Int)

/**
 * Where a lazy list's items are, as Compose's: the items in view, how many there are in all, and
 * the viewport, in dp along the list's axis. Read once a frame, once the list is laid out.
 */
@Immutable
public data class LazyListLayoutInfo(
    val visibleItemsInfo: List<LazyListItemInfo>,
    val totalItemsCount: Int,
    val viewportStartOffset: Int,
    val viewportEndOffset: Int,
) {
    internal companion object {
        val Empty: LazyListLayoutInfo = LazyListLayoutInfo(emptyList(), 0, 0, 0)
    }
}
