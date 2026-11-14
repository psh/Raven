package raven.sample.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import raven.cinterop.Rimage_decode
import raven.cinterop.Rpixels_free
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.FilterQuality
import raven.compose.ui.graphics.ImageBitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// The albums, the tracks and the playlists, all made up with the C example's generator, so they
// are its names, and the covers: covers/01.jpg, 02.jpg... (or .png) when there are files, else
// drawn here. Each cover gives its album two colours, its most vivid ones: the accent while it
// plays, and the background's.

const val ALBUMS = 24
const val PLAYLISTS = 6

class Album(
    val index: Int,
    val title: String,
    val artist: String,
    val genre: String,
    val year: Int,
    val firstTrack: Int,
    val trackCount: Int,
    /** The colour the album is known by: the accent while it plays. */
    val color: FloatArray,
    val color2: FloatArray,
    val cover: ImageBitmap,
)

class Track(val index: Int, val title: String, val album: Int, val seconds: Int, liked: Boolean) {
    var liked by mutableStateOf(liked)
}

class Playlist(val title: String, val description: String, val tracks: List<Int>, val cover: Int)

/** "3:07" */
fun formatTime(seconds: Int): String {
    val s = max(seconds, 0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

private val Adjectives = listOf(
    "Velvet", "Neon", "Paper", "Glass", "Midnight", "Golden", "Silent", "Electric", "Hollow", "Crimson",
    "Northern", "Lucid", "Static", "Wild", "Slow", "Distant", "Burning", "Quiet", "Endless", "Broken",
    "Little", "Blue", "Copper", "Stray", "Silver", "Tender", "Restless", "Faded", "Bright", "Lonely",
)
private val Nouns = listOf(
    "Harbor", "Satellites", "Weather", "Gardens", "Echoes", "Highways", "Motel", "Summer", "Tides", "Signals",
    "Lanterns", "Cathedral", "Rivers", "Daydreams", "Machines", "Orchards", "Avenue", "Fever", "Horizon", "Comets",
    "Heartbeat", "Mirrors", "Wolves", "Arcade", "Paradise", "Skyline", "Strangers", "Waves", "Embers", "Ghosts",
)
private val Places = listOf(
    "Tokyo", "the Rain", "Lisbon", "the Dark", "July", "Berlin", "the Valley", "Room 9", "the Desert", "Brooklyn",
    "Marseille", "the Morning", "Seoul", "the Deep End", "Nowhere",
)
private val Verbs = listOf("Call", "Hold", "Wake", "Take", "Find", "Leave", "Save", "Show", "Keep", "Tell")
private val Adverbs = listOf("Slowly", "Again", "Tonight", "Home", "Closer", "Gently", "Back", "Forever")
private val FirstNames = listOf("Mira", "Jonah", "Elio", "Sasha", "Noor", "Theo", "Lena", "Kai", "Ines", "Felix", "Ava", "Remy", "Yuna", "Otis", "Zadie", "Milo")
private val LastNames = listOf("Solen", "Hart", "Vale", "Okafor", "Lindqvist", "Moreau", "Kane", "Rivers", "Sato", "Bellamy", "Quinn", "Delacroix", "Ash", "Novak")
val Genres = listOf("Pop", "Hip-Hop", "Rock", "Indie", "R&B", "Electronic", "Soul", "Folk", "Jazz", "Chill")
private val GenreColors = listOf(0xE8115B, 0xBA5D07, 0xE91429, 0x608108, 0x8D67AB, 0x0D73EC, 0xDC148C, 0x477D95, 0x1E3264, 0x27856A)

fun genreColor(index: Int): Color = Color(0xFF000000 or GenreColors[index].toLong())

/** The C example's generator: the same seed, so the same names. */
private class Names {
    private var seed = 20261101u

    private fun next(): UInt {
        seed = seed * 1664525u + 1013904223u
        return seed shr 8
    }

    fun pick(count: Int): Int = (next() % count.toUInt()).toInt()

    fun <T> pick(list: List<T>): T = list[pick(list.size)]

    fun artist(): String = when (pick(4)) {
        0 -> "The ${pick(Adjectives)} ${pick(Nouns)}"
        1 -> "${pick(Adjectives)} ${pick(Nouns)}"
        else -> "${pick(FirstNames)} ${pick(LastNames)}"
    }

    fun albumTitle(): String = when (pick(5)) {
        0 -> pick(Nouns)
        1 -> "${pick(Nouns)} in ${pick(Places)}"
        2 -> "The ${pick(Adjectives)} ${pick(Nouns)}"
        else -> "${pick(Adjectives)} ${pick(Nouns)}"
    }

    fun trackTitle(): String = when (pick(7)) {
        0 -> "${pick(Adjectives)} ${pick(Nouns)}"
        1 -> "${pick(Nouns)} in ${pick(Places)}"
        2 -> "${pick(Verbs)} Me ${pick(Adverbs)}"
        3 -> "The ${pick(Nouns)}"
        4 -> pick(Nouns)
        5 -> "${pick(Adjectives)} ${pick(Nouns)} (Interlude)"
        else -> "${pick(Places)}, ${pick(Adverbs)}"
    }
}

class Library(val albums: List<Album>, val tracks: List<Track>, private val playlists: List<Playlist>) {
    /** The liked songs are a playlist made when it is needed. */
    fun playlist(index: Int): Playlist =
        if (index == 0) playlists[0].let { Playlist(it.title, it.description, tracks.filter { t -> t.liked }.map { t -> t.index }.take(40), it.cover) } else playlists[index]

    fun albumTracks(album: Int): List<Int> = albums[album].let { a -> List(a.trackCount) { a.firstTrack + it } }

    fun albumSeconds(album: Int): Int = albumTracks(album).sumOf { tracks[it].seconds }

    companion object {
        /** Makes the library up, with the covers of [directory]/covers, or drawn ones. Raven must run. */
        fun create(directory: String): Library {
            val names = Names()
            val albums = mutableListOf<Album>()
            val tracks = mutableListOf<Track>()
            for (a in 0 until ALBUMS) {
                val genre = names.pick(Genres.size)
                val title = names.albumTitle()
                val artist = names.artist()
                // In the order C evaluates the expression.
                val back = names.pick(40)
                val old = names.pick(3) == 0
                val recent = names.pick(6)
                val year = 2026 - back * (if (old) 1 else 0) - recent
                val first = tracks.size
                val count = 6 + names.pick(7)
                repeat(count) {
                    val trackTitle = names.trackTitle()
                    val seconds = 128 + names.pick(200)
                    names.pick(4000)
                    names.pick(900)
                    tracks += Track(tracks.size, trackTitle, a, seconds, names.pick(6) == 0)
                }
                val color = FloatArray(3)
                val color2 = FloatArray(3)
                val cover = loadCover(directory, a, genre, color, color2)
                albums += Album(a, title, artist, Genres[genre], year, first, count, color, color2, cover)
            }
            val titles = listOf("Liked songs", "Late night drive", "Focus flow", "Sunday morning", "Heavy rotation", "Fresh finds")
            val descriptions = listOf(
                "The songs you marked with a heart.",
                "Long roads, city lights and the windows down.",
                "Quiet songs to keep your head down.",
                "Slow coffee, open windows, nowhere to be.",
                "What you have been playing the most lately.",
                "New songs picked for you, every Friday.",
            )
            val playlists = List(PLAYLISTS) { p ->
                val cover = if (p == 0) 15 else (p * 7 + 3) % ALBUMS
                val list = if (p == 0) emptyList() else List(minOf(14 + p * 3, 40)) { names.pick(tracks.size) }
                Playlist(titles[p], descriptions[p], list, cover)
            }
            return Library(albums, tracks, playlists)
        }
    }
}

// ---- colours ----

internal fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
    val max = max(r, max(g, b))
    val min = min(r, min(g, b))
    val d = max - min
    val s = if (max > 0f) d / max else 0f
    val h = when {
        d <= 0f -> 0f
        max == r -> ((g - b) / d + 6f).mod(6f) / 6f
        max == g -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    }
    return floatArrayOf(h, s, max)
}

internal fun hsvToRgb(h: Float, s: Float, v: Float, out: FloatArray) {
    val k = floatArrayOf(5f, 3f, 1f)
    for (i in 0 until 3) {
        val x = (k[i] + h * 6f).mod(6f)
        out[i] = v - v * s * max(0f, min(min(x, 4f - x), 1f))
    }
}

/** A colour that reads well as an accent on a dark window: vivid enough, light enough. */
private fun makeAccent(input: FloatArray, out: FloatArray) {
    val (h, s0, v0) = rgbToHsv(input[0], input[1], input[2]).let { Triple(it[0], it[1], it[2]) }
    hsvToRgb(h, s0.coerceIn(0.45f, 0.85f), v0.coerceIn(0.78f, 0.98f), out)
}

private fun genreRgb(genre: Int): FloatArray = GenreColors[genre].let { floatArrayOf(((it shr 16) and 255) / 255f, ((it shr 8) and 255) / 255f, (it and 255) / 255f) }

/** The two most vivid colours of a picture: twelve families of hue, weighted by how vivid each pixel is. */
private fun coverColors(pixels: ByteArray, width: Int, height: Int, genre: Int, color: FloatArray, color2: FloatArray) {
    val weight = FloatArray(12)
    val sum = Array(12) { FloatArray(3) }
    val step = max(width / 48, 1)
    for (y in 0 until height step step) {
        for (x in 0 until width step step) {
            val p = (y * width + x) * 4
            val r = (pixels[p].toInt() and 255) / 255f
            val g = (pixels[p + 1].toInt() and 255) / 255f
            val b = (pixels[p + 2].toInt() and 255) / 255f
            val (h, s, v) = rgbToHsv(r, g, b).let { Triple(it[0], it[1], it[2]) }
            if (v < 0.18f) continue
            val w = s * s * v
            val bin = (h * 12f).toInt() % 12
            weight[bin] += w
            sum[bin][0] += r * w
            sum[bin][1] += g * w
            sum[bin][2] += b * w
        }
    }
    val first = weight.indices.maxBy { weight[it] }
    var second = -1
    for (i in 0 until 12) {
        var distance = abs(i - first)
        if (distance > 6) distance = 12 - distance
        if (distance >= 2 && (second < 0 || weight[i] > weight[second])) second = i
    }
    if (weight[first] < 2f) {
        // A picture with almost no colour (black and white): the colour of its genre.
        makeAccent(genreRgb(genre), color)
        for (i in 0 until 3) color2[i] = color[i] * 0.35f
        return
    }
    makeAccent(FloatArray(3) { sum[first][it] / weight[first] }, color)
    if (second >= 0 && weight[second] > weight[first] * 0.12f) {
        makeAccent(FloatArray(3) { sum[second][it] / weight[second] }, color2)
    } else {
        val hsv = rgbToHsv(color[0], color[1], color[2])
        hsvToRgb((hsv[0] + 0.12f).mod(1f), hsv[1], hsv[2] * 0.7f, color2)
    }
}

// ---- the covers drawn here ----

private const val DRAWN = 256

private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Four kinds of abstract covers, in the colours of the album. */
private fun drawCover(index: Int, a: FloatArray, b: FloatArray): ByteArray {
    val pixels = ByteArray(DRAWN * DRAWN * 4)
    val kind = index % 4
    var noise = 12345u + index.toUInt() * 977u
    for (y in 0 until DRAWN) {
        for (x in 0 until DRAWN) {
            val u = (x + 0.5f) / DRAWN
            val v = (y + 0.5f) / DRAWN
            var mix: Float
            var light: Float
            when (kind) {
                0 -> { // rings around a point
                    val dx = u - 0.32f
                    val dy = v - 0.68f
                    val d = sqrt(dx * dx + dy * dy)
                    mix = 0.5f + 0.5f * sin(d * 38f)
                    light = 1.05f - d * 0.6f
                }
                1 -> { // a sun over the sea
                    val dx = u - 0.5f
                    val dy = v - 0.46f
                    val d = sqrt(dx * dx + dy * dy)
                    val sun = 1f - smooth(0.2f, 0.205f, d)
                    val band = if (v > 0.46f) (if ((v * 30f).mod(1f) < 0.5f + (v - 0.46f) * 0.9f) 1f else 0f) else 1f
                    mix = v
                    light = 0.35f + sun * band * 0.9f + (1f - sun) * 0.25f
                }
                2 -> { // stripes across
                    val t = (u + v) * 7f
                    mix = smooth(0.45f, 0.55f, t.mod(1f)) * 0.8f + u * 0.2f
                    light = 0.6f + 0.5f * (1f - v)
                }
                else -> { // soft blobs
                    val d1 = (u - 0.3f) * (u - 0.3f) + (v - 0.3f) * (v - 0.3f)
                    val d2 = (u - 0.75f) * (u - 0.75f) + (v - 0.65f) * (v - 0.65f)
                    mix = exp(-d2 * 9f) / (exp(-d1 * 9f) + exp(-d2 * 9f))
                    light = 0.4f + 0.8f * max(exp(-d1 * 7f), exp(-d2 * 7f))
                }
            }
            noise = noise * 1103515245u + 12345u
            val grain = ((noise shr 16) and 255u).toFloat() / 255f * 0.06f - 0.03f
            val p = (y * DRAWN + x) * 4
            for (i in 0 until 3) pixels[p + i] = (((a[i] * (1f - mix) + b[i] * mix) * light + grain).coerceIn(0f, 1f) * 255f).toInt().toByte()
            pixels[p + 3] = -1
        }
    }
    return pixels
}

/** The cover of album [index]: its file in covers/, else one drawn in the colours of its genre. Sets its colours. */
private fun loadCover(directory: String, index: Int, genre: Int, color: FloatArray, color2: FloatArray): ImageBitmap {
    for (extension in listOf("jpg", "png", "jpeg")) {
        val bytes = readFile("$directory/covers/${(index + 1).toString().padStart(2, '0')}.$extension") ?: continue
        val decoded = decode(bytes) ?: continue
        val (pixels, width, height) = decoded
        coverColors(pixels, width, height, genre, color, color2)
        return ImageBitmap(width, height, pixels, FilterQuality.High)
    }
    val base = genreRgb(genre)
    val hsv = rgbToHsv(base[0], base[1], base[2])
    val other = FloatArray(3)
    hsvToRgb((hsv[0] + 0.08f * (index % 5) + 0.5f).mod(1f), min(hsv[1] + 0.1f, 1f), 0.55f, other)
    makeAccent(base, color)
    makeAccent(other, color2)
    return ImageBitmap(DRAWN, DRAWN, drawCover(index, base, other), FilterQuality.High)
}

@OptIn(ExperimentalForeignApi::class)
private fun readFile(path: String): ByteArray? {
    val file = fopen(path, "rb") ?: return null
    try {
        val chunks = mutableListOf<ByteArray>()
        val buffer = ByteArray(64 * 1024)
        buffer.usePinned { pinned ->
            while (true) {
                val read = fread(pinned.addressOf(0), 1u, buffer.size.convert(), file).toInt()
                if (read <= 0) break
                chunks += buffer.copyOf(read)
            }
        }
        return chunks.fold(ByteArray(0)) { all, chunk -> all + chunk }
    } finally {
        fclose(file)
    }
}

/** An image file's pixels, straight RGBA, with Raven's decoder. */
@OptIn(ExperimentalForeignApi::class)
private fun decode(bytes: ByteArray): Triple<ByteArray, Int, Int>? = memScoped {
    val width = alloc<IntVar>()
    val height = alloc<IntVar>()
    val pixels = bytes.usePinned { Rimage_decode(it.addressOf(0), bytes.size.convert(), width.ptr, height.ptr) } ?: return null
    try {
        Triple(pixels.readBytes(width.value * height.value * 4), width.value, height.value)
    } finally {
        Rpixels_free(pixels)
    }
}

/** A colour of the album's, scaled, as the C example's hex() gives it. */
fun FloatArray.toColor(scale: Float = 1f): Color = Color((this[0] * scale).coerceIn(0f, 1f), (this[1] * scale).coerceIn(0f, 1f), (this[2] * scale).coerceIn(0f, 1f))
