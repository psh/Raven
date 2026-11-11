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
import raven.cinterop.Rslot_set_halign
import raven.cinterop.Rslot_set_padding
import raven.cinterop.Rslot_set_valign
import raven.cinterop.Rwidget_set_max_size
import raven.cinterop.Rwidget_set_min_size
import raven.cinterop.Rwidget_set_size
import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.isSpecified

/** The axis a panel lays its children along: a Row's, a Column's, or none for a Box. */
@InternalComposeRavenApi
public enum class LayoutAxis { Horizontal, Vertical, None }

/**
 * How a panel places its children: its [axis], and the alignment each child gets on the axes it
 * doesn't lay out along, unless the child's Modifier says otherwise.
 */
@InternalComposeRavenApi
public data class PanelLayout(
    val axis: LayoutAxis,
    val horizontal: Alignment.Horizontal = Alignment.Start,
    val vertical: Alignment.Vertical = Alignment.Top,
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
    val testTag: String? = null,
)

internal fun fold(modifier: Modifier): FoldedModifier = modifier.foldIn(FoldedModifier()) { folded, element ->
    when (element) {
        is PaddingElement -> folded.copy(
            paddingStart = folded.paddingStart + element.start.value,
            paddingTop = folded.paddingTop + element.top.value,
            paddingEnd = folded.paddingEnd + element.end.value,
            paddingBottom = folded.paddingBottom + element.bottom.value,
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
        is AlignElement -> folded.copy(
            horizontal = element.horizontal?.align ?: folded.horizontal,
            vertical = element.vertical?.align ?: folded.vertical,
        )
        is TestTagElement -> folded.copy(testTag = element.tag)
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
    var halign = modifier.horizontal ?: parent.horizontal.align
    var valign = modifier.vertical ?: parent.vertical.align
    var fill: Float? = null
    when (parent.axis) {
        LayoutAxis.Vertical -> {
            if (modifier.vertical != null && modifier.vertical != VAlign.Fill) {
                warnings += "Modifier.align(Alignment.Vertical) works only in a Row"
            }
            fill = modifier.weight ?: if (modifier.vertical == VAlign.Fill) 1f else null
            valign = if (fill != null) VAlign.Fill else VAlign.Top
        }
        LayoutAxis.Horizontal -> {
            if (modifier.horizontal != null && modifier.horizontal != HAlign.Fill) {
                warnings += "Modifier.align(Alignment.Horizontal) works only in a Column"
            }
            fill = modifier.weight ?: if (modifier.horizontal == HAlign.Fill) 1f else null
            halign = if (fill != null) HAlign.Fill else HAlign.Start
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
    )
    return ResolvedLayout(slot, warnings)
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
}

/** Where layout warnings go. Each is given once per process. */
internal var layoutWarningSink: (String) -> Unit = { println("compose-raven: warning: $it") }

private val warningsGiven = mutableSetOf<String>()

internal fun warnOnce(warning: String) {
    if (warningsGiven.add(warning)) layoutWarningSink(warning)
}
