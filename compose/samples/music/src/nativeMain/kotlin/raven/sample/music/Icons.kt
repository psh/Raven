package raven.sample.music

import raven.compose.ui.graphics.FilterQuality
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.ui.graphics.painter.BitmapPainter
import raven.compose.ui.graphics.painter.Painter
import raven.compose.ui.unit.IntOffset
import raven.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// The player's pictures, drawn once into one white atlas from signed distances, as the C example's
// tags.c does: an icon is a part of it, tinted with the text colour where it sits
// (ColorFilter.textColor()).

private const val CELL = 96
private const val COLUMNS = 8
private const val ROWS = 4

/** The glyphs, in the atlas's order. */
enum class Glyph {
    Play, Pause, Next, Previous, Shuffle, Repeat, RepeatOne, Heart,
    HeartFilled, Home, Search, Library, Volume, Muted, Queue, Settings,
    Plus, Clock, More, Back, Forward, Note, Microphone, Check,
    Panel, User, Sparkle, Close, Moon, Radio, Download, Share,
}

/** The atlas and a painter for each glyph. Raven must run. */
class Icons {
    private val atlas = ImageBitmap(CELL * COLUMNS, CELL * ROWS, drawAtlas(), FilterQuality.High)
    private val painters = Glyph.entries.associateWith { g ->
        BitmapPainter(atlas, IntOffset((g.ordinal % COLUMNS) * CELL, (g.ordinal / COLUMNS) * CELL), IntSize(CELL, CELL))
    }

    operator fun get(glyph: Glyph): Painter = painters.getValue(glyph)
}

private data class V(val x: Float, val y: Float)

private fun dot2(x: Float, y: Float) = x * x + y * y

/** Distance to a segment. */
private fun seg(p: V, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val ex = bx - ax
    val ey = by - ay
    val wx = p.x - ax
    val wy = p.y - ay
    val t = ((wx * ex + wy * ey) / (ex * ex + ey * ey)).coerceIn(0f, 1f)
    return sqrt(dot2(wx - ex * t, wy - ey * t))
}

/** Signed distance to a polygon. */
private fun poly(p: V, v: List<V>): Float {
    var d = dot2(p.x - v[0].x, p.y - v[0].y)
    var s = 1f
    var j = v.size - 1
    for (i in v.indices) {
        val ex = v[j].x - v[i].x
        val ey = v[j].y - v[i].y
        val wx = p.x - v[i].x
        val wy = p.y - v[i].y
        val t = ((wx * ex + wy * ey) / dot2(ex, ey)).coerceIn(0f, 1f)
        val c1 = p.y >= v[i].y
        val c2 = p.y < v[j].y
        val c3 = ex * wy > ey * wx
        d = min(d, dot2(wx - ex * t, wy - ey * t))
        if ((c1 && c2 && c3) || (!c1 && !c2 && !c3)) s = -s
        j = i
    }
    return s * sqrt(d)
}

private fun tri(p: V, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) = poly(p, listOf(V(ax, ay), V(bx, by), V(cx, cy)))

private fun box(p: V, cx: Float, cy: Float, hx: Float, hy: Float, r: Float): Float {
    val dx = abs(p.x - cx) - hx + r
    val dy = abs(p.y - cy) - hy + r
    return sqrt(dot2(max(dx, 0f), max(dy, 0f))) + min(max(dx, dy), 0f) - r
}

private fun circle(p: V, cx: Float, cy: Float, r: Float) = sqrt(dot2(p.x - cx, p.y - cy)) - r

/** Inigo Quilez's heart, its tip at the bottom. */
private fun heart(p: V): Float {
    val s = 0.6f
    val qx = abs(p.x - 0.5f) / s
    val qy = (0.8f - p.y) / s
    val d = if (qy + qx > 1f) {
        sqrt(dot2(qx - 0.25f, qy - 0.75f)) - 0.35355f
    } else {
        val m = 0.5f * max(qx + qy, 0f)
        sqrt(min(dot2(qx, qy - 1f), dot2(qx - m, qy - m))) * (if (qx - qy > 0f) 1f else -1f)
    }
    return d * s
}

private const val W = 0.075f

private fun line(d: Float) = d - W * 0.5f

private fun ring(d: Float) = abs(d) - W * 0.5f

private fun speaker(p: V) = poly(p, listOf(V(0.14f, 0.38f), V(0.30f, 0.38f), V(0.50f, 0.20f), V(0.50f, 0.80f), V(0.30f, 0.62f), V(0.14f, 0.62f))) - 0.01f

private fun repeatShape(p: V): Float {
    // A gap in the top line, for the arrow.
    val loop = max(ring(box(p, 0.5f, 0.5f, 0.32f, 0.2f, 0.14f)), -box(p, 0.66f, 0.30f, 0.07f, 0.1f, 0f))
    return min(loop, tri(p, 0.58f, 0.17f, 0.58f, 0.43f, 0.75f, 0.30f))
}

private fun glyph(index: Int, p: V): Float = when (index) {
    0 -> tri(p, 0.32f, 0.22f, 0.32f, 0.78f, 0.80f, 0.50f) - 0.035f
    1 -> min(box(p, 0.36f, 0.5f, 0.075f, 0.28f, 0.035f), box(p, 0.64f, 0.5f, 0.075f, 0.28f, 0.035f))
    2 -> min(tri(p, 0.22f, 0.25f, 0.22f, 0.75f, 0.62f, 0.5f) - 0.03f, box(p, 0.72f, 0.5f, 0.05f, 0.26f, 0.03f))
    3 -> glyph(2, V(1f - p.x, p.y))
    4 -> {
        val a = min(seg(p, 0.14f, 0.32f, 0.34f, 0.32f), min(seg(p, 0.34f, 0.32f, 0.60f, 0.68f), seg(p, 0.60f, 0.68f, 0.78f, 0.68f)))
        val b = min(seg(p, 0.14f, 0.68f, 0.34f, 0.68f), min(seg(p, 0.34f, 0.68f, 0.60f, 0.32f), seg(p, 0.60f, 0.32f, 0.78f, 0.32f)))
        val arrows = min(tri(p, 0.74f, 0.19f, 0.74f, 0.45f, 0.90f, 0.32f), tri(p, 0.74f, 0.55f, 0.74f, 0.81f, 0.90f, 0.68f))
        min(line(min(a, b)), arrows)
    }
    5 -> repeatShape(p)
    6 -> min(repeatShape(p), line(min(seg(p, 0.5f, 0.42f, 0.5f, 0.6f), seg(p, 0.44f, 0.46f, 0.5f, 0.42f))) + 0.01f)
    7 -> ring(heart(p))
    8 -> heart(p)
    9 -> min(ring(poly(p, listOf(V(0.18f, 0.46f), V(0.5f, 0.18f), V(0.82f, 0.46f), V(0.82f, 0.82f), V(0.18f, 0.82f)))), box(p, 0.5f, 0.71f, 0.07f, 0.11f, 0.02f))
    10 -> min(ring(circle(p, 0.44f, 0.44f, 0.24f)), seg(p, 0.62f, 0.62f, 0.82f, 0.82f) - W * 0.6f)
    11 -> line(min(seg(p, 0.22f, 0.2f, 0.22f, 0.8f), min(seg(p, 0.42f, 0.2f, 0.42f, 0.8f), seg(p, 0.6f, 0.22f, 0.8f, 0.78f))))
    12 -> {
        val r = sqrt(dot2(p.x - 0.5f, p.y - 0.5f))
        min(speaker(p), max(min(ring(r - 0.17f), ring(r - 0.32f)), 0.6f - p.x))
    }
    13 -> min(speaker(p), line(min(seg(p, 0.64f, 0.38f, 0.86f, 0.62f), seg(p, 0.64f, 0.62f, 0.86f, 0.38f))))
    14 -> min(
        line(min(seg(p, 0.16f, 0.28f, 0.84f, 0.28f), min(seg(p, 0.16f, 0.5f, 0.84f, 0.5f), seg(p, 0.16f, 0.72f, 0.5f, 0.72f)))),
        tri(p, 0.62f, 0.6f, 0.62f, 0.84f, 0.84f, 0.72f),
    )
    15 -> {
        val r = sqrt(dot2(p.x - 0.5f, p.y - 0.5f))
        val a = atan2(p.y - 0.5f, p.x - 0.5f)
        val outer = 0.30f + 0.08f * ((cos(a * 8f) - 0.1f) * 3f).coerceIn(0f, 1f)
        max(r - outer, 0.12f - r)
    }
    16 -> line(min(seg(p, 0.5f, 0.2f, 0.5f, 0.8f), seg(p, 0.2f, 0.5f, 0.8f, 0.5f)))
    17 -> min(ring(circle(p, 0.5f, 0.5f, 0.32f)), line(min(seg(p, 0.5f, 0.5f, 0.5f, 0.3f), seg(p, 0.5f, 0.5f, 0.64f, 0.58f))))
    18 -> min(circle(p, 0.24f, 0.5f, 0.065f), min(circle(p, 0.5f, 0.5f, 0.065f), circle(p, 0.76f, 0.5f, 0.065f)))
    19 -> min(seg(p, 0.6f, 0.22f, 0.36f, 0.5f), seg(p, 0.36f, 0.5f, 0.6f, 0.78f)) - W * 0.6f
    20 -> glyph(19, V(1f - p.x, p.y))
    21 -> min(circle(p, 0.36f, 0.72f, 0.12f), line(min(seg(p, 0.47f, 0.72f, 0.47f, 0.18f), seg(p, 0.47f, 0.18f, 0.72f, 0.3f))))
    22 -> {
        val body = ring(seg(p, 0.5f, 0.26f, 0.5f, 0.44f) - 0.12f)
        val arc = max(ring(circle(p, 0.5f, 0.42f, 0.24f)), 0.44f - p.y)
        min(min(body, arc), line(min(seg(p, 0.5f, 0.66f, 0.5f, 0.82f), seg(p, 0.36f, 0.84f, 0.64f, 0.84f))))
    }
    23 -> line(min(seg(p, 0.22f, 0.52f, 0.42f, 0.72f), seg(p, 0.42f, 0.72f, 0.8f, 0.3f)))
    24 -> min(ring(box(p, 0.5f, 0.5f, 0.34f, 0.28f, 0.08f)), line(seg(p, 0.62f, 0.22f, 0.62f, 0.78f)))
    25 -> min(ring(circle(p, 0.5f, 0.36f, 0.14f)), max(ring(circle(p, 0.5f, 0.92f, 0.32f)), p.y - 0.84f))
    26 -> poly(p, List(8) { i -> val a = i * 0.785398f - 1.570796f; val r = if (i % 2 == 1) 0.1f else 0.36f; V(0.5f + cos(a) * r, 0.5f + sin(a) * r) }) - 0.01f
    27 -> line(min(seg(p, 0.26f, 0.26f, 0.74f, 0.74f), seg(p, 0.26f, 0.74f, 0.74f, 0.26f)))
    28 -> max(circle(p, 0.5f, 0.5f, 0.32f), -circle(p, 0.66f, 0.38f, 0.26f))
    29 -> min(
        circle(p, 0.5f, 0.5f, 0.07f),
        min(max(ring(circle(p, 0.5f, 0.5f, 0.2f)), 0.08f - abs(p.x - 0.5f)), max(ring(circle(p, 0.5f, 0.5f, 0.34f)), 0.14f - abs(p.x - 0.5f))),
    )
    30 -> min(line(min(seg(p, 0.5f, 0.18f, 0.5f, 0.6f), seg(p, 0.22f, 0.82f, 0.78f, 0.82f))), tri(p, 0.32f, 0.5f, 0.68f, 0.5f, 0.5f, 0.7f))
    else -> min(
        line(min(seg(p, 0.5f, 0.24f, 0.5f, 0.6f), min(seg(p, 0.24f, 0.52f, 0.24f, 0.8f), min(seg(p, 0.24f, 0.8f, 0.76f, 0.8f), seg(p, 0.76f, 0.8f, 0.76f, 0.52f))))),
        tri(p, 0.33f, 0.36f, 0.67f, 0.36f, 0.5f, 0.15f),
    )
}

/** White pixels whose alpha is each glyph's coverage. */
private fun drawAtlas(): ByteArray {
    val width = CELL * COLUMNS
    val height = CELL * ROWS
    val pixels = ByteArray(width * height * 4)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = (y / CELL) * COLUMNS + x / CELL
            val p = V(((x % CELL) + 0.5f) / CELL, ((y % CELL) + 0.5f) / CELL)
            val coverage = (0.5f - glyph(index, p) * CELL).coerceIn(0f, 1f)
            val out = (y * width + x) * 4
            pixels[out] = -1
            pixels[out + 1] = -1
            pixels[out + 2] = -1
            pixels[out + 3] = (coverage * 255f + 0.5f).toInt().toByte()
        }
    }
    return pixels
}
