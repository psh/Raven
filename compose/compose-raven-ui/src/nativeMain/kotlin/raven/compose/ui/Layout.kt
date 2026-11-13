@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import raven.cinterop.R_HALIGN_CENTER
import raven.cinterop.R_HALIGN_FILL
import raven.cinterop.R_HALIGN_LEFT
import raven.cinterop.R_HALIGN_RIGHT
import raven.cinterop.R_VALIGN_BOTTOM
import raven.cinterop.R_VALIGN_CENTER
import raven.cinterop.R_VALIGN_FILL
import raven.cinterop.R_VALIGN_TOP
import raven.cinterop.Rid
import raven.cinterop.Rslot_set_auto
import raven.cinterop.Rslot_set_fill
import raven.cinterop.Rslot_set_grid
import raven.cinterop.Rslot_set_grid_span
import raven.cinterop.Rslot_set_halign
import raven.cinterop.Rslot_set_padding
import raven.cinterop.Rslot_set_valign
import raven.cinterop.Rwidget_set_max_size
import raven.cinterop.Rwidget_set_min_size
import raven.cinterop.Rwidget_set_size
import raven.compose.animation.TransitionRequester
import raven.compose.animation.TransitionRequesterElement
import raven.compose.ui.focus.FocusRequester
import raven.compose.ui.focus.FocusRequesterElement
import raven.compose.ui.focus.FocusableElement
import raven.compose.ui.graphics.Material
import raven.compose.ui.input.pointer.PointerHoverIconElement
import raven.compose.ui.input.pointer.PointerIcon
import raven.compose.ui.semantics.SemanticsElement
import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.isSpecified

/** The axis a panel lays its children along: a Row's, a Column's, or none for a Box. */
@InternalComposeRavenApi
public enum class LayoutAxis { Horizontal, Vertical, None }

/** How a panel sizes its children along its [LayoutAxis]. */
@InternalComposeRavenApi
public enum class MainAxis {
    /** A Row or Column: a weight, or `fillMax*`, shares out the space left. */
    Weighted,

    /** A scroll container: each child keeps its own size, so a weight means nothing. */
    Scrolling,

    /** A splitter: each child fills a pane, whose relative size is its weight, 1 by default. */
    Panes,

    /**
     * A header bar: as [Weighted], and a child aligned to the end is packed at the end, so a
     * child can be aligned to the start or the end, but not centred.
     */
    Packed,

    /**
     * A grid: each child is in a cell, which it fills unless its Modifier aligns it; a Grid's
     * child says which cell with `Modifier.cell`, and a uniform grid places its children itself.
     */
    Cells,
}

/**
 * How a panel places its children: its [axis], how it sizes them along it ([mainAxis]), and the
 * alignment each child gets on the axes it doesn't lay out along, unless the child's Modifier says
 * otherwise. Splitter panes fill across instead.
 */
@InternalComposeRavenApi
public data class PanelLayout(
    val axis: LayoutAxis,
    val horizontal: Alignment.Horizontal = Alignment.Start,
    val vertical: Alignment.Vertical = Alignment.Top,
    val mainAxis: MainAxis = MainAxis.Weighted,
) {
    public companion object {
        /** A Box with its content at the top start: the window root, and any other parent. */
        public val Box: PanelLayout = PanelLayout(LayoutAxis.None)
    }
}

/** A Modifier chain folded into flat values. For size and alignment, the last element wins. */
internal data class FoldedModifier(
    val paddingStart: Float = 0f,
    val paddingTop: Float = 0f,
    val paddingEnd: Float = 0f,
    val paddingBottom: Float = 0f,
    val width: Dp = Dp.Unspecified,
    val height: Dp = Dp.Unspecified,
    val minWidth: Dp = Dp.Unspecified,
    val minHeight: Dp = Dp.Unspecified,
    val maxWidth: Dp = Dp.Unspecified,
    val maxHeight: Dp = Dp.Unspecified,
    /** An alignment, or [HAlign.Fill] for `fillMaxWidth`. */
    val horizontal: HAlign? = null,
    /** An alignment, or [VAlign.Fill] for `fillMaxHeight`. */
    val vertical: VAlign? = null,
    val weight: Float? = null,
    /** The child's cell in a Grid; null when the chain gives none. */
    val gridCell: GridCellElement? = null,
    val testTag: String? = null,
    val tooltip: String? = null,
    /** The widget's own material; null is none, the theme's. */
    val material: Material? = null,
    /** How the widget appears when it is inserted; null is none. */
    val appearTransition: AppearTransitionElement? = null,
    /** The widget's opacity, 1 when the chain gives none. */
    val alpha: Float = 1f,
    /** Whether the widget clips its children; off when the chain gives none. */
    val clipToBounds: Boolean = false,
    /** The input elements, in chain order: the order they see each event in. */
    val inputs: List<InputElement> = emptyList(),
    /** The pointer icon over the widget; null leaves it to the widget. */
    val pointerIcon: PointerIcon? = null,
    /** Whether `focusable` lets the widget take the focus; null when the chain has none. */
    val focusable: Boolean? = null,
    /** The focus requesters that can focus the widget. */
    val focusRequesters: List<FocusRequester> = emptyList(),
    /** What the chain's `semantics` blocks set, together; null when it has none. */
    val semantics: SemanticsElement? = null,
    /** The transition requesters attached to the widget, in the chain's order. */
    val transitionRequesters: List<TransitionRequester> = emptyList(),
)

internal fun fold(modifier: Modifier): FoldedModifier = modifier.foldIn(FoldedModifier()) { folded, element ->
    when (element) {
        is PaddingElement -> folded.copy(
            paddingStart = folded.paddingStart + element.start.value,
            paddingTop = folded.paddingTop + element.top.value,
            paddingEnd = folded.paddingEnd + element.end.value,
            paddingBottom = folded.paddingBottom + element.bottom.value,
        )
        // Raven insets a child's area by its padding, negative too, so an offset moves the widget
        // by as much as it takes from the other side.
        is OffsetElement -> folded.copy(
            paddingStart = folded.paddingStart + element.x.value,
            paddingTop = folded.paddingTop + element.y.value,
            paddingEnd = folded.paddingEnd - element.x.value,
            paddingBottom = folded.paddingBottom - element.y.value,
        )
        is SizeElement -> folded.copy(
            width = element.width.orElse(folded.width),
            height = element.height.orElse(folded.height),
        )
        is SizeInElement -> folded.copy(
            minWidth = element.minWidth.orElse(folded.minWidth),
            minHeight = element.minHeight.orElse(folded.minHeight),
            maxWidth = element.maxWidth.orElse(folded.maxWidth),
            maxHeight = element.maxHeight.orElse(folded.maxHeight),
        )
        is FillElement -> folded.copy(
            horizontal = if (element.horizontal) HAlign.Fill else folded.horizontal,
            vertical = if (element.vertical) VAlign.Fill else folded.vertical,
        )
        is WeightElement -> folded.copy(weight = element.weight)
        is GridCellElement -> folded.copy(gridCell = element)
        is AlignElement -> folded.copy(
            horizontal = element.horizontal?.align ?: folded.horizontal,
            vertical = element.vertical?.align ?: folded.vertical,
        )
        is TestTagElement -> folded.copy(testTag = element.tag)
        is TooltipElement -> folded.copy(tooltip = element.text)
        is MaterialElement -> folded.copy(material = element.material)
        is AppearTransitionElement -> folded.copy(appearTransition = element)
        is AlphaElement -> folded.copy(alpha = element.alpha)
        is ClipToBoundsElement -> folded.copy(clipToBounds = true)
        is InputElement -> folded.copy(inputs = folded.inputs + element)
        is PointerHoverIconElement -> folded.copy(pointerIcon = element.icon)
        is FocusableElement -> folded.copy(focusable = element.enabled)
        is FocusRequesterElement -> folded.copy(focusRequesters = folded.focusRequesters + element.requester)
        is SemanticsElement -> folded.copy(semantics = folded.semantics?.then(element) ?: element)
        is ClipElement -> folded.copy(clipToBounds = true)
        is TransitionRequesterElement -> folded.copy(transitionRequesters = folded.transitionRequesters + element.requester)
        else -> folded
    }
}

private fun Dp.orElse(other: Dp): Dp = if (isSpecified) this else other

/**
 * What Raven is told about a child: its slot (padding, alignment, fill coefficient or `null` for an
 * auto slot) and its size constraints, with -1 for none.
 */
internal data class SlotLayout(
    val paddingLeft: Float = 0f,
    val paddingTop: Float = 0f,
    val paddingRight: Float = 0f,
    val paddingBottom: Float = 0f,
    val halign: HAlign = HAlign.Fill,
    val valign: VAlign = VAlign.Fill,
    val fill: Float? = null,
    val width: Float = -1f,
    val height: Float = -1f,
    val minWidth: Float = -1f,
    val minHeight: Float = -1f,
    val maxWidth: Float = -1f,
    val maxHeight: Float = -1f,
    val gridColumn: Int = 0,
    val gridRow: Int = 0,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
) {
    companion object {
        /** What Raven gives a new widget: fill alignment, an auto slot, no constraints. */
        val RavenDefault = SlotLayout()
    }
}

internal class ResolvedLayout(val slot: SlotLayout, val warnings: List<String>)

/**
 * Resolves [modifier] against the [parent] panel: Compose's start/top defaults, the panel's
 * alignment on the cross axis, and `fillMax*` on a Row's or Column's main axis as `weight(1f)`.
 * What the parent can't honour is ignored, with a warning.
 */
internal fun resolve(modifier: FoldedModifier, parent: PanelLayout): ResolvedLayout {
    val warnings = mutableListOf<String>()
    val panes = parent.mainAxis == MainAxis.Panes || parent.mainAxis == MainAxis.Cells
    if (modifier.gridCell != null && parent.mainAxis != MainAxis.Cells) warnings += "Modifier.cell works only in a Grid"
    var halign = modifier.horizontal ?: if (panes) HAlign.Fill else parent.horizontal.align
    var valign = modifier.vertical ?: if (panes) VAlign.Fill else parent.vertical.align
    var fill: Float? = null
    when (parent.axis) {
        LayoutAxis.Vertical -> {
            if (modifier.vertical != null && modifier.vertical != VAlign.Fill) {
                warnings += "Modifier.align(Alignment.Vertical) works only in a Row"
            }
            fill = mainAxisFill(modifier.weight, modifier.vertical == VAlign.Fill, parent.mainAxis, warnings)
            valign = if (fill != null) VAlign.Fill else VAlign.Top
        }
        LayoutAxis.Horizontal -> {
            val packed = parent.mainAxis == MainAxis.Packed
            if (packed && modifier.horizontal == HAlign.Center) {
                warnings += "Modifier.align(Alignment.CenterHorizontally) has no place in a header bar, which packs its children at the start or the end"
            } else if (!packed && modifier.horizontal != null && modifier.horizontal != HAlign.Fill) {
                warnings += "Modifier.align(Alignment.Horizontal) works only in a Column"
            }
            fill = mainAxisFill(modifier.weight, modifier.horizontal == HAlign.Fill, parent.mainAxis, warnings)
            halign = when {
                fill != null -> HAlign.Fill
                packed && modifier.horizontal == HAlign.End -> HAlign.End
                else -> HAlign.Start
            }
        }
        LayoutAxis.None -> if (modifier.weight != null) warnings += "Modifier.weight works only in a Row or Column"
    }
    val slot = SlotLayout(
        paddingLeft = modifier.paddingStart,
        paddingTop = modifier.paddingTop,
        paddingRight = modifier.paddingEnd,
        paddingBottom = modifier.paddingBottom,
        halign = halign,
        valign = valign,
        fill = fill,
        width = modifier.width.orNone(),
        height = modifier.height.orNone(),
        minWidth = modifier.minWidth.orNone(),
        minHeight = modifier.minHeight.orNone(),
        maxWidth = modifier.maxWidth.orNone(),
        maxHeight = modifier.maxHeight.orNone(),
        gridColumn = modifier.gridCell?.column ?: 0,
        gridRow = modifier.gridCell?.row ?: 0,
        columnSpan = modifier.gridCell?.columnSpan ?: 1,
        rowSpan = modifier.gridCell?.rowSpan ?: 1,
    )
    return ResolvedLayout(slot, warnings)
}

/** The fill coefficient of a child's slot along its parent's axis, or null for an auto slot. */
private fun mainAxisFill(weight: Float?, fillMax: Boolean, mainAxis: MainAxis, warnings: MutableList<String>): Float? =
    when (mainAxis) {
        MainAxis.Weighted, MainAxis.Packed -> weight ?: if (fillMax) 1f else null
        MainAxis.Scrolling -> {
            if (weight != null) warnings += "Modifier.weight has no space to share in a scroll container"
            null
        }
        MainAxis.Panes -> weight ?: 1f
        MainAxis.Cells -> null
    }

private fun Dp.orNone(): Float = if (isSpecified) value else -1f

/** The Raven setters a [SlotLayout] goes through. */
internal interface SlotTarget {
    fun padding(left: Float, top: Float, right: Float, bottom: Float)
    fun halign(align: HAlign)
    fun valign(align: VAlign)
    fun fill(coefficient: Float?)
    fun size(width: Float, height: Float)
    fun minSize(width: Float, height: Float)
    fun maxSize(width: Float, height: Float)
    fun grid(column: Int, row: Int, columnSpan: Int, rowSpan: Int)
}

/** Calls only the setters of [target] whose values differ from [previous]. */
internal fun SlotLayout.applyChanges(previous: SlotLayout, target: SlotTarget) {
    if (paddingLeft != previous.paddingLeft || paddingTop != previous.paddingTop ||
        paddingRight != previous.paddingRight || paddingBottom != previous.paddingBottom
    ) {
        target.padding(paddingLeft, paddingTop, paddingRight, paddingBottom)
    }
    if (halign != previous.halign) target.halign(halign)
    if (valign != previous.valign) target.valign(valign)
    if (fill != previous.fill) target.fill(fill)
    if (width != previous.width || height != previous.height) target.size(width, height)
    if (minWidth != previous.minWidth || minHeight != previous.minHeight) target.minSize(minWidth, minHeight)
    if (maxWidth != previous.maxWidth || maxHeight != previous.maxHeight) target.maxSize(maxWidth, maxHeight)
    if (gridColumn != previous.gridColumn || gridRow != previous.gridRow || columnSpan != previous.columnSpan || rowSpan != previous.rowSpan) {
        target.grid(gridColumn, gridRow, columnSpan, rowSpan)
    }
}

/** The slot and size setters of one Raven widget. */
internal class RavenSlotTarget(private val widget: Rid) : SlotTarget {
    override fun padding(left: Float, top: Float, right: Float, bottom: Float) = Rslot_set_padding(widget, left, top, right, bottom)

    override fun halign(align: HAlign) = Rslot_set_halign(
        widget,
        when (align) {
            HAlign.Start -> R_HALIGN_LEFT
            HAlign.Center -> R_HALIGN_CENTER
            HAlign.End -> R_HALIGN_RIGHT
            HAlign.Fill -> R_HALIGN_FILL
        },
    )

    override fun valign(align: VAlign) = Rslot_set_valign(
        widget,
        when (align) {
            VAlign.Top -> R_VALIGN_TOP
            VAlign.Center -> R_VALIGN_CENTER
            VAlign.Bottom -> R_VALIGN_BOTTOM
            VAlign.Fill -> R_VALIGN_FILL
        },
    )

    override fun fill(coefficient: Float?) = if (coefficient == null) Rslot_set_auto(widget) else Rslot_set_fill(widget, coefficient)

    override fun size(width: Float, height: Float) = Rwidget_set_size(widget, width, height)

    override fun minSize(width: Float, height: Float) = Rwidget_set_min_size(widget, width, height)

    override fun maxSize(width: Float, height: Float) = Rwidget_set_max_size(widget, width, height)

    override fun grid(column: Int, row: Int, columnSpan: Int, rowSpan: Int) {
        Rslot_set_grid(widget, column, row)
        Rslot_set_grid_span(widget, columnSpan, rowSpan)
    }
}

/**
 * Where layout and window warnings go. Layout warnings are given once per process ([warnOnce]);
 * a window's or a `HeaderBar`'s, each time.
 */
@InternalComposeRavenApi
public var layoutWarningSink: (String) -> Unit = { println("compose-raven: warning: $it") }

private val warningsGiven = mutableSetOf<String>()

internal fun warnOnce(warning: String) {
    if (warningsGiven.add(warning)) layoutWarningSink(warning)
}
