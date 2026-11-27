package raven.compose.foundation

import androidx.compose.runtime.Composable
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_BREADCRUMB
import raven.cinterop.R_WIDGET_PAGINATION
import raven.cinterop.R_WIDGET_RATING
import raven.cinterop.Rbreadcrumb_add_item
import raven.cinterop.Rbreadcrumb_truncate
import raven.cinterop.Rpagination_set_page_count
import raven.cinterop.Rpagination_set_siblings
import raven.cinterop.Rrating_set_allow_half
import raven.cinterop.Rrating_set_max
import raven.cinterop.Rrating_set_read_only
import raven.cinterop.Rrating_set_star_size
import raven.compose.ui.BreadcrumbClicked
import raven.compose.ui.Modifier
import raven.compose.ui.PaginationChanged
import raven.compose.ui.PaginationPage
import raven.compose.ui.Property
import raven.compose.ui.RatingChanged
import raven.compose.ui.RatingValue
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.dp

// Rating, Pagination and Breadcrumb: small controlled widgets Raven draws itself (the stars, the
// chevrons and the arrows), so they take no icons.

private val RatingWidget = WidgetDescriptor("Rating", R_WIDGET_RATING, events = listOf(RatingChanged))
private val PaginationWidget = WidgetDescriptor("Pagination", R_WIDGET_PAGINATION, events = listOf(PaginationChanged))
private val BreadcrumbWidget = WidgetDescriptor("Breadcrumb", R_WIDGET_BREADCRUMB, events = listOf(BreadcrumbClicked))

private val RatingMax = Property<Int> { widget, max -> Rrating_set_max(widget, max) }
private val AllowHalf = Property<Boolean> { widget, allow -> Rrating_set_allow_half(widget, if (allow) R_TRUE else R_FALSE) }
private val RatingReadOnly = Property<Boolean> { widget, readOnly -> Rrating_set_read_only(widget, if (readOnly) R_TRUE else R_FALSE) }
private val StarSize = Property<Dp> { widget, size -> Rrating_set_star_size(widget, size.value) }
private val PageCount = Property<Int> { widget, count -> Rpagination_set_page_count(widget, count) }
private val Siblings = Property<Int> { widget, siblings -> Rpagination_set_siblings(widget, siblings) }
private val BreadcrumbItems = Property<List<String>> { widget, items ->
    Rbreadcrumb_truncate(widget, 0)
    // One by one, so a text may hold '|', which Rbreadcrumb_set_items splits on.
    for (item in items) Rbreadcrumb_add_item(widget, item)
}

/**
 * Raven's row of [max] stars showing [value], from 0 to [max], controlled as [Slider] is: a click,
 * the wheel or the arrow keys call [onValueChange]. With [allowHalf] a half star can be chosen.
 * [readOnly] shows the score without letting the user change it, as [TextField]'s does.
 */
@Composable
public fun Rating(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    max: Int = 5,
    allowHalf: Boolean = false,
    starSize: Dp = 18.dp,
) {
    require(max in 1..20) { "a Rating has 1 to 20 stars, not $max" }
    RavenWidget(RatingWidget, modifier, {
        bind(enabled, Enabled)
        bind(readOnly, RatingReadOnly)
        bind(starSize, StarSize)
        // The stars and halves before the value: Raven snaps the value to them.
        bind(max, RatingMax)
        bind(allowHalf, AllowHalf)
        bind(value.coerceIn(0f, max.toFloat()), RatingValue)
        bind(onValueChange, RatingChanged)
    })
}

/**
 * Raven's page numbers (`< 1 ... 4 5 6 ... 20 >`) showing [page] of [pageCount], controlled as
 * [Slider] is: the user's choice calls [onPageChange]. Pages count from 0, as
 * `PagerState.currentPage` does, and are shown from 1. [siblings] is how many numbers are shown
 * on each side of the current one.
 */
@Composable
public fun Pagination(
    page: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    siblings: Int = 1,
) {
    require(pageCount >= 1) { "a Pagination has at least one page, not $pageCount" }
    require(siblings >= 0) { "siblings must not be negative" }
    RavenWidget(PaginationWidget, modifier, {
        bind(enabled, Enabled)
        bind(siblings, Siblings)
        // The count before the page: Raven keeps the page within it.
        bind(pageCount, PageCount)
        bind(page.coerceIn(0, pageCount - 1), PaginationPage)
        bind(onPageChange, PaginationChanged)
    })
}

/**
 * Raven's trail of [items] (`Home > Documents > Report`): those before the last are links, and a
 * click on one calls [onItemClick] with its index. The app owns the list, and shortens it when an
 * earlier item is clicked. When the items don't fit, the first ones give their place to "...".
 */
@Composable
public fun Breadcrumb(
    items: List<String>,
    onItemClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    RavenWidget(BreadcrumbWidget, modifier, {
        bind(items, BreadcrumbItems)
        bind(onItemClick, BreadcrumbClicked)
    })
}
