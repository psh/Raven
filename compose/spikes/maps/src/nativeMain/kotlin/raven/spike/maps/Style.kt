package raven.spike.maps

import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.StrokeCap
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

// MapLibre's style JSON (https://maplibre.org/maplibre-style-spec/), the part fill and line layers
// need: a layer's source layer, zoom range, filter and paint, and the expressions they are written
// in. A fill extrusion is drawn as a flat fill, as from straight above. Labels and icons are
// Labels.kt's, dashes and line gaps LineStyle.kt's. Left out: patterns, `line-offset`, and
// data-driven paint (a paint value that reads a feature's properties).

class StyleLayer(
    val id: String,
    val type: String,
    val sourceLayer: String?,
    val minZoom: Double,
    val maxZoom: Double,
    val filter: Any?,
    val paint: Map<String, Any?>,
    val layout: Map<String, Any?>,
) {
    val visible: Boolean get() = layout["visibility"] != "none"

    /** A fill, or a 3D extrusion, which the map, seen straight from above, draws as its fill. */
    val isFill: Boolean get() = type == "fill" || type == "fill-extrusion"

    /**
     * Whether a fill draws its outline over its edges, which the mesh leaves aliased: `fill-antialias`,
     * on by default. A flat extrusion has one too.
     */
    val antialias: Boolean get() = isFill && paint["fill-antialias"] != false

    /** The colour of a fill's outline at [zoom]: `fill-outline-color`, else the fill's own. */
    fun outlineColor(zoom: Double): Color? {
        if (type != "fill" || "fill-outline-color" !in paint) return drawColor(zoom)
        return color("fill-outline-color", zoom, "fill-opacity")
    }

    /** The colour and opacity a fill or a line draws in at [zoom]. */
    fun drawColor(zoom: Double): Color? = when (type) {
        "fill" -> color("fill-color", zoom, "fill-opacity")
        // Its roofs, opaque: MapLibre's depth test keeps overlapping parts from showing through
        // each other, which a flat, translucent fill can't.
        "fill-extrusion" -> parseColor(paint("fill-extrusion-color", zoom))
        else -> color("line-color", zoom, "line-opacity")
    }

    /** Where the layer's symbols go at [zoom]: "point", or "line" for along its lines (an expression for road shields). */
    fun placementAt(zoom: Double): String = layout("symbol-placement", zoom) as? String ?: "point"

    /** How the layer's lines end at [zoom]: `line-cap`, butt by default. */
    fun lineCap(zoom: Double): StrokeCap = when (layout("line-cap", zoom)) {
        "round" -> StrokeCap.Round
        "square" -> StrokeCap.Square
        else -> StrokeCap.Butt
    }

    /** A layout property at [zoom], for every feature alike. */
    fun layout(name: String, zoom: Double): Any? = layout[name]?.let { evaluate(it, EvaluationContext(zoom)) }

    /** A paint property at [zoom], for every feature alike. */
    fun paint(name: String, zoom: Double): Any? = paint[name]?.let { evaluate(it, EvaluationContext(zoom)) }

    fun color(name: String, zoom: Double, opacityName: String): Color? {
        val color = parseColor(paint(name, zoom)) ?: return null
        val opacity = (paint(opacityName, zoom) as? Double ?: 1.0).toFloat()
        return Color(color.red, color.green, color.blue, (color.alpha * opacity).coerceIn(0f, 1f))
    }

    fun number(name: String, zoom: Double, default: Double): Double = paint(name, zoom) as? Double ?: default
}

class Style(val background: Color, val layers: List<StyleLayer>, val symbols: List<StyleLayer> = emptyList()) {
    companion object {
        /** The background, and the fill and line layers of [json] that draw from [source]; the others are left out. */
        fun parse(json: String, source: String = "openmaptiles"): Style {
            @Suppress("UNCHECKED_CAST")
            val root = parseJson(json) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val all = (root["layers"] as List<Map<String, Any?>>).map { layer ->
                @Suppress("UNCHECKED_CAST")
                StyleLayer(
                    id = layer["id"] as String,
                    type = layer["type"] as String,
                    sourceLayer = layer["source-layer"] as String?,
                    minZoom = layer["minzoom"] as? Double ?: 0.0,
                    maxZoom = layer["maxzoom"] as? Double ?: 24.0,
                    filter = layer["filter"],
                    paint = layer["paint"] as? Map<String, Any?> ?: emptyMap(),
                    layout = layer["layout"] as? Map<String, Any?> ?: emptyMap(),
                )
            }
            val background = all.firstOrNull { it.type == "background" }?.color("background-color", 14.0, "background-opacity") ?: Color.White
            val drawn = all.filter {
                it.visible && layerSource(root, it, source) &&
                    ((it.type == "fill" && "fill-pattern" !in it.paint) || it.type == "fill-extrusion" || it.type == "line")
            }
            // Labels and icons, at points and along lines.
            val symbols = all.filter {
                it.visible && layerSource(root, it, source) && it.type == "symbol" &&
                    ("text-field" in it.layout || "icon-image" in it.layout)
            }
            return Style(background, drawn, symbols)
        }

        private fun layerSource(root: Map<String, Any?>, layer: StyleLayer, source: String): Boolean {
            @Suppress("UNCHECKED_CAST")
            val raw = (root["layers"] as List<Map<String, Any?>>).first { it["id"] == layer.id }
            return raw["source"] == source
        }
    }
}

/** What an expression reads: the zoom, and the feature's properties and geometry type. */
class EvaluationContext(
    val zoom: Double,
    val properties: Map<String, Any?> = emptyMap(),
    val geometryType: GeometryType = GeometryType.Unknown,
)

/** Whether [filter] keeps the feature; no filter keeps every one. */
fun accepts(filter: Any?, context: EvaluationContext): Boolean = filter == null || evaluate(filter, context) == true

/** Evaluates a style expression: a literal, or a list starting with its operator. */
fun evaluate(expression: Any?, context: EvaluationContext): Any? {
    if (expression !is List<*>) return expression
    if (expression.isEmpty() || expression[0] !is String) return expression
    val op = expression[0] as String
    fun arg(i: Int) = evaluate(expression[i], context)
    return when (op) {
        "literal" -> expression[1]
        "get" -> context.properties[arg(1) as String]
        "has" -> (arg(1) as String) in context.properties
        "zoom" -> context.zoom
        "geometry-type" -> when (context.geometryType) {
            GeometryType.Point -> "Point"
            GeometryType.LineString -> "LineString"
            GeometryType.Polygon -> "Polygon"
            GeometryType.Unknown -> "Unknown"
        }
        "typeof" -> when (arg(1)) {
            null -> "null"
            is String -> "string"
            is Double -> "number"
            is Boolean -> "boolean"
            is List<*> -> "array"
            else -> "object"
        }
        "!" -> arg(1) != true
        "all" -> (1 until expression.size).all { arg(it) == true }
        "any" -> (1 until expression.size).any { arg(it) == true }
        "==" -> same(arg(1), arg(2))
        "!=" -> !same(arg(1), arg(2))
        "<", "<=", ">", ">=" -> {
            val a = arg(1)
            val b = arg(2)
            val c = when {
                a is Double && b is Double -> a.compareTo(b)
                a is String && b is String -> a.compareTo(b)
                else -> return false
            }
            when (op) {
                "<" -> c < 0
                "<=" -> c <= 0
                ">" -> c > 0
                else -> c >= 0
            }
        }
        "in" -> when (val haystack = arg(2)) {
            is List<*> -> haystack.any { same(it, arg(1)) }
            is String -> (arg(1) as? String)?.let { it in haystack } ?: false
            else -> false
        }
        "match" -> {
            val input = arg(1)
            var i = 2
            while (i + 1 < expression.size) {
                val labels = expression[i]
                val hit = if (labels is List<*>) labels.any { same(it, input) } else same(labels, input)
                if (hit) return evaluate(expression[i + 1], context)
                i += 2
            }
            evaluate(expression.last(), context)
        }
        "case" -> {
            var i = 1
            while (i + 1 < expression.size) {
                if (arg(i) == true) return arg(i + 1)
                i += 2
            }
            arg(expression.size - 1)
        }
        "coalesce" -> (1 until expression.size).firstNotNullOfOrNull { arg(it) }
        "concat" -> (1 until expression.size).joinToString("") { arg(it)?.let(::text).orEmpty() }
        "upcase" -> (arg(1) as? String)?.uppercase()
        "downcase" -> (arg(1) as? String)?.lowercase()
        "interpolate" -> interpolate(expression, context)
        "step" -> {
            val input = arg(1) as Double
            var result = arg(2)
            var i = 3
            while (i + 1 < expression.size && input >= (expression[i] as Double)) {
                result = arg(i + 1)
                i += 2
            }
            result
        }
        "to-number" -> when (val v = arg(1)) {
            is Double -> v
            is String -> v.toDoubleOrNull() ?: 0.0
            is Boolean -> if (v) 1.0 else 0.0
            else -> 0.0
        }
        "to-string" -> arg(1)?.let(::text) ?: ""
        else -> null
    }
}

/** A value as text: a whole number without its ".0". */
fun text(value: Any): String = if (value is Double && value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()

/** Equality as the style spec has it: numbers compare as numbers, whether a tile stored 1 or 1.0. */
private fun same(a: Any?, b: Any?): Boolean = a == b || (a is Number && b is Number && a.toDouble() == b.toDouble())

private fun interpolate(expression: List<*>, context: EvaluationContext): Any? {
    val curve = expression[1] as List<*>
    val input = evaluate(expression[2], context) as Double
    val stops = (3 until expression.size step 2).map { (expression[it] as Double) to expression[it + 1] }
    if (input <= stops.first().first) return evaluate(stops.first().second, context)
    if (input >= stops.last().first) return evaluate(stops.last().second, context)
    val upper = stops.indexOfFirst { it.first > input }
    val (z0, v0) = stops[upper - 1]
    val (z1, v1) = stops[upper]
    val base = if (curve[0] == "exponential") curve[1] as Double else 1.0
    val t = if (abs(base - 1.0) < 1e-9) (input - z0) / (z1 - z0) else (base.pow(input - z0) - 1) / (base.pow(z1 - z0) - 1)
    val a = evaluate(v0, context)
    val b = evaluate(v1, context)
    return when {
        a is Double && b is Double -> a + (b - a) * t
        else -> {
            val ca = parseColor(a) ?: return a
            val cb = parseColor(b) ?: return a
            val f = t.toFloat()
            Color(ca.red + (cb.red - ca.red) * f, ca.green + (cb.green - ca.green) * f, ca.blue + (cb.blue - ca.blue) * f, ca.alpha + (cb.alpha - ca.alpha) * f)
        }
    }
}

/** A CSS colour as the style spec writes it: #rgb, #rrggbb, rgb(), rgba(), hsl() and hsla(). */
fun parseColor(value: Any?): Color? {
    if (value is Color) return value
    val text = (value as? String)?.trim()?.lowercase() ?: return null
    if (text.startsWith("#")) {
        val hex = text.substring(1)
        val full = when (hex.length) {
            3, 4 -> hex.map { "$it$it" }.joinToString("")
            else -> hex
        }
        val v = full.toLongOrNull(16) ?: return null
        return if (full.length == 8) {
            Color(((v shr 24) and 0xFF) / 255f, ((v shr 16) and 0xFF) / 255f, ((v shr 8) and 0xFF) / 255f, (v and 0xFF) / 255f)
        } else {
            Color(((v shr 16) and 0xFF) / 255f, ((v shr 8) and 0xFF) / 255f, (v and 0xFF) / 255f)
        }
    }
    val open = text.indexOf('(')
    if (open < 0 || !text.endsWith(")")) return null
    val name = text.substring(0, open)
    val parts = text.substring(open + 1, text.length - 1).split(',').map { it.trim() }
    fun number(s: String): Float = if (s.endsWith("%")) s.dropLast(1).toFloat() / 100f else s.toFloat()
    val alpha = parts.getOrNull(3)?.let { number(it) } ?: 1f
    return when (name) {
        "rgb", "rgba" -> Color(number(parts[0]) / 255f, number(parts[1]) / 255f, number(parts[2]) / 255f, alpha)
        "hsl", "hsla" -> hsl(parts[0].toFloat(), number(parts[1]), number(parts[2]), alpha)
        else -> null
    }
}

private fun hsl(h: Float, s: Float, l: Float, alpha: Float): Color {
    fun f(n: Int): Float {
        val k = (n + h / 30f) % 12f
        val a = s * minOf(l, 1f - l)
        return l - a * maxOf(-1f, minOf(k - 3f, 9f - k, 1f))
    }
    return Color(f(0).coerceIn(0f, 1f), f(8).coerceIn(0f, 1f), f(4).coerceIn(0f, 1f), alpha)
}

/** A colour as `#rrggbbaa`, for tests and logs. */
fun Color.hex(): String = listOf(red, green, blue, alpha).joinToString("", "#") { (it * 255).roundToInt().toString(16).padStart(2, '0') }
