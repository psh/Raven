package raven.spike.maps

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import raven.cinterop.R_FONT_BOLD
import raven.cinterop.R_FONT_BOLD_ITALIC
import raven.cinterop.R_FONT_ITALIC
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_TRUE
import raven.cinterop.Rfont_get_default
import raven.cinterop.Rfont_get_variant
import raven.cinterop.Rfont_is_valid
import raven.cinterop.Rfont_measure_text
import raven.cinterop.Rid
import raven.cinterop.Rtheme_get_font
import raven.compose.ui.geometry.Offset
import raven.compose.ui.geometry.Size
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.ui.graphics.drawscope.DrawScope
import raven.compose.ui.text.font.FontStyle
import raven.compose.ui.text.font.FontWeight
import raven.compose.ui.unit.IntOffset
import raven.compose.ui.unit.IntSize
import raven.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.TimeSource

// Labels, as MapLibre's symbol layers place them, in part: the text of `text-field`, at
// `text-size`, in the face `text-font` names (regular, bold or italic: Raven's default font, with the
// faces it derives), in `text-color` with a `text-halo-color` halo `text-halo-width` wide, with the
// `icon-image` of the style's sprite sheet at `icon-size`, and kept apart by collision, the layers
// higher in the style first. At a point, the icon on it and the text wrapped at `text-max-width`
// and placed by `text-anchor` and `text-offset`; along a line, every `symbol-spacing`: glyph by
// glyph, each turned to the line where it stands (Raven's rotated text), reading left to right, and
// left out where the line bends more than 45 degrees between two glyphs; or upright, an icon with
// its text, where the text keeps to the viewport (road shields). Left out: icons turned with the
// map (one-way arrows: Raven doesn't turn images), the style's own fonts (Liberty's Noto Sans),
// letter spacing, halo blur, and fading in and out.

/** A symbol of a tile at a point of its unit square: its text, its icon, either of them null. */
class Label(val text: String?, val x: Float, val y: Float, val icon: String? = null)

/** A symbol along a line of a tile: its text and icon, and the line's x, y pairs in the tile's unit square. */
class LineLabel(val text: String?, val points: FloatArray, val icon: String? = null)

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

/** The name of the sprite a symbol layer gives a feature: `icon-image`; null if none. */
fun iconName(layer: StyleLayer, context: EvaluationContext): String? =
    layer.layout["icon-image"]?.let { evaluate(it, context) }?.let(::text)?.ifEmpty { null }

/** One icon of a sprite sheet: where it is in the sheet, in its pixels, and how many of them a logical pixel holds. */
class SpriteIcon(val x: Int, val y: Int, val width: Int, val height: Int, val pixelRatio: Float) {
    val logicalWidth: Float get() = width / pixelRatio
    val logicalHeight: Float get() = height / pixelRatio
}

/** A style's sprite sheet: its icons by name, as its JSON index says, and the PNG they are in. */
class Sprite(val icons: Map<String, SpriteIcon>, val png: ByteArray) {
    companion object {
        fun parse(json: String, png: ByteArray): Sprite {
            @Suppress("UNCHECKED_CAST")
            val index = parseJson(json) as Map<String, Map<String, Any?>>
            val icons = index.mapValues { (_, v) ->
                SpriteIcon(
                    (v["x"] as Double).toInt(), (v["y"] as Double).toInt(),
                    (v["width"] as Double).toInt(), (v["height"] as Double).toInt(),
                    (v["pixelRatio"] as? Double ?: 1.0).toFloat(),
                )
            }
            return Sprite(icons, png)
        }
    }
}

/** A face of the default font, as a style's `text-font` names it ("Noto Sans Bold Italic"). */
enum class Face(val weight: FontWeight?, val style: FontStyle?) {
    Regular(null, null),
    Bold(FontWeight.Bold, null),
    Italic(null, FontStyle.Italic),
    BoldItalic(FontWeight.Bold, FontStyle.Italic);

    companion object {
        fun of(fonts: Any?): Face {
            val name = (fonts as? List<*>)?.firstOrNull() as? String ?: return Regular
            val bold = "Bold" in name
            val italic = "Italic" in name
            return when {
                bold && italic -> BoldItalic
                bold -> Bold
                italic -> Italic
                else -> Regular
            }
        }
    }
}

/**
 * Widths of texts measured as `drawText` draws them: the theme's font, or the face of it Raven
 * derives, by size, face and text, since labels repeat across frames.
 */
@OptIn(ExperimentalForeignApi::class)
class TextMeasure {
    private val widths = HashMap<String, Float>()
    private val heights = HashMap<Float, Float>()
    private val fonts = HashMap<Face, Rid>()

    fun width(text: String, size: Float, face: Face = Face.Regular): Float = widths.getOrPut("$size|${face.ordinal}|$text") { measure(text, size, face).first }

    /** The height of a line of text at [size]. */
    fun height(size: Float): Float = heights.getOrPut(size) { measure("Ag", size, Face.Regular).second }

    private fun font(face: Face): Rid = fonts.getOrPut(face) {
        if (face == Face.Regular) return R_INVALID_ID
        val base = Rtheme_get_font().takeIf { Rfont_is_valid(it) == R_TRUE } ?: Rfont_get_default()
        Rfont_get_variant(base, when (face) {
            Face.Bold -> R_FONT_BOLD
            Face.Italic -> R_FONT_ITALIC
            else -> R_FONT_BOLD_ITALIC
        })
    }

    private fun measure(text: String, size: Float, face: Face): Pair<Float, Float> = memScoped {
        val width = alloc<FloatVar>()
        val height = alloc<FloatVar>()
        Rfont_measure_text(font(face), size, text, width.ptr, height.ptr)
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
}

/** Words of [text] in lines no wider than [maxWidth], its own line breaks kept. */
private fun wrap(text: String, size: Float, face: Face, maxWidth: Float, measure: TextMeasure): List<String> {
    val lines = ArrayList<String>()
    for (paragraph in text.split('\n')) {
        var line = ""
        for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && measure.width(candidate, size, face) > maxWidth) {
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

/** What the labels of the last frame cost: [ms] in all; [placeMs] the last placement's. */
class LabelStats {
    var placed = 0
    var candidates = 0
    var icons = 0
    var ms = 0.0
    var placeMs = 0.0

    /** Whether the last frame drew the placement before it, moved, rather than placing again. */
    var reused = false
}

/** How a layer's texts look. */
internal class GlyphStyle(
    val size: Float,
    val height: Float,
    val color: Color,
    val halo: Color?,
    val haloWidth: Float,
    val padding: Float,
    val face: Face,
)

/** What placement leaves to draw: a text (a line of a label at a point, or a glyph along a line), or an icon. */
internal sealed class DrawOp(val x: Float, val y: Float)

internal class TextOp(val text: String, x: Float, y: Float, val rotation: Float, val style: GlyphStyle) : DrawOp(x, y)

internal class IconOp(val icon: SpriteIcon, x: Float, y: Float, val width: Float, val height: Float) : DrawOp(x, y)

/**
 * The last placement, and what it was made for: the zoom, the view's size, the prepared tiles
 * ([TileStore.version]) and the camera's centre. Panning moves it rather than placing again.
 */
class LabelCache {
    internal var zoom = Double.NaN
    internal var width = 0f
    internal var height = 0f
    internal var version = -1
    internal var centerX = 0.0
    internal var centerY = 0.0
    internal var ops: List<DrawOp> = emptyList()
}

/** A sprite sheet ready to draw: its icons, and the image it was decoded into. */
class SpriteSheet(val sprite: Sprite, val image: ImageBitmap)

/**
 * Draws the labels of [tiles] under [camera], their icons from [sprites]. The placement is kept in
 * [cache] while the view and the tiles ready ([version]) stay as they were, and moves with the map,
 * as MapLibre's labels do between placements: a pan of up to a quarter of the view, or a zoom of
 * up to a quarter of a level, about the view's centre, the text and icons keeping their size. Past
 * those, or once the camera has [settled] somewhere else than where it was placed, the labels are
 * placed again.
 */
fun DrawScope.drawLabels(
    style: Style,
    tiles: List<PreparedTile>,
    camera: Camera,
    measure: TextMeasure,
    stats: LabelStats,
    cache: LabelCache,
    version: Int,
    settled: Boolean = false,
    sprites: SpriteSheet? = null,
) {
    val start = TimeSource.Monotonic.markNow()
    val world = camera.worldSize
    val k = (world / (TILE_SIZE * 2.0.pow(cache.zoom))).toFloat()
    var dx = ((cache.centerX - camera.centerX) * world).toFloat()
    var dy = ((cache.centerY - camera.centerY) * world).toFloat()
    val moved = cache.zoom != camera.zoom || dx != 0f || dy != 0f
    val stale = cache.zoom.isNaN() || abs(camera.zoom - cache.zoom) > 0.25 || cache.width != size.width || cache.height != size.height ||
        cache.version != version || abs(dx) > size.width / 4 || abs(dy) > size.height / 4 || (settled && moved)
    var scale = k
    if (stale) {
        cache.ops = placeLabels(style, tiles, camera, measure, stats, sprites?.sprite)
        cache.zoom = camera.zoom
        cache.width = size.width
        cache.height = size.height
        cache.version = version
        cache.centerX = camera.centerX
        cache.centerY = camera.centerY
        stats.placeMs = start.elapsedNow().inWholeMicroseconds / 1000.0
        dx = 0f
        dy = 0f
        scale = 1f
    }
    stats.reused = !stale
    // A point placed at p0 is now at the view's centre plus (p0 - centre) scaled, plus the pan.
    val cx = size.width / 2
    val cy = size.height / 2
    for (op in cache.ops) {
        val x = cx + (op.x - cx) * scale + dx
        val y = cy + (op.y - cy) * scale + dy
        when (op) {
            is IconOp -> sprites?.let {
                drawImage(it.image, IntOffset(op.icon.x, op.icon.y), IntSize(op.icon.width, op.icon.height), Offset(x, y), Size(op.width, op.height))
            }
            is TextOp -> {
                val st = op.style
                if (st.halo != null && st.haloWidth > 0f) {
                    val d = max(st.haloWidth * 0.75f, 0.5f)
                    for ((hx, hy) in HALO) {
                        drawText(op.text, Offset(x + hx * d, y + hy * d), st.halo, st.size.sp, rotation = op.rotation, fontWeight = st.face.weight, fontStyle = st.face.style)
                    }
                }
                drawText(op.text, Offset(x, y), st.color, st.size.sp, rotation = op.rotation, fontWeight = st.face.weight, fontStyle = st.face.style)
            }
        }
    }
    stats.ms = start.elapsedNow().inWholeMicroseconds / 1000.0
}

/** What a layer's symbols are, at a zoom: read once a placement, not once a symbol. */
private class SymbolStyle(
    val text: GlyphStyle,
    val maxWidth: Float,
    val anchor: String,
    val offset: List<Float>,
    val lineHeight: Float,
    val spacing: Float,
    val iconSize: Float,
    val iconOverlaps: Boolean,
    /** Along a line, each glyph turned to it; else upright, at points along it. */
    val alongLine: Boolean,
    val upright: Boolean,
)

/** What a placement shares: the collision grid, the names placed, the operations, the counts. */
private class Placement(val grid: CollisionGrid, val sprite: Sprite?, val measure: TextMeasure) {
    val seen = HashSet<String>()
    val ops = ArrayList<DrawOp>()
    var placed = 0
    var candidates = 0
    var icons = 0
}

/**
 * Places the labels of [tiles] under [camera]: layer by layer, the style's last first, each label
 * only where its boxes, padded, overlap none placed before it. Returns what to draw.
 */
private fun DrawScope.placeLabels(style: Style, tiles: List<PreparedTile>, camera: Camera, measure: TextMeasure, stats: LabelStats, sprite: Sprite?): List<DrawOp> {
    val zoom = camera.zoom
    val world = camera.worldSize
    val placement = Placement(CollisionGrid(), sprite, measure)
    for (layer in style.symbols.asReversed()) {
        if (zoom < layer.minZoom || zoom >= layer.maxZoom) continue
        val textSize = (layer.layout("text-size", zoom) as? Double ?: 16.0).toFloat()
        val glyphStyle = GlyphStyle(
            textSize, measure.height(textSize),
            parseColor(layer.paint("text-color", zoom)) ?: Color.Black,
            parseColor(layer.paint("text-halo-color", zoom)),
            (layer.paint("text-halo-width", zoom) as? Double ?: 0.0).toFloat(),
            (layer.layout("text-padding", zoom) as? Double ?: 2.0).toFloat(),
            Face.of(layer.layout("text-font", zoom)),
        )
        val symbols = SymbolStyle(
            glyphStyle,
            maxWidth = (layer.layout("text-max-width", zoom) as? Double ?: 10.0).toFloat() * textSize,
            anchor = layer.layout("text-anchor", zoom) as? String ?: "center",
            offset = (layer.layout("text-offset", zoom) as? List<*>)?.map { (it as Double).toFloat() * textSize } ?: listOf(0f, 0f),
            lineHeight = textSize * 1.2f,
            spacing = (layer.layout("symbol-spacing", zoom) as? Double ?: 250.0).toFloat(),
            iconSize = (layer.layout("icon-size", zoom) as? Double ?: 1.0).toFloat(),
            iconOverlaps = layer.layout("icon-allow-overlap", zoom) == true,
            alongLine = layer.placementAt(zoom) == "line",
            upright = layer.layout("text-rotation-alignment", zoom) == "viewport",
        )
        for (tile in tiles) {
            val bucket = tile.buckets[layer.id] ?: continue
            val n = (1 shl tile.id.z).toDouble()
            val scale = (world / n).toFloat()
            val ox = ((tile.id.x / n - camera.centerX) * world + size.width / 2).toFloat()
            val oy = ((tile.id.y / n - camera.centerY) * world + size.height / 2).toFloat()
            for (label in bucket.lineLabels) {
                placement.candidates++
                val text = label.text
                val placed = if (symbols.alongLine && !symbols.upright && text != null) {
                    placeAlong(text, label.points, ox, oy, scale, symbols.spacing, glyphStyle, placement)
                } else {
                    placeAtPointsAlong(label, ox, oy, scale, symbols, placement)
                }
                if (placed) placement.placed++
            }
            for (label in bucket.labels) {
                placement.candidates++
                if (placePoint(label.text, label.icon, ox + label.x * scale, oy + label.y * scale, symbols, placement)) placement.placed++
            }
        }
    }
    stats.placed = placement.placed
    stats.candidates = placement.candidates
    stats.icons = placement.icons
    return placement.ops
}

/**
 * Places a symbol at ([x], [y]): its icon centred on it, its text placed by the layer's anchor and
 * offset, both only where they overlap nothing placed. Returns whether it placed it.
 */
private fun DrawScope.placePoint(text: String?, iconName: String?, x: Float, y: Float, style: SymbolStyle, placement: Placement): Boolean {
    val margin = style.maxWidth
    if (x < -margin || y < -margin || x > size.width + margin || y > size.height + margin) return false
    val icon = iconName?.let { placement.sprite?.icons?.get(it) }
    if (text == null && icon == null) return false
    val boxes = ArrayList<Box>(2)
    var iconBox: Box? = null
    if (icon != null) {
        val w = icon.logicalWidth * style.iconSize
        val h = icon.logicalHeight * style.iconSize
        iconBox = Box(x - w / 2, y - h / 2, x + w / 2, y + h / 2)
        if (!style.iconOverlaps) boxes += iconBox
    }
    val st = style.text
    var lines = emptyList<String>()
    var widths = emptyList<Float>()
    var left = 0f
    var top = 0f
    var w = 0f
    if (text != null) {
        lines = wrap(text, st.size, st.face, style.maxWidth, placement.measure)
        widths = lines.map { placement.measure.width(it, st.size, st.face) }
        w = widths.maxOrNull() ?: 0f
        val h = style.lineHeight * lines.size
        // Where the anchor puts the box, then the offset.
        left = x + style.offset[0] - when {
            style.anchor.endsWith("left") -> 0f
            style.anchor.endsWith("right") -> w
            else -> w / 2
        }
        top = y + style.offset[1] - when {
            style.anchor.startsWith("top") -> 0f
            style.anchor.startsWith("bottom") -> h
            else -> h / 2
        }
        boxes += Box(left - st.padding, top - st.padding, left + w + st.padding, top + h + st.padding)
    }
    // The same symbol in the next tile's buffer, or again in the same place, once.
    val key = "$text|$iconName|${(x / 8).roundToInt()}|${(y / 8).roundToInt()}"
    if (key in placement.seen || !boxes.all { placement.grid.fits(it) }) return false
    boxes.forEach { placement.grid.insert(it) }
    placement.seen += key
    if (icon != null && iconBox != null) {
        placement.ops += IconOp(icon, iconBox.left, iconBox.top, iconBox.right - iconBox.left, iconBox.bottom - iconBox.top)
        placement.icons++
    }
    for ((i, line) in lines.withIndex()) placement.ops += TextOp(line, left + (w - widths[i]) / 2, top + i * style.lineHeight, 0f, st)
    return true
}

/** The line of [points] on screen, at ([ox], [oy]) plus the points times [scale], with the distance along it at each point. */
private class ScreenLine(points: FloatArray, ox: Float, oy: Float, scale: Float) {
    val count = points.size / 2
    val xs = FloatArray(count) { ox + points[it * 2] * scale }
    val ys = FloatArray(count) { oy + points[it * 2 + 1] * scale }
    val cumulative = FloatArray(count).also { for (i in 1 until count) it[i] = it[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]) }
    val length: Float get() = cumulative.last()

    fun offScreen(width: Float, height: Float): Boolean = xs.max() < 0 || xs.min() > width || ys.max() < 0 || ys.min() > height

    fun at(distance: Float): Along = along(xs, ys, cumulative, distance)
}

/** Places [label] upright at the middle of every [SymbolStyle.spacing] of its line: a road shield, its number on its icon. */
private fun DrawScope.placeAtPointsAlong(label: LineLabel, ox: Float, oy: Float, scale: Float, style: SymbolStyle, placement: Placement): Boolean {
    val line = ScreenLine(label.points, ox, oy, scale)
    if (line.offScreen(size.width, size.height)) return false
    val repeats = maxOf(1, (line.length / style.spacing).toInt())
    var any = false
    for (r in 0 until repeats) {
        val at = line.at((r + 0.5f) * line.length / repeats)
        if (placePoint(label.text, label.icon, at.x, at.y, style, placement)) any = true
    }
    return any
}

/** A point of a line on screen, and the line's direction there, in radians. */
private class Along(val x: Float, val y: Float, val angle: Float)

/**
 * Places [text] along the line of [points], on screen at ([ox], [oy]) plus the points times
 * [scale]: at the middle of every [spacing] of line long enough for it, glyph by glyph, each turned
 * to the line where it stands. Returns whether it placed it once at least.
 */
private fun DrawScope.placeAlong(text: String, points: FloatArray, ox: Float, oy: Float, scale: Float, spacing: Float, style: GlyphStyle, placement: Placement): Boolean {
    val line = ScreenLine(points, ox, oy, scale)
    if (line.offScreen(size.width, size.height)) return false
    val flat = text.replace('\n', ' ')
    val width = placement.measure.width(flat, style.size, style.face)
    if (width + style.size > line.length) return false
    // Each glyph's advance, from the widths of the text up to it, so kerning is kept.
    val starts = FloatArray(flat.length + 1) { if (it == 0) 0f else placement.measure.width(flat.substring(0, it), style.size, style.face) }
    var any = false
    val repeats = maxOf(1, (line.length / spacing).toInt())
    for (r in 0 until repeats) {
        val center = (r + 0.5f) * line.length / repeats
        if (center - width / 2 < 0 || center + width / 2 > line.length) continue
        // Reading left to right: along the line, or back along it when it runs leftward.
        val reversed = cos(line.at(center).angle) < 0
        val glyphs = ArrayList<Pair<Int, Along>>()
        var previous: Float? = null
        var ok = true
        for (i in flat.indices) {
            val middle = (starts[i] + starts[i + 1]) / 2 - width / 2
            val at = line.at(if (reversed) center - middle else center + middle)
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
        val mid = glyphs[glyphs.size / 2].second
        val key = "$flat|${(mid.x / 64).roundToInt()}|${(mid.y / 64).roundToInt()}"
        if (key in placement.seen || !boxes.all { placement.grid.fits(it) }) continue
        boxes.forEach { placement.grid.insert(it) }
        placement.seen += key
        any = true
        for ((i, at) in glyphs) {
            val c = flat[i]
            if (c == ' ') continue
            val advance = starts[i + 1] - starts[i]
            val degrees = at.angle * 180f / PI.toFloat()
            // The glyph's top left corner, so its middle sits on the line.
            val cosA = cos(at.angle)
            val sinA = sin(at.angle)
            val dx = -advance / 2
            val dy = -style.height / 2
            placement.ops += TextOp(c.toString(), at.x + dx * cosA - dy * sinA, at.y + dx * sinA + dy * cosA, degrees, style)
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

/** Four diagonals around a letter, for its halo: as wide as eight directions to the eye, at half the draws. */
private val HALO = listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)
