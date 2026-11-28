package raven.compose.foundation.lazy

import raven.compose.foundation.lazy.LazySlot.Item
import raven.compose.foundation.lazy.LazySlot.Space
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The lazy list window, with plain numbers: keys are the indices, sizes in dp. */
class LazyListWindowTest {
    private fun sizes(vararg measured: Pair<Int, Float>) = LazySizes().apply { measured.forEach { (key, size) -> put(key, size) } }

    private fun window(
        count: Int,
        sizes: LazySizes = LazySizes(),
        anchor: LazyAnchor = LazyAnchor(0, 0f),
        viewport: Float = 100f,
        gap: Float = 0f,
        pinned: Int? = null,
    ) = lazyListWindow(count, { it }, sizes, gap, anchor, viewport, pinned)

    @Test
    fun unmeasuredItemsCountAs48UntilAnyIsMeasuredThenAsTheAverage() {
        val sizes = LazySizes()
        assertEquals(48f, sizes.estimate)
        sizes.put(0, 20f)
        sizes.put(1, 40f)
        assertEquals(30f, sizes.estimate)
        assertEquals(20f, sizes.sizeOf(0))
        assertEquals(30f, sizes.sizeOf(7))
        assertEquals(false, sizes.put(0, 20f), "the same size is no change")
        sizes.put(0, 60f)
        assertEquals(50f, sizes.estimate, "a size measured again replaces the one before")
        assertNull(sizes.measured(7))
    }

    @Test
    fun theFirstWindowIsFilledByTheEstimate() {
        // 100 dp at 48 dp an item: items 0 to 2 are in view, and 3 is one past the end.
        val window = window(10_000)
        assertEquals(listOf(Item(0), Item(1), Item(2), Item(3), Space(4, 9_999, 9_996 * 48f)), window.slots)
        assertEquals(0f, window.offset)
        assertEquals(listOf(0, 1, 2), window.visibleItems.map { it.index })
        assertEquals(10_000 * 48f, window.totalSize)
    }

    @Test
    fun theItemsInViewAndOnePastEachEdgeAreComposedBetweenTwoSpacers() {
        val window = window(100, sizes(*(0 until 100).map { it to 20f }.toTypedArray()), LazyAnchor(10, 5f))
        // The viewport is 205 dp to 305 dp: items 10 to 15, and 9 and 16 beyond them.
        assertEquals(Space(0, 8, 9 * 20f), window.slots.first())
        assertEquals((9..16).map(::Item), window.slots.subList(1, 9))
        assertEquals(Space(17, 99, 83 * 20f), window.slots.last())
        assertEquals(205f, window.offset)
        assertEquals(10, window.firstVisibleIndex)
        assertEquals(5f, window.firstVisibleOffset)
        assertEquals(LazyWindowItem(10, 10, -5f, 20f), window.visibleItems.first())
        assertEquals(LazyWindowItem(15, 15, 95f, 20f), window.visibleItems.last())
    }

    @Test
    fun gapsCountBetweenItemsAndSpacersTakeTheGapsWithin() {
        val window = window(50, sizes(*(0 until 50).map { it to 20f }.toTypedArray()), LazyAnchor(10, 0f), gap = 5f)
        // Item 10 starts at 10 × 25 dp; the Spacer for 0 to 8 ends where 8 does, and the box adds the gap after it.
        assertEquals(250f, window.offset)
        assertEquals(Space(0, 8, 9 * 20f + 8 * 5f), window.slots.first())
        assertEquals(50 * 20f + 49 * 5f, window.totalSize)
    }

    @Test
    fun theOffsetIsClampedToTheContentAndTheAnchorMovesWithIt() {
        val window = window(20, sizes(*(0 until 20).map { it to 20f }.toTypedArray()), LazyAnchor(19, 0f))
        // 400 dp of content in 100: the offset stops at 300, where item 15 starts.
        assertEquals(300f, window.offset)
        assertEquals(LazyAnchor(15, 0f), window.anchor)
        assertEquals((14..19).map(::Item), window.slots.drop(1))
        assertEquals(Space(0, 13, 14 * 20f), window.slots.first())
    }

    @Test
    fun shortContentIsComposedWhole() {
        val window = window(3, sizes(0 to 20f, 1 to 20f, 2 to 20f), LazyAnchor(2, 10f))
        assertEquals(listOf(Item(0), Item(1), Item(2)), window.slots)
        assertEquals(0f, window.offset)
        assertEquals(LazyAnchor(0, 0f), window.anchor)
    }

    @Test
    fun anAnchorPastTheItemsIsClamped() {
        assertEquals(LazyAnchor(4, 0f), window(5, anchor = LazyAnchor(40, 0f), viewport = 0f).anchor)
        assertEquals(LazyWindow.Empty.copy(viewport = 100f), window(0, anchor = LazyAnchor(4, 0f)))
    }

    @Test
    fun thePinnedItemSplitsTheSpacerItIsIn() {
        val sizes = sizes(*(0 until 100).map { it to 20f }.toTypedArray())
        val above = window(100, sizes, LazyAnchor(50, 0f), pinned = 10)
        assertEquals(listOf(Space(0, 9, 200f), Item(10), Space(11, 48, 38 * 20f)), above.slots.take(3))
        val below = window(100, sizes, LazyAnchor(0, 0f), pinned = 99)
        assertEquals(listOf(Space(6, 98, 93 * 20f), Item(99)), below.slots.takeLast(2))
        val first = window(100, sizes, LazyAnchor(50, 0f), pinned = 0)
        assertEquals(listOf(Item(0), Space(1, 48, 48 * 20f)), first.slots.take(2))
        val within = window(100, sizes, LazyAnchor(50, 0f), pinned = 51)
        assertEquals((49..55).map(::Item), within.slots.subList(1, 8), "a pinned item in view changes nothing")
    }

    @Test
    fun aMeasuredSizeAboveMovesThePlacesBelowButNotTheAnchor() {
        val sizes = sizes(*(0 until 100).map { it to 20f }.toTypedArray())
        val before = window(100, sizes, LazyAnchor(30, 4f))
        sizes.put(29, 60f)
        val after = window(100, sizes, LazyAnchor(30, 4f))
        assertEquals(before.offset + 40f, after.offset, "the anchor's place moved down 40 dp")
        assertEquals(before.firstVisibleIndex to before.firstVisibleOffset, after.firstVisibleIndex to after.firstVisibleOffset)
    }

    @Test
    fun placesFindTheItemAtAPosition() {
        val places = LazyPlaces(4, 10f) { listOf(20f, 30f, 40f, 50f)[it] }
        assertEquals(listOf(0f, 30f, 70f, 120f), (0 until 4).map(places::start))
        assertEquals(170f, places.total)
        assertEquals(0, places.itemAt(0f))
        assertEquals(0, places.itemAt(19f))
        assertEquals(1, places.itemAt(25f), "in the gap after item 0: the item after it")
        assertEquals(LazyAnchor(1, -5f), places.anchorAt(25f))
        assertEquals(3, places.itemAt(500f))
        assertEquals(Space(1, 2, 80f), places.space(1, 2))
    }

    @Test
    fun spacersAreInWholeDp() {
        // An estimate of 25.5 dp: the Spacer for items 5 to 99 is 2,422.5 dp, made 2,423.
        val window = window(100, sizes(0 to 20f, 1 to 31f))
        assertEquals(Space(5, 99, 2_423f), window.slots.last())
        assertEquals(Space(1, 2, 25f), LazyPlaces(3, 0f) { 12.4f }.space(1, 2))
    }

    @Test
    fun sizesOfKeysGoneAreForgottenOnceTheyAreMany() {
        val sizes = sizes(*(0 until 200).map { it to 10f }.toTypedArray())
        sizes.retainOnly(setOf(0, 1, 2))
        assertEquals(3, sizes.measuredCount)
        assertEquals(10f, sizes.estimate)
        val few = sizes(0 to 10f, 1 to 30f)
        few.retainOnly(setOf(0))
        assertEquals(2, few.measuredCount, "a few aren't worth the sweep")
    }
}
