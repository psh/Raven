package raven.sample.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import raven.compose.ui.graphics.ImageBitmap
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.TimeSource

/** The colours an object can have, in the order of the Color list. */
internal val Palette = listOf(
    floatArrayOf(1.00f, 0.55f, 0.15f),
    floatArrayOf(0.25f, 0.55f, 1.00f),
    floatArrayOf(0.30f, 0.85f, 0.45f),
    floatArrayOf(1.00f, 0.40f, 0.70f),
)

internal val ColorNames = listOf("Orange", "Blue", "Green", "Pink")

/** An object of the scene: a disc bobbing up and down. Its fields are snapshot state, edited by the properties. */
internal class SceneObject(val id: Int, name: String, x: Float, y: Float, radius: Float, speed: Float, val phase: Float, color: Int) {
    var name by mutableStateOf(name)
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)
    var radius by mutableFloatStateOf(radius)
    var speed by mutableFloatStateOf(speed)
    var color by mutableIntStateOf(color)
    var visible by mutableStateOf(true)

    fun frame() = ObjectFrame(x, y, radius, speed, phase, color, visible)
}

/** What the renderer needs of an object, copied, so it can be drawn on another thread. */
internal data class ObjectFrame(
    val x: Float,
    val y: Float,
    val radius: Float,
    val speed: Float,
    val phase: Float,
    val color: Int,
    val visible: Boolean,
)

/** A scene to draw: its objects, the selected one's index (-1 for none), and the time. */
internal data class SceneFrame(val objects: List<ObjectFrame>, val selected: Int, val time: Double)

/** The editor's state: the scene, the selection, whether it plays, and the log. */
internal class Editor {
    val objects = mutableStateListOf<SceneObject>()
    var selectedId by mutableStateOf<Int?>(null)
    var playing by mutableStateOf(true)
    val log = mutableStateListOf<String>()

    /** How the scene is rendered, and what the last frame took. */
    val rendering = RenderSettings()

    /** The profiler's samples. */
    val frameTimes = FrameTimes()

    /** The time of the animation, in seconds. */
    var time = 0.0
    private var created = 0

    val selected: SceneObject? get() = objects.firstOrNull { it.id == selectedId }

    fun frame() = SceneFrame(objects.map { it.frame() }, objects.indexOfFirst { it.id == selectedId }, time)

    fun log(line: String) {
        if (log.size < LOG_CAPACITY) log += line
    }

    fun add(name: String? = null) {
        if (objects.size == MAX_OBJECTS) {
            log("The scene is full ($MAX_OBJECTS objects)")
            return
        }
        created++
        val c = created
        val added = SceneObject(
            id = c,
            name = name ?: "Object $c",
            x = 0.2f + 0.6f * ((c * 37) % 100) / 100f,
            y = 0.25f + 0.5f * ((c * 61) % 100) / 100f,
            radius = 0.08f + 0.02f * (c % 4),
            speed = 1f + 0.25f * (c % 5),
            phase = c * 1.7f,
            color = (c - 1) % 4,
        )
        objects += added
        selectedId = added.id
        log("Added \"${added.name}\"")
    }

    fun deleteSelected() {
        val doomed = selected ?: return
        log("Deleted \"${doomed.name}\"")
        objects -= doomed
        selectedId = null
    }

    fun newScene() {
        objects.clear()
        selectedId = null
        log("New scene")
    }

    fun togglePlaying() {
        playing = !playing
        log(if (playing) "Animation started" else "Animation paused")
    }

    /** The object under ([u], [v]), 0 to 1 across the scene of [aspect] width over height, topmost first. */
    fun objectAt(u: Float, v: Float, aspect: Float): SceneObject? = objects.lastOrNull { o ->
        val dx = (u - o.x) * aspect
        val dy = v - o.y
        o.visible && dx * dx + dy * dy < o.radius * o.radius * 1.5f
    }

    companion object {
        const val MAX_OBJECTS = 64
        const val LOG_CAPACITY = 2000
    }
}

/**
 * A software renderer drawing a [SceneFrame] into an RGBA image, in bands of rows: on every core
 * through `Dispatchers.Default`, or on the calling thread. The C example uses Raven's job system
 * (`Rparallel_for`); coroutines take its place.
 */
internal class SceneRenderer(val image: ImageBitmap) {
    private var pixels = ByteArray(4)
    private var width = 1
    private var height = 1

    /** Renders [frame] at [width] by [height] pixels, then shows it in [image]; returns what it took. */
    suspend fun render(frame: SceneFrame, width: Int, height: Int, parallel: Boolean): String {
        if (width < 2 || height < 2) return ""
        if (width != this.width || height != this.height) {
            pixels = ByteArray(width * height * 4)
            this.width = width
            this.height = height
        }
        val start = TimeSource.Monotonic.markNow()
        val target = pixels
        if (parallel) {
            withContext(Dispatchers.Default) {
                coroutineScope { (0 until height step BAND).map { row -> async { renderRows(frame, target, width, height, row, minOf(row + BAND, height)) } }.awaitAll() }
            }
        } else {
            renderRows(frame, target, width, height, 0, height)
        }
        val millis = start.elapsedNow().inWholeMicroseconds / 1000.0
        image.update(width, height, target)
        val ms = (millis * 100).toLong() / 100.0
        return if (parallel) "$width x $height pixels in $ms ms on every core" else "$width x $height pixels in $ms ms on 1 thread"
    }

    private companion object {
        const val BAND = 8
    }
}

/** Fills rows [from] until [until] of [out]: a gradient with a grid, and shaded discs; the selected one gets a ring. */
internal fun renderRows(frame: SceneFrame, out: ByteArray, width: Int, height: Int, from: Int, until: Int) {
    val aspect = width.toFloat() / height
    for (row in from until until) {
        val v = (row + 0.5f) / height
        for (column in 0 until width) {
            val u = (column + 0.5f) / width
            var r = 0.07f + 0.05f * v
            var g = 0.08f + 0.06f * v
            var b = 0.11f + 0.10f * v
            val gridU = u * aspect * 10f
            val gridV = v * 10f
            if (gridU - floor(gridU) < 0.03f || gridV - floor(gridV) < 0.03f) {
                r += 0.04f
                g += 0.04f
                b += 0.05f
            }
            frame.objects.forEachIndexed { i, o ->
                if (!o.visible) return@forEachIndexed
                val dx = (u - o.x) * aspect
                val dy = v - (o.y + 0.06f * sin(frame.time.toFloat() * o.speed + o.phase))
                val distance = sqrt(dx * dx + dy * dy)
                if (distance > o.radius + 0.01f) return@forEachIndexed
                var shade = 1f - 0.55f * (distance / o.radius) * (distance / o.radius)
                val edge = ((o.radius - distance) * height * 0.5f).coerceIn(0f, 1f)
                if (i == frame.selected && distance > o.radius - 0.012f) shade = 1.6f
                val c = Palette[o.color]
                r += (c[0] * shade - r) * edge
                g += (c[1] * shade - g) * edge
                b += (c[2] * shade - b) * edge
            }
            val at = (row * width + column) * 4
            out[at] = (255f * r.coerceAtMost(1f)).toInt().toByte()
            out[at + 1] = (255f * g.coerceAtMost(1f)).toInt().toByte()
            out[at + 2] = (255f * b.coerceAtMost(1f)).toInt().toByte()
            out[at + 3] = -1
        }
    }
}
