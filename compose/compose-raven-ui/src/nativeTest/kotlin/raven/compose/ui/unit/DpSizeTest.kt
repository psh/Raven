package raven.compose.ui.unit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DpSizeTest {
    @Test
    fun aSizeHasItsSidesAndComparesByThem() {
        val size = DpSize(420.dp, 220.5.dp)
        assertEquals(420.dp, size.width)
        assertEquals(220.5.dp, size.height)
        val (width, height) = size
        assertEquals(420.dp to 220.5.dp, width to height)
        assertEquals(DpSize(420.dp, 220.5.dp), size)
        assertNotEquals(DpSize(220.5.dp, 420.dp), size)
        assertEquals(DpSize(420.dp, 300.dp), size.copy(height = 300.dp))
        assertEquals("420.0.dp x 220.5.dp", size.toString())
    }

    @Test
    fun unspecifiedEqualsItselfAndNoSize() {
        assertEquals(DpSize.Unspecified, DpSize(Dp.Unspecified, Dp.Unspecified))
        assertTrue(DpSize.Unspecified.isUnspecified)
        assertFalse(DpSize.Unspecified.width.isSpecified)
        assertNotEquals(DpSize.Zero, DpSize.Unspecified)
        assertTrue(DpSize.Zero.isSpecified)
        assertTrue(DpSize(Dp.Unspecified, 10.dp).isSpecified, "only both sides unspecified are Unspecified, as in Compose")
        assertEquals("DpSize.Unspecified", DpSize.Unspecified.toString())
    }
}
