package raven.spike.maps

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

// What a line layer's paint does to its lines' shapes, as MapLibre's style spec has it, in part:
// `line-dasharray`, dashes and gaps in line widths, the pattern starting again at each line's
// start; `line-gap-width`, the line drawn twice, either side of a gap that wide. Both depend on
// the line's width on screen, so they are cut for the zoom drawn, and kept while it holds.

/** A layer's dashes at a zoom: on, off, on, off... in line widths; null for a solid line. */
fun StyleLayer.dashes(zoom: Double): List<Float>? {
    val raw = paint("line-dasharray", zoom) as? List<*> ?: return null
    val values = raw.map { (it as? Double ?: return null).toFloat() }
    // A pattern of nothing but gaps, or of an empty dash and an empty gap, draws a solid line.
    if (values.size < 2 || values.sum() <= 0f || values.filterIndexed { i, _ -> i % 2 == 1 }.all { it <= 0f }) return null
    return values
}

/**
 * [packed] lines (x, y pairs, a NaN pair between lines) cut into dashes of [pattern] (on, off, on,
 * off... lengths, in the lines' units), the pattern starting again at each line's start. The
 * dashes come out the same way, a NaN pair between each.
 */
fun dash(packed: FloatArray, pattern: List<Float>): FloatArray {
    val out = FloatList()
    // Where the dash being drawn starts in [out], and how long it is so far: an empty one is dropped.
    var dashStart = -1
    var dashLength = 0f
    fun begin(x: Float, y: Float) {
        dashStart = out.size
        dashLength = 0f
        out.add(x, y)
    }
    fun finish() {
        if (dashStart < 0) return
        if (dashLength > 0f) out.add(Float.NaN, Float.NaN) else out.truncate(dashStart)
        dashStart = -1
    }
    var i = 0
    while (i < packed.size) {
        // One line: from i to the next NaN.
        var end = i
        while (end < packed.size && !packed[end].isNaN()) end += 2
        var phase = 0
        var left = pattern[0]
        var px = packed[i]
        var py = packed[i + 1]
        if (left > 0f) begin(px, py)
        var j = i + 2
        while (j < end) {
            val x = packed[j]
            val y = packed[j + 1]
            var length = hypot(x - px, y - py)
            var sx = px
            var sy = py
            while (length > 0f) {
                val step = min(left, length)
                val t = step / length
                sx += (x - sx) * t
                sy += (y - sy) * t
                length -= step
                left -= step
                if (dashStart >= 0) dashLength += step
                if (left <= 0f) {
                    // The end of this dash or gap, here: the next starts.
                    if (phase % 2 == 0 && dashStart >= 0) {
                        out.add(sx, sy)
                        finish()
                    }
                    phase = (phase + 1) % pattern.size
                    left = pattern[phase]
                    if (phase % 2 == 0 && left > 0f) begin(sx, sy)
                }
            }
            if (dashStart >= 0) out.add(x, y)
            px = x
            py = y
            j += 2
        }
        finish()
        i = end + 2
    }
    return out.toArray()
}

/**
 * [packed] lines offset by [distance] to their left (negative: their right), in the lines' units:
 * each point moved along the bisector of the lines meeting there, by as much as keeps both [distance]
 * away (a mitre), at most four times it at sharp corners.
 */
fun offset(packed: FloatArray, distance: Float): FloatArray {
    val out = FloatList()
    var i = 0
    while (i < packed.size) {
        var end = i
        while (end < packed.size && !packed[end].isNaN()) end += 2
        val count = (end - i) / 2
        for (k in 0 until count) {
            val x = packed[i + k * 2]
            val y = packed[i + k * 2 + 1]
            // The normals of the segments before and after this point, to their left (y down).
            val before = if (k > 0) normal(packed[i + k * 2 - 2], packed[i + k * 2 - 1], x, y) else null
            val after = if (k < count - 1) normal(x, y, packed[i + k * 2 + 2], packed[i + k * 2 + 3]) else null
            val (nx, ny) = when {
                before != null && after != null -> {
                    // The mitre: the bisector m = n1 + n2 over 1 + n1 . n2 keeps the point a unit
                    // from both segments; its length, sqrt(2 / (1 + n1 . n2)), is capped at 4.
                    val dot = before.first * after.first + before.second * after.second
                    val denominator = (1f + dot).coerceAtLeast(1f / 8f)
                    (before.first + after.first) / denominator to (before.second + after.second) / denominator
                }
                before != null -> before
                after != null -> after
                else -> 0f to 0f
            }
            out.add(x + nx * distance, y + ny * distance)
        }
        out.add(Float.NaN, Float.NaN)
        i = end + 2
    }
    return out.toArray()
}

/** The unit normal to the left of the segment from (x0, y0) to (x1, y1), y down; null for an empty one. */
private fun normal(x0: Float, y0: Float, x1: Float, y1: Float): Pair<Float, Float>? {
    val dx = x1 - x0
    val dy = y1 - y0
    val length = hypot(dx, dy)
    if (length <= 0f) return null
    return dy / length to -dx / length
}

/**
 * The dashed and offset shapes of tiles' lines, cut for the size they are drawn at: kept per tile
 * and layer, and cut again when that size changes by more than a hundredth.
 */
class LineShapes {
    private class Entry(val unit: Float, val shape: FloatArray)

    private val entries = HashMap<String, Entry>()

    /** Shapes cut so far: what the frames' zooms cost. */
    var cuts = 0
        private set

    /**
     * [bucket]'s lines for [layer] in [tile], dashed by [pattern] and offset by [offsetWidths], both
     * in widths of a line [width] pixels wide drawn at [scale] pixels a tile.
     */
    fun shape(tile: TileId, layer: String, bucket: Bucket, pattern: List<Float>?, offsetWidths: Float, width: Float, scale: Float): FloatArray {
        if (pattern == null && offsetWidths == 0f) return bucket.packedLines
        // A line width in tile units: the shapes' scale.
        val unit = width.coerceAtLeast(1f) / scale
        val key = "${tile.z}/${tile.x}/${tile.y}|$layer|$offsetWidths"
        entries[key]?.let { if (abs(it.unit - unit) <= it.unit * 0.01f) return it.shape }
        var shape = bucket.packedLines
        if (offsetWidths != 0f) shape = offset(shape, offsetWidths * unit)
        if (pattern != null) shape = dash(shape, pattern.map { it * unit })
        entries[key] = Entry(unit, shape)
        cuts++
        return shape
    }

    /** Forgets the shapes of tiles no longer drawn. */
    fun retain(tiles: Collection<TileId>) {
        val keep = tiles.map { "${it.z}/${it.x}/${it.y}|" }
        entries.keys.retainAll { key -> keep.any { key.startsWith(it) } }
    }
}
