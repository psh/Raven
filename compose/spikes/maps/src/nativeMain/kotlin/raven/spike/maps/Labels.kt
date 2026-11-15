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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.TimeSource

// Labels, as MapLibre's symbol layers place them, in part: the text of `text-field`, at
// `text-size`, in `text-color` with a `text-halo-color` halo `text-halo-width` wide, and kept apart
// by collision, the layers higher in the style first. At a point, wrapped at `text-max-width` and
// placed by `text-anchor` and `text-offset`; along a line, every `symbol-spacing`, each glyph turned
// to the line where it stands (Raven's rotated text), reading left to right, and left out where the
// line bends more than 45 degrees between two glyphs. Left out: icons (they need the style's sprite
// sheet), the style's fonts (Liberty's Noto Sans faces; Raven's default font draws every label),
// letter spacing, halo blur, and fading in and out.

/** A label of a tile: its text, at a point of the tile's unit square. */
class Label(val text: String, val x: Float, val y: Float)

/** A label along a line of a tile: its text, and the line's x, y pairs in the tile's unit square. */
class LineLabel(val text: String, val points: FloatArray)

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

    private val heights = HashMap<Float, Float>()

    fun width(text: String, size: Float): Float = widths.getOrPut("$size|$text") { measure(text, size).first }

    /** The height of a line of text at [size]. */
    fun height(size: Float): Float = heights.getOrPut(size) { measure("Ag", size).second }

    private fun measure(text: String, size: Float): Pair<Float, Float> = memScoped {
        val width = alloc<FloatVar>()
        val height = alloc<FloatVar>()
        Rfont_measure_text(Rfont_get_default(), size, text, width.ptr, height.ptr)
        width.value to height.value
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

    fun fits(box: Box): Boolean {
        forCells(box) { k -> if (cells[k]?.any { it.overlaps(box) } == true) return false }
        return true
    }

    fun insert(box: Box) {
        forCells(box) { k -> cells.getOrPut(k) { ArrayList() } += box }
    }

    fun place(box: Box): Boolean = fits(box).also { if (it) insert(box) }
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
        val spacing = (layer.layout("symbol-spacing", zoom) as? Double ?: 250.0).toFloat()
        val style = GlyphStyle(textSize, measure.height(textSize), color, halo, haloWidth, padding)
        for (tile in tiles) {
            val bucket = tile.buckets[layer.id] ?: continue
            val n = (1 shl tile.id.z).toDouble()
            val scale = (world / n).toFloat()
            val ox = ((tile.id.x / n - camera.centerX) * world + size.width / 2).toFloat()
            val oy = ((tile.id.y / n - camera.centerY) * world + size.height / 2).toFloat()
            for (label in bucket.lineLabels) {
                candidates++
                if (placeAlong(label, ox, oy, scale, spacing, style, measure, grid, seen)) placed++
            }
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

/** How a layer's glyphs look along a line. */
private class GlyphStyle(val size: Float, val height: Float, val color: Color, val halo: Color?, val haloWidth: Float, val padding: Float)

/** A point of a line on screen, and the line's direction there, in radians. */
private class Along(val x: Float, val y: Float, val angle: Float)

/**
 * Places [label] along its line, on screen at ([ox], [oy]) plus its points times [scale]: at the
 * middle of every [spacing] of line long enough for it, glyph by glyph, each turned to the line
 * where it stands. Returns whether it placed it once at least.
 */
private fun DrawScope.placeAlong(
    label: LineLabel,
    ox: Float,
    oy: Float,
    scale: Float,
    spacing: Float,
    style: GlyphStyle,
    measure: TextMeasure,
    grid: CollisionGrid,
    seen: HashSet<String>,
): Boolean {
    val count = label.points.size / 2
    val xs = FloatArray(count) { ox + label.points[it * 2] * scale }
    val ys = FloatArray(count) { oy + label.points[it * 2 + 1] * scale }
    // Off screen, nothing to place.
    if (xs.max() < 0 || xs.min() > size.width || ys.max() < 0 || ys.min() > size.height) return false
    val cumulative = FloatArray(count)
    for (i in 1 until count) cumulative[i] = cumulative[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
    val length = cumulative.last()
    val text = label.text.replace('\n', ' ')
    val width = measure.width(text, style.size)
    if (width + style.size > length) return false
    // Each glyph's advance, from the widths of the text up to it, so kerning is kept.
    val starts = FloatArray(text.length + 1) { if (it == 0) 0f else measure.width(text.substring(0, it), style.size) }
    var any = false
    val repeats = maxOf(1, (length / spacing).toInt())
    for (r in 0 until repeats) {
        val center = (r + 0.5f) * length / repeats
        if (center - width / 2 < 0 || center + width / 2 > length) continue
        // Reading left to right: along the line, or back along it when it runs leftward.
        val reversed = cos(along(xs, ys, cumulative, center).angle) < 0
        val glyphs = ArrayList<Pair<Int, Along>>()
        var previous: Float? = null
        var ok = true
        for (i in text.indices) {
            val middle = (starts[i] + starts[i + 1]) / 2 - width / 2
            val at = along(xs, ys, cumulative, if (reversed) center - middle else center + middle)
            val angle = if (reversed) at.angle + PI.toFloat() else at.angle
            if (previous != null && abs(turn(angle, previous)) > MAX_TURN) {
                ok = false
                break
            }
            previous = angle
            glyphs += i to Along(at.x, at.y, angle)
        }
        if (!ok) continue
        val half = style.height / 2 + style.padding
        val boxes = glyphs.map { (_, at) -> Box(at.x - half, at.y - half, at.x + half, at.y + half) }
        // The same name placed nearby already, from the next tile's buffer or a piece of the same street.
        val key = "$text|${(glyphs[glyphs.size / 2].second.x / 64).roundToInt()}|${(glyphs[glyphs.size / 2].second.y / 64).roundToInt()}"
        if (key in seen || !boxes.all { grid.fits(it) }) continue
        boxes.forEach { grid.insert(it) }
        seen += key
        any = true
        for ((i, at) in glyphs) {
            val c = text[i]
            if (c == ' ') continue
            val advance = starts[i + 1] - starts[i]
            val degrees = at.angle * 180f / PI.toFloat()
            // The glyph's top left corner, so its middle sits on the line.
            val cosA = cos(at.angle)
            val sinA = sin(at.angle)
            val dx = -advance / 2
            val dy = -style.height / 2
            val left = at.x + dx * cosA - dy * sinA
            val top = at.y + dx * sinA + dy * cosA
            val glyph = c.toString()
            if (style.halo != null && style.haloWidth > 0f) {
                val d = max(style.haloWidth * 0.75f, 0.5f)
                for ((hx, hy) in HALO) drawText(glyph, Offset(left + hx * d, top + hy * d), style.halo, style.size.sp, rotation = degrees)
            }
            drawText(glyph, Offset(left, top), style.color, style.size.sp, rotation = degrees)
        }
    }
    return any
}

/** The point [distance] along the line of [xs], [ys], and its direction there. */
private fun along(xs: FloatArray, ys: FloatArray, cumulative: FloatArray, distance: Float): Along {
    var i = 1
    while (i < cumulative.size - 1 && cumulative[i] < distance) i++
    val segment = cumulative[i] - cumulative[i - 1]
    val t = if (segment > 0f) ((distance - cumulative[i - 1]) / segment).coerceIn(0f, 1f) else 0f
    return Along(xs[i - 1] + (xs[i] - xs[i - 1]) * t, ys[i - 1] + (ys[i] - ys[i - 1]) * t, atan2(ys[i] - ys[i - 1], xs[i] - xs[i - 1]))
}

/** The turn from [from] to [to], between -pi and pi. */
private fun turn(to: Float, from: Float): Float {
    var d = to - from
    while (d > PI) d -= 2 * PI.toFloat()
    while (d < -PI) d += 2 * PI.toFloat()
    return d
}

/** MapLibre's `text-max-angle`: the most a line may turn between two glyphs of a label. */
private const val MAX_TURN = (45 * PI / 180).toFloat()

/** Eight directions around a letter, for its halo. */
private val HALO = listOf(-1f to -1f, 0f to -1f, 1f to -1f, -1f to 0f, 1f to 0f, -1f to 1f, 0f to 1f, 1f to 1f)
