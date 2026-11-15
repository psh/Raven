package raven.spike.maps

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import raven.cinterop.Rfont_get_default
import raven.cinterop.Rfont_measure_text
import raven.compose.ui.geometry.Offset
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.drawscope.DrawScope
import raven.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.TimeSource

// Point labels, as MapLibre's symbol layers place them, in part: the text of `text-field`, at
// `text-size`, wrapped at `text-max-width`, placed by `text-anchor` and `text-offset`, in
// `text-color` with a `text-halo-color` halo `text-halo-width` wide, and kept apart by collision,
// the layers higher in the style first. Left out: icons (they need the style's sprite sheet), the
// style's fonts (Liberty's Noto Sans faces; Raven's default font draws every label), letter
// spacing, halo blur, fading in and out, and labels along lines (they need rotated text).

/** A label of a tile: its text, at a point of the tile's unit square. */
class Label(val text: String, val x: Float, val y: Float)

/** The text a symbol layer gives a feature: `text-field`, with `text-transform`; null if empty. */
fun labelText(layer: StyleLayer, context: EvaluationContext): String? {
    val field = layer.layout["text-field"] ?: return null
    var text = when (val value = evaluate(field, context)) {
        null -> return null
        // A legacy field: "{name}" tokens replaced by the feature's properties.
        is String -> if ('{' in value) Regex("\\{([^}]+)}").replace(value) { context.properties[it.groupValues[1]]?.let(::text).orEmpty() } else value
        else -> text(value)
    }.trim()
    when (layer.layout("text-transform", context.zoom)) {
        "uppercase" -> text = text.uppercase()
        "lowercase" -> text = text.lowercase()
    }
    return text.ifEmpty { null }
}

/** Widths of texts measured with Raven's default font, by size and text: labels repeat across frames. */
@OptIn(ExperimentalForeignApi::class)
class TextMeasure {
    private val widths = HashMap<String, Float>()

    fun width(text: String, size: Float): Float = widths.getOrPut("$size|$text") {
        memScoped {
            val width = alloc<FloatVar>()
            val height = alloc<FloatVar>()
            Rfont_measure_text(Rfont_get_default(), size, text, width.ptr, height.ptr)
            width.value
        }
    }
}

/** A box on screen, for collision. */
private class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlaps(other: Box): Boolean = left < other.right && other.left < right && top < other.bottom && other.top < bottom
}

/** The boxes placed this frame, in a grid of cells so a test looks only at its neighbours. */
private class CollisionGrid(private val cell: Float = 64f) {
    private val cells = HashMap<Long, MutableList<Box>>()

    private fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

    private inline fun forCells(box: Box, action: (Long) -> Unit) {
        for (x in (box.left / cell).toInt()..(box.right / cell).toInt()) {
            for (y in (box.top / cell).toInt()..(box.bottom / cell).toInt()) action(key(x, y))
        }
    }

    fun place(box: Box): Boolean {
        forCells(box) { k -> if (cells[k]?.any { it.overlaps(box) } == true) return false }
        forCells(box) { k -> cells.getOrPut(k) { ArrayList() } += box }
        return true
    }
}

/** Words of [text] in lines no wider than [maxWidth], its own line breaks kept. */
private fun wrap(text: String, size: Float, maxWidth: Float, measure: TextMeasure): List<String> {
    val lines = ArrayList<String>()
    for (paragraph in text.split('\n')) {
        var line = ""
        for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && measure.width(candidate, size) > maxWidth) {
                lines += line
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) lines += line
    }
    return lines
}

/** What the labels of the last frame cost. */
class LabelStats {
    var placed = 0
    var candidates = 0
    var ms = 0.0
}

/**
 * Places and draws the point labels of [tiles] under [camera]: layer by layer, the style's last
 * first, each label only where its box, padded, overlaps none placed before it.
 */
fun DrawScope.drawLabels(style: Style, tiles: List<PreparedTile>, camera: Camera, measure: TextMeasure, stats: LabelStats) {
    val start = TimeSource.Monotonic.markNow()
    val zoom = camera.zoom
    val world = camera.worldSize
    val grid = CollisionGrid()
    var placed = 0
    var candidates = 0
    val seen = HashSet<String>()
    for (layer in style.symbols.asReversed()) {
        if (zoom < layer.minZoom || zoom >= layer.maxZoom) continue
        val textSize = (layer.layout("text-size", zoom) as? Double ?: 16.0).toFloat()
        val maxWidth = (layer.layout("text-max-width", zoom) as? Double ?: 10.0).toFloat() * textSize
        val anchor = layer.layout("text-anchor", zoom) as? String ?: "center"
        val offset = (layer.layout("text-offset", zoom) as? List<*>)?.map { (it as Double).toFloat() * textSize } ?: listOf(0f, 0f)
        val padding = (layer.layout("text-padding", zoom) as? Double ?: 2.0).toFloat()
        val color = parseColor(layer.paint("text-color", zoom)) ?: Color.Black
        val halo = parseColor(layer.paint("text-halo-color", zoom))
        val haloWidth = (layer.paint("text-halo-width", zoom) as? Double ?: 0.0).toFloat()
        val lineHeight = textSize * 1.2f
        for (tile in tiles) {
            val bucket = tile.buckets[layer.id] ?: continue
            val n = (1 shl tile.id.z).toDouble()
            val scale = (world / n).toFloat()
            val ox = ((tile.id.x / n - camera.centerX) * world + size.width / 2).toFloat()
            val oy = ((tile.id.y / n - camera.centerY) * world + size.height / 2).toFloat()
            for (label in bucket.labels) {
                val x = ox + label.x * scale
                val y = oy + label.y * scale
                if (x < -maxWidth || y < -maxWidth || x > size.width + maxWidth || y > size.height + maxWidth) continue
                candidates++
                val lines = wrap(label.text, textSize, maxWidth, measure)
                if (lines.isEmpty()) continue
                val widths = lines.map { measure.width(it, textSize) }
                val w = widths.max()
                val h = lineHeight * lines.size
                // Where the anchor puts the box, then the offset.
                val left = x + offset[0] - when {
                    anchor.endsWith("left") -> 0f
                    anchor.endsWith("right") -> w
                    else -> w / 2
                }
                val top = y + offset[1] - when {
                    anchor.startsWith("top") -> 0f
                    anchor.startsWith("bottom") -> h
                    else -> h / 2
                }
                // The same name in the next tile's buffer, or again in the same place, once.
                val key = "${label.text}|${(left / 8).roundToInt()}|${(top / 8).roundToInt()}"
                if (key in seen) continue
                if (!grid.place(Box(left - padding, top - padding, left + w + padding, top + h + padding))) continue
                seen += key
                placed++
                for ((i, line) in lines.withIndex()) {
                    val lx = left + (w - widths[i]) / 2
                    val ly = top + i * lineHeight
                    if (halo != null && haloWidth > 0f) {
                        val d = max(haloWidth * 0.75f, 0.5f)
                        for ((dx, dy) in HALO) drawText(line, Offset(lx + dx * d, ly + dy * d), halo, textSize.sp)
                    }
                    drawText(line, Offset(lx, ly), color, textSize.sp)
                }
            }
        }
    }
    stats.placed = placed
    stats.candidates = candidates
    stats.ms = start.elapsedNow().inWholeMicroseconds / 1000.0
}

/** Eight directions around a letter, for its halo. */
private val HALO = listOf(-1f to -1f, 0f to -1f, 1f to -1f, -1f to 0f, 1f to 0f, -1f to 1f, 0f to 1f, 1f to 1f)
