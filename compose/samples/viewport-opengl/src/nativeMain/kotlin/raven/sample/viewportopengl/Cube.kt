package raven.sample.viewportopengl

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The C example's six face colours, in its order. */
val faceColors: List<FloatArray> = listOf(
    floatArrayOf(1.0f, 0.35f, 0.2f), floatArrayOf(0.25f, 0.85f, 0.4f), floatArrayOf(0.25f, 0.5f, 1.0f),
    floatArrayOf(1.0f, 0.8f, 0.2f), floatArrayOf(0.8f, 0.3f, 0.9f), floatArrayOf(0.2f, 0.85f, 0.9f),
)

/** The background, the C example's glClearColor. */
val clearColor: FloatArray = floatArrayOf(0.05f, 0.06f, 0.10f)

/** The C example's six quads, each corner as x, y, z, in its order. */
private val faces: Array<Array<IntArray>> = arrayOf(
    arrayOf(intArrayOf(-1, -1, 1), intArrayOf(1, -1, 1), intArrayOf(1, 1, 1), intArrayOf(-1, 1, 1)),
    arrayOf(intArrayOf(1, -1, -1), intArrayOf(-1, -1, -1), intArrayOf(-1, 1, -1), intArrayOf(1, 1, -1)),
    arrayOf(intArrayOf(-1, 1, 1), intArrayOf(1, 1, 1), intArrayOf(1, 1, -1), intArrayOf(-1, 1, -1)),
    arrayOf(intArrayOf(-1, -1, -1), intArrayOf(1, -1, -1), intArrayOf(1, -1, 1), intArrayOf(-1, -1, 1)),
    arrayOf(intArrayOf(1, -1, 1), intArrayOf(1, -1, -1), intArrayOf(1, 1, -1), intArrayOf(1, 1, 1)),
    arrayOf(intArrayOf(-1, -1, -1), intArrayOf(-1, -1, 1), intArrayOf(-1, 1, 1), intArrayOf(-1, 1, -1)),
)

/**
 * The cube for a vertex buffer, each vertex its position then its face's colour (six floats): first
 * the faces as triangles, each quad split in two, as core GL has no GL_QUADS; then each face's four
 * edges as lines, for the wireframe, which GL_LINE on the triangles would cross with a diagonal the
 * C example's quads don't have.
 */
fun cubeVertices(): FloatArray {
    val out = ArrayList<Float>((CUBE_TRIANGLE_VERTICES + CUBE_EDGE_VERTICES) * 6)
    fun add(face: Int, corners: IntArray) {
        for (corner in corners) {
            faces[face][corner].forEach { out += it.toFloat() }
            faceColors[face].forEach { out += it }
        }
    }
    for (face in faces.indices) add(face, intArrayOf(0, 1, 2, 0, 2, 3))
    for (face in faces.indices) add(face, intArrayOf(0, 1, 1, 2, 2, 3, 3, 0))
    return out.toFloatArray()
}

/** The faces' vertices in [cubeVertices], first. */
const val CUBE_TRIANGLE_VERTICES: Int = 36

/** The edges' vertices in [cubeVertices], after the faces'. */
const val CUBE_EDGE_VERTICES: Int = 48

/**
 * The C example's matrices as one, column-major for glUniformMatrix4fv: glFrustum with its planes,
 * then glTranslatef(0, 0, -5), then glRotatef(angle * 40, 0.3, 1, 0.2).
 */
fun cubeTransform(width: Int, height: Int, angle: Float): FloatArray {
    val aspect = width.toFloat() / height
    val projection = frustum(-0.1f * aspect, 0.1f * aspect, -0.1f, 0.1f, 0.2f, 20f)
    val translation = identity().also { it[14] = -5f }
    return projection * translation * rotation(angle * 40f, 0.3f, 1f, 0.2f)
}

private fun identity() = FloatArray(16).also { for (i in 0 until 4) it[i * 5] = 1f }

private fun frustum(l: Float, r: Float, b: Float, t: Float, n: Float, f: Float) = FloatArray(16).also {
    it[0] = 2 * n / (r - l)
    it[5] = 2 * n / (t - b)
    it[8] = (r + l) / (r - l)
    it[9] = (t + b) / (t - b)
    it[10] = -(f + n) / (f - n)
    it[11] = -1f
    it[14] = -2 * f * n / (f - n)
}

/** glRotatef's matrix: [degrees] about the axis, which it normalises. */
private fun rotation(degrees: Float, x: Float, y: Float, z: Float): FloatArray {
    val length = sqrt(x * x + y * y + z * z)
    val (ax, ay, az) = Triple(x / length, y / length, z / length)
    val radians = degrees * PI.toFloat() / 180f
    val c = cos(radians)
    val s = sin(radians)
    val k = 1 - c
    return floatArrayOf(
        ax * ax * k + c, ay * ax * k + az * s, ax * az * k - ay * s, 0f,
        ax * ay * k - az * s, ay * ay * k + c, ay * az * k + ax * s, 0f,
        ax * az * k + ay * s, ay * az * k - ax * s, az * az * k + c, 0f,
        0f, 0f, 0f, 1f,
    )
}

/** Column-major product. */
private operator fun FloatArray.times(other: FloatArray): FloatArray = FloatArray(16).also { out ->
    for (column in 0 until 4) for (row in 0 until 4) {
        var sum = 0f
        for (i in 0 until 4) sum += this[i * 4 + row] * other[column * 4 + i]
        out[column * 4 + row] = sum
    }
}
