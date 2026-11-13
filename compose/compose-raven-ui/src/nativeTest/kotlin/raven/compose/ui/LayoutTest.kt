package raven.compose.ui

import raven.compose.ui.graphics.Material
import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Folding, resolving and diffing, with no Raven. */
class LayoutTest {
    private val column = PanelLayout(LayoutAxis.Vertical, Alignment.CenterHorizontally)
    private val row = PanelLayout(LayoutAxis.Horizontal, vertical = Alignment.Bottom)
    private val box = PanelLayout(LayoutAxis.None, Alignment.End, Alignment.CenterVertically)
    private val verticalScroll = PanelLayout(LayoutAxis.Vertical, Alignment.CenterHorizontally, mainAxis = MainAxis.Scrolling)
    private val horizontalSplitter = PanelLayout(LayoutAxis.Horizontal, mainAxis = MainAxis.Panes)
    private val headerBar = PanelLayout(LayoutAxis.Horizontal, vertical = Alignment.CenterVertically, mainAxis = MainAxis.Packed)
    private val grid = PanelLayout(LayoutAxis.None, mainAxis = MainAxis.Cells)

    private fun padding(all: Dp) = PaddingElement(all, all, all, all)
    private val fillMaxWidth = FillElement(horizontal = true, vertical = false)
    private val fillMaxHeight = FillElement(horizontal = false, vertical = true)

    @Test
    fun equalChainsAreEqual() {
        assertEquals(Modifier then padding(4.dp) then fillMaxWidth, Modifier then padding(4.dp) then fillMaxWidth)
        assertNotEquals<Modifier>(Modifier then padding(4.dp) then fillMaxWidth, Modifier then fillMaxWidth then padding(4.dp))
        assertEquals<Modifier>(Modifier, Modifier then Modifier)
    }

    @Test
    fun paddingsAddUp() {
        val folded = fold(Modifier then padding(4.dp) then PaddingElement(1.dp, 2.dp, 3.dp, 4.dp))
        assertEquals(listOf(5f, 6f, 7f, 8f), listOf(folded.paddingStart, folded.paddingTop, folded.paddingEnd, folded.paddingBottom))
    }

    @Test
    fun anOffsetMovesThePaddingFromOneSideToTheOther() {
        val folded = fold(Modifier then padding(4.dp) then OffsetElement(3.dp, (-2).dp) then OffsetElement(1.dp, 0.dp))
        assertEquals(listOf(8f, 2f, 0f, 6f), listOf(folded.paddingStart, folded.paddingTop, folded.paddingEnd, folded.paddingBottom))
        val slot = resolve(folded, column).slot
        assertEquals(listOf(8f, 2f, 0f, 6f), listOf(slot.paddingLeft, slot.paddingTop, slot.paddingRight, slot.paddingBottom))
    }

    @Test
    fun theLastSizeWinsOnEachAxis() {
        val folded = fold(
            Modifier then SizeElement(10.dp, 20.dp) then SizeElement(30.dp, Dp.Unspecified) then
                SizeInElement(1.dp, Dp.Unspecified, Dp.Unspecified, 50.dp) then SizeInElement(Dp.Unspecified, 2.dp, Dp.Unspecified, Dp.Unspecified),
        )
        assertEquals(30.dp, folded.width)
        assertEquals(20.dp, folded.height)
        assertEquals(listOf(1.dp, 2.dp, Dp.Unspecified, 50.dp), listOf(folded.minWidth, folded.minHeight, folded.maxWidth, folded.maxHeight))
    }

    @Test
    fun theLastAlignmentOrFillWins() {
        assertEquals(HAlign.Center, fold(Modifier then fillMaxWidth then AlignElement(Alignment.CenterHorizontally, null)).horizontal)
        assertEquals(HAlign.Fill, fold(Modifier then AlignElement(Alignment.CenterHorizontally, null) then fillMaxWidth).horizontal)
    }

    @Test
    fun theLastMaterialWinsAndLeavesTheSlotAsItIs() {
        val folded = fold(Modifier.material(Material.Glass).material(Material.FrostedGlass))
        assertEquals(Material.FrostedGlass, folded.material)
        assertNull(fold(Modifier).material)
        assertEquals(resolve(fold(Modifier), column).slot, resolve(folded, column).slot)
    }

    @Test
    fun aColumnChildTakesTheColumnsAlignmentAndAnAutoSlot() {
        val slot = resolve(fold(Modifier), column).slot
        assertEquals(HAlign.Center, slot.halign)
        assertEquals(VAlign.Top, slot.valign)
        assertNull(slot.fill)
    }

    @Test
    fun fillMaxOnAColumnsMainAxisIsAWeightOfOne() {
        val slot = resolve(fold(Modifier then fillMaxHeight), column).slot
        assertEquals(1f, slot.fill)
        assertEquals(VAlign.Fill, slot.valign)
        assertEquals(HAlign.Fill, resolve(fold(Modifier then fillMaxWidth), column).slot.halign)
    }

    @Test
    fun aWeightOutweighsFillMax() {
        assertEquals(2f, resolve(fold(Modifier then fillMaxHeight then WeightElement(2f)), column).slot.fill)
        assertEquals(2f, resolve(fold(Modifier then WeightElement(2f)), row).slot.fill)
    }

    @Test
    fun aRowMirrorsAColumn() {
        val slot = resolve(fold(Modifier), row).slot
        assertEquals(HAlign.Start, slot.halign)
        assertEquals(VAlign.Bottom, slot.valign)
        val filled = resolve(fold(Modifier then fillMaxWidth then AlignElement(null, Alignment.Top)), row).slot
        assertEquals(1f, filled.fill)
        assertEquals(HAlign.Fill, filled.halign)
        assertEquals(VAlign.Top, filled.valign)
    }

    @Test
    fun aBoxChildTakesTheBoxsAlignmentUnlessItAlignsItself() {
        assertEquals(HAlign.End to VAlign.Center, resolve(fold(Modifier), box).slot.let { it.halign to it.valign })
        val aligned = resolve(fold(Modifier then AlignElement(Alignment.Start, Alignment.Bottom)), box).slot
        assertEquals(HAlign.Start to VAlign.Bottom, aligned.halign to aligned.valign)
        val filled = resolve(fold(Modifier then FillElement(horizontal = true, vertical = true)), box).slot
        assertEquals(HAlign.Fill to VAlign.Fill, filled.halign to filled.valign)
        assertNull(filled.fill)
    }

    @Test
    fun whatTheParentCantHonourIsIgnoredWithAWarning() {
        val inBox = resolve(fold(Modifier then WeightElement(1f)), box)
        assertNull(inBox.slot.fill)
        assertEquals(listOf("Modifier.weight works only in a Row or Column"), inBox.warnings)

        val inColumn = resolve(fold(Modifier then AlignElement(null, Alignment.Bottom)), column)
        assertEquals(VAlign.Top, inColumn.slot.valign)
        assertEquals(1, inColumn.warnings.size)

        val inRow = resolve(fold(Modifier then AlignElement(Alignment.End, null)), row)
        assertEquals(HAlign.Start, inRow.slot.halign)
        assertEquals(1, inRow.warnings.size)

        assertTrue(resolve(fold(Modifier then fillMaxHeight then WeightElement(1f)), column).warnings.isEmpty())
    }

    @Test
    fun aScrollChildKeepsItsSizeAlongTheAxis() {
        val slot = resolve(fold(Modifier then fillMaxHeight), verticalScroll).slot
        assertNull(slot.fill)
        assertEquals(VAlign.Top, slot.valign)
        assertEquals(HAlign.Center, slot.halign)

        val weighted = resolve(fold(Modifier then WeightElement(1f)), verticalScroll)
        assertNull(weighted.slot.fill)
        assertEquals(listOf("Modifier.weight has no space to share in a scroll container"), weighted.warnings)
    }

    @Test
    fun aSplitterPaneFillsItsPaneWithAWeightOfOneByDefault() {
        val slot = resolve(fold(Modifier), horizontalSplitter).slot
        assertEquals(1f, slot.fill)
        assertEquals(HAlign.Fill to VAlign.Fill, slot.halign to slot.valign)
        assertEquals(3f, resolve(fold(Modifier then WeightElement(3f)), horizontalSplitter).slot.fill)
        val inVertical = resolve(fold(Modifier then WeightElement(2f)), horizontalSplitter.copy(axis = LayoutAxis.Vertical)).slot
        assertEquals(2f, inVertical.fill)
        assertEquals(HAlign.Fill to VAlign.Fill, inVertical.halign to inVertical.valign)
    }

    @Test
    fun aHeaderBarChildIsPackedAtTheStartOrTheEnd() {
        val slot = resolve(fold(Modifier), headerBar).slot
        assertEquals(HAlign.Start to VAlign.Center, slot.halign to slot.valign)
        assertNull(slot.fill)
        val atEnd = resolve(fold(Modifier then AlignElement(Alignment.End, null)), headerBar)
        assertEquals(HAlign.End, atEnd.slot.halign)
        assertTrue(atEnd.warnings.isEmpty())
        assertEquals(1f, resolve(fold(Modifier then fillMaxWidth), headerBar).slot.fill)

        val centred = resolve(fold(Modifier then AlignElement(Alignment.CenterHorizontally, null)), headerBar)
        assertEquals(HAlign.Start, centred.slot.halign)
        assertEquals(1, centred.warnings.size)
    }

    @Test
    fun aGridChildFillsTheCellItIsGiven() {
        val slot = resolve(fold(Modifier then GridCellElement(2, 1, 3, 1)), grid)
        assertEquals(HAlign.Fill to VAlign.Fill, slot.slot.halign to slot.slot.valign)
        assertNull(slot.slot.fill)
        assertTrue(slot.warnings.isEmpty())
        assertEquals(listOf("grid 2 1 3 1"), calls(slot.slot.copy(halign = HAlign.Fill, valign = VAlign.Fill), SlotLayout.RavenDefault))
        assertEquals(VAlign.Center, resolve(fold(Modifier then AlignElement(null, Alignment.CenterVertically)), grid).slot.valign)

        val outside = resolve(fold(Modifier then GridCellElement(1, 0, 1, 1)), column)
        assertEquals(listOf("Modifier.cell works only in a Grid"), outside.warnings)
    }

    @Test
    fun onlyChangedSettersAreCalled() {
        val first = resolve(fold(Modifier), column).slot
        assertEquals(listOf("halign Center", "valign Top"), calls(first, SlotLayout.RavenDefault))
        assertEquals(emptyList(), calls(first, first))

        val padded = resolve(fold(Modifier then padding(4.dp)), column).slot
        assertEquals(listOf("padding 4.0 4.0 4.0 4.0"), calls(padded, first))

        val sized = resolve(fold(Modifier then padding(4.dp) then SizeElement(10.dp, Dp.Unspecified)), column).slot
        assertEquals(listOf("size 10.0 -1.0"), calls(sized, padded))
        assertEquals(listOf("size -1.0 -1.0"), calls(padded, sized))

        val weighted = resolve(fold(Modifier then padding(4.dp) then WeightElement(1f)), column).slot
        assertEquals(listOf("valign Fill", "fill 1.0"), calls(weighted, padded))
        assertEquals(listOf("valign Top", "fill auto"), calls(padded, weighted))
    }

    private fun calls(slot: SlotLayout, previous: SlotLayout): List<String> {
        val calls = mutableListOf<String>()
        slot.applyChanges(
            previous,
            object : SlotTarget {
                override fun padding(left: Float, top: Float, right: Float, bottom: Float) {
                    calls += "padding $left $top $right $bottom"
                }
                override fun halign(align: HAlign) {
                    calls += "halign $align"
                }
                override fun valign(align: VAlign) {
                    calls += "valign $align"
                }
                override fun fill(coefficient: Float?) {
                    calls += "fill ${coefficient ?: "auto"}"
                }
                override fun size(width: Float, height: Float) {
                    calls += "size $width $height"
                }
                override fun minSize(width: Float, height: Float) {
                    calls += "minSize $width $height"
                }
                override fun maxSize(width: Float, height: Float) {
                    calls += "maxSize $width $height"
                }
                override fun grid(column: Int, row: Int, columnSpan: Int, rowSpan: Int) {
                    calls += "grid $column $row $columnSpan $rowSpan"
                }
            },
        )
        return calls
    }
}
