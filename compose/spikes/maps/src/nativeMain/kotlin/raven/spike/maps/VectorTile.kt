package raven.spike.maps

// A Mapbox Vector Tile 2.1 (https://github.com/mapbox/vector-tile-spec/blob/master/2.1/README.md):
// protobuf layers of features, each with tags into the layer's keys and values, and a stream of
// MoveTo, LineTo and ClosePath commands in tile coordinates (0 to the layer's extent, often 4096).

enum class GeometryType { Unknown, Point, LineString, Polygon }

class Feature(
    val type: GeometryType,
    val properties: Map<String, Any?>,
    /** The raw command stream: decoded on demand by [rings]. */
    private val commands: IntArray,
) {
    /**
     * The geometry as rings of x, y pairs in tile coordinates: the points of a point feature, the
     * lines of a line feature, the rings of a polygon feature (exteriors and holes, by winding).
     */
    fun rings(): List<FloatArray> = decodeGeometry(commands)
}

class Layer(val name: String, val extent: Int, val features: List<Feature>)

/** Decodes a whole tile. */
fun decodeTile(bytes: ByteArray): List<Layer> {
    val layers = ArrayList<Layer>()
    val reader = ProtoReader(bytes, 0, bytes.size)
    while (reader.hasMore()) {
        val (field, wire) = reader.tag()
        if (field == 3 && wire == 2) layers += decodeLayer(reader.message()) else reader.skip(wire)
    }
    return layers
}

private fun decodeLayer(reader: ProtoReader): Layer {
    var name = ""
    var extent = 4096
    val keys = ArrayList<String>()
    val values = ArrayList<Any?>()
    val features = ArrayList<ProtoReader>()
    while (reader.hasMore()) {
        val (field, wire) = reader.tag()
        when (field) {
            1 -> name = reader.string()
            2 -> features += reader.message()
            3 -> keys += reader.string()
            4 -> values += decodeValue(reader.message())
            5 -> extent = reader.varint().toInt()
            else -> reader.skip(wire)
        }
    }
    // The features' tags point into keys and values, which may come after them.
    return Layer(name, extent, features.map { decodeFeature(it, keys, values) })
}

private fun decodeValue(reader: ProtoReader): Any? {
    var value: Any? = null
    while (reader.hasMore()) {
        val (field, wire) = reader.tag()
        value = when (field) {
            1 -> reader.string()
            2 -> Float.fromBits(reader.fixed32()).toDouble()
            3 -> Double.fromBits(reader.fixed64())
            4, 5 -> reader.varint().toDouble()
            6 -> zigzag64(reader.varint()).toDouble()
            7 -> reader.varint() != 0L
            else -> value.also { reader.skip(wire) }
        }
    }
    return value
}

private fun decodeFeature(reader: ProtoReader, keys: List<String>, values: List<Any?>): Feature {
    var type = GeometryType.Unknown
    val properties = HashMap<String, Any?>()
    var commands = IntArray(0)
    while (reader.hasMore()) {
        val (field, wire) = reader.tag()
        when (field) {
            2 -> {
                val tags = reader.packed()
                for (i in 0 until tags.size - 1 step 2) properties[keys[tags[i]]] = values[tags[i + 1]]
            }
            3 -> type = GeometryType.entries.getOrElse(reader.varint().toInt()) { GeometryType.Unknown }
            4 -> commands = reader.packed()
            else -> reader.skip(wire)
        }
    }
    return Feature(type, properties, commands)
}

/** The command stream as rings of x, y pairs; a ClosePath leaves the ring as it is (no repeated point). */
fun decodeGeometry(commands: IntArray): List<FloatArray> {
    val rings = ArrayList<FloatArray>()
    var ring = FloatList()
    var x = 0
    var y = 0
    var i = 0
    while (i < commands.size) {
        val command = commands[i] and 7
        val count = commands[i] ushr 3
        i++
        when (command) {
            1, 2 -> repeat(count) {
                if (command == 1 && ring.size > 0) {
                    rings += ring.toArray()
                    ring = FloatList()
                }
                x += zigzag32(commands[i])
                y += zigzag32(commands[i + 1])
                i += 2
                ring.add(x.toFloat(), y.toFloat())
            }
            7 -> {}
            else -> error("vector tile: unknown command $command")
        }
    }
    if (ring.size > 0) rings += ring.toArray()
    return rings
}

/** Twice the signed area of a ring of x, y pairs: positive for an exterior ring in tile coordinates (y down). */
fun ringArea(ring: FloatArray): Double {
    var sum = 0.0
    var j = ring.size - 2
    for (i in 0 until ring.size step 2) {
        sum += ring[j].toDouble() * ring[i + 1] - ring[i].toDouble() * ring[j + 1]
        j = i
    }
    return sum
}

internal fun zigzag32(n: Int): Int = (n ushr 1) xor -(n and 1)

internal fun zigzag64(n: Long): Long = (n ushr 1) xor -(n and 1)

internal class FloatList {
    private var data = FloatArray(64)
    var size = 0
        private set

    fun add(x: Float, y: Float) {
        if (size + 2 > data.size) data = data.copyOf(data.size * 2)
        data[size++] = x
        data[size++] = y
    }

    fun toArray(): FloatArray = data.copyOf(size)
}

/** A protobuf reader over [bytes] from [pos] to [end]: varints, fixed numbers, strings and sub-messages. */
internal class ProtoReader(private val bytes: ByteArray, private var pos: Int, private val end: Int) {
    fun hasMore(): Boolean = pos < end

    fun tag(): Pair<Int, Int> {
        val key = varint().toInt()
        return (key ushr 3) to (key and 7)
    }

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = bytes[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b < 0x80) return result
            shift += 7
        }
    }

    fun fixed32(): Int {
        var v = 0
        for (i in 0 until 4) v = v or ((bytes[pos + i].toInt() and 0xFF) shl (8 * i))
        pos += 4
        return v
    }

    fun fixed64(): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((bytes[pos + i].toLong() and 0xFF) shl (8 * i))
        pos += 8
        return v
    }

    fun message(): ProtoReader {
        val length = varint().toInt()
        return ProtoReader(bytes, pos, pos + length).also { pos += length }
    }

    fun string(): String {
        val length = varint().toInt()
        return bytes.decodeToString(pos, pos + length).also { pos += length }
    }

    /** A packed repeated varint field, as ints. */
    fun packed(): IntArray {
        val inner = message()
        val out = ArrayList<Int>()
        while (inner.hasMore()) out += inner.varint().toInt()
        return out.toIntArray()
    }

    fun skip(wire: Int) {
        when (wire) {
            0 -> varint()
            1 -> pos += 8
            2 -> pos += varint().toInt()
            5 -> pos += 4
            else -> error("protobuf: wire type $wire")
        }
    }
}
