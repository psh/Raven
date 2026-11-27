package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.R_FALSE
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_TRUE
import raven.cinterop.Rbreadcrumb_get_item_count
import raven.cinterop.Rbreadcrumb_get_item_text
import raven.cinterop.Rid
import raven.cinterop.Rpagination_get_page
import raven.cinterop.Rpagination_get_page_count
import raven.cinterop.Rrating_get_max
import raven.cinterop.Rrating_get_value
import raven.cinterop.Rslider_get_value
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwidget_is_enabled
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.height
import raven.compose.ui.Modifier
import raven.compose.ui.input.key.Key
import raven.compose.ui.input.key.KeyShortcut
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.test.widgetRect
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NavigationWidgetsTest {
    private val left = KeyShortcut(Key.DirectionLeft)
    private val right = KeyShortcut(Key.DirectionRight)
    private val up = KeyShortcut(Key.DirectionUp)
    private val enter = KeyShortcut(Key.Enter)

    /** A click at a fraction of the widget's width and height, from its top left. */
    private fun clickAt(widget: Rid, fx: Float, fy: Float) {
        val rect = widgetRect(widget)
        val window = Rwidget_get_window(widget)
        Rwindow_inject_mouse_move(window, rect.x + rect.width * fx, rect.y + rect.height * fy)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE)
    }

    private fun itemsOf(breadcrumb: Rid) =
        List(Rbreadcrumb_get_item_count(breadcrumb)) { Rbreadcrumb_get_item_text(breadcrumb, it).toRavenString() }

    // ---- Rating ----

    @Test
    fun aRatingShowsTheAppsValueAndReportsTheUsers() = runRavenTest {
        var value by mutableFloatStateOf(2f)
        setContent { Rating(value, { value = it }, Modifier.testTag("rating"), max = 10) }
        val rating = onNodeWithTag("rating").fetchWidget()
        assertEquals(2f, Rrating_get_value(rating))
        assertEquals(10, Rrating_get_max(rating))
        // A click on the last star.
        clickAt(rating, 0.97f, 0.5f)
        waitForIdle()
        assertEquals(10f, value)
        assertEquals(10f, Rrating_get_value(onNodeWithTag("rating").fetchWidget()))
    }

    @Test
    fun theAppsRatingWins() = runRavenTest {
        val reported = mutableListOf<Float>()
        setContent { Rating(3f, { reported += it }, Modifier.testTag("rating")) }
        onNodeWithTag("rating").performKeyPress(right)
        waitForIdle()
        assertEquals(listOf(4f), reported)
        assertEquals(3f, Rrating_get_value(onNodeWithTag("rating").fetchWidget()))
    }

    @Test
    fun halfStarsWhenAllowed() = runRavenTest {
        var value by mutableFloatStateOf(3f)
        setContent { Rating(value, { value = it }, Modifier.testTag("rating"), allowHalf = true) }
        onNodeWithTag("rating").performKeyPress(right)
        waitForIdle()
        assertEquals(3.5f, value)
    }

    @Test
    fun aReadOnlyRatingRefusesAChange() = runRavenTest {
        val reported = mutableListOf<Float>()
        setContent { Rating(3f, { reported += it }, Modifier.testTag("rating"), readOnly = true) }
        val rating = onNodeWithTag("rating")
        rating.performKeyPress(right)
        clickAt(rating.fetchWidget(), 0.97f, 0.5f)
        waitForIdle()
        assertEquals(emptyList(), reported)
        assertEquals(3f, Rrating_get_value(rating.fetchWidget()))
    }

    @Test
    fun aRatingOfTooManyStarsThrows() = runRavenTest {
        assertFailsWith<IllegalArgumentException> { setContent { Rating(1f, {}, max = 21) } }
    }

    // ---- Pagination ----

    @Test
    fun aPaginationShowsTheAppsPageAndReportsTheUsers() = runRavenTest {
        var page by mutableIntStateOf(4)
        var count by mutableIntStateOf(20)
        setContent { Pagination(page, count, { page = it }, Modifier.testTag("pages")) }
        val pages = onNodeWithTag("pages").fetchWidget()
        assertEquals(4, Rpagination_get_page(pages))
        assertEquals(20, Rpagination_get_page_count(pages))
        onNodeWithTag("pages").performKeyPress(right)
        waitForIdle()
        assertEquals(5, page)
        assertEquals(5, Rpagination_get_page(onNodeWithTag("pages").fetchWidget()))
        // Fewer pages: the page is kept within them.
        count = 3
        waitForIdle()
        assertEquals(2, Rpagination_get_page(onNodeWithTag("pages").fetchWidget()))
    }

    @Test
    fun theAppsPageWins() = runRavenTest {
        val reported = mutableListOf<Int>()
        setContent { Pagination(0, 5, { reported += it }, Modifier.testTag("pages")) }
        onNodeWithTag("pages").performKeyPress(right)
        waitForIdle()
        assertEquals(listOf(1), reported)
        assertEquals(0, Rpagination_get_page(onNodeWithTag("pages").fetchWidget()))
    }

    // ---- Breadcrumb ----

    @Test
    fun aBreadcrumbsItemsReachRavenOneByOne() = runRavenTest {
        var items by mutableStateOf(listOf("Home", "A|B", "Report"))
        setContent { Breadcrumb(items, {}, Modifier.testTag("trail")) }
        assertEquals(listOf("Home", "A|B", "Report"), itemsOf(onNodeWithTag("trail").fetchWidget()))
        items = listOf("Home")
        waitForIdle()
        assertEquals(listOf("Home"), itemsOf(onNodeWithTag("trail").fetchWidget()))
    }

    @Test
    fun aClickedItemIsReportedAndTheAppShortensTheTrail() = runRavenTest {
        var items by mutableStateOf(listOf("Home", "Documents", "Report"))
        val clicked = mutableListOf<Int>()
        setContent { Breadcrumb(items, { clicked += it; items = items.take(it + 1) }, Modifier.testTag("trail")) }
        val trail = onNodeWithTag("trail")
        // The keyboard starts on the last link, Documents; Left goes to Home.
        trail.performKeyPress(left)
        trail.performKeyPress(enter)
        waitForIdle()
        assertEquals(listOf(0), clicked)
        assertEquals(listOf("Home"), itemsOf(onNodeWithTag("trail").fetchWidget()))
    }

    // ---- VerticalSlider ----

    @Test
    fun aVerticalSlidersMinimumIsAtTheBottom() = runRavenTest {
        var value by mutableFloatStateOf(0.5f)
        setContent { VerticalSlider(value, { value = it }, Modifier.height(200.dp).testTag("slider")) }
        val slider = onNodeWithTag("slider").fetchWidget()
        val rect = widgetRect(slider)
        assertTrue(rect.height > rect.width, "upright: $rect")
        clickAt(slider, 0.5f, 0.98f)
        waitForIdle()
        assertTrue(value < 0.1f, "a click at the bottom gives the minimum, not $value")
        clickAt(onNodeWithTag("slider").fetchWidget(), 0.5f, 0.02f)
        waitForIdle()
        assertTrue(value > 0.9f, "a click at the top gives the maximum, not $value")
    }

    @Test
    fun theAppsVerticalSliderValueWins() = runRavenTest {
        val reported = mutableListOf<Float>()
        setContent { VerticalSlider(5f, { reported += it }, Modifier.testTag("slider"), valueRange = 0f..10f, steps = 9) }
        onNodeWithTag("slider").performKeyPress(up)
        waitForIdle()
        assertEquals(listOf(6f), reported)
        assertEquals(5f, Rslider_get_value(onNodeWithTag("slider").fetchWidget()))
    }

    @Test
    fun theyCanBeDisabled() = runRavenTest {
        var enabled by mutableStateOf(false)
        setContent {
            Column {
                Rating(1f, {}, Modifier.testTag("rating"), enabled = enabled)
                Pagination(0, 3, {}, Modifier.testTag("pages"), enabled = enabled)
                VerticalSlider(0f, {}, Modifier.testTag("slider"), enabled = enabled)
            }
        }
        for (tag in listOf("rating", "pages", "slider")) assertEquals(R_FALSE, Rwidget_is_enabled(onNodeWithTag(tag).fetchWidget()), tag)
        enabled = true
        waitForIdle()
        for (tag in listOf("rating", "pages", "slider")) assertEquals(R_TRUE, Rwidget_is_enabled(onNodeWithTag(tag).fetchWidget()), tag)
    }
}
