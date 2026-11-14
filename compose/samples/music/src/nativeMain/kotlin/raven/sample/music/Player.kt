package raven.sample.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

enum class Repeat { Off, All, One }

/**
 * The player, simulated: no sound is played, the songs "play" by the clock ([advance]). What it
 * plays is a queue of tracks, from a context (an album, a playlist).
 */
class Player(private val library: Library, private val random: Random = Random.Default) {
    var queue by mutableStateOf(emptyList<Int>())
        private set
    var position by mutableIntStateOf(0)
        private set
    var track by mutableIntStateOf(-1)
        private set

    /** Seconds into the track. */
    var seconds by mutableDoubleStateOf(0.0)
    var playing by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeat by mutableStateOf(Repeat.Off)
    var volume by mutableFloatStateOf(0.7f)
    var muted by mutableStateOf(false)

    /** What the queue was played from: an album's or a playlist's title. */
    var context by mutableStateOf("")
        private set

    /** The albums saved to the library. */
    val likedAlbums = mutableStateListOf(2, 5)

    val currentTrack: Track? get() = library.tracks.getOrNull(track)
    val currentAlbum: Album? get() = currentTrack?.let { library.albums[it.album] }

    fun playPosition(index: Int) {
        if (queue.isEmpty()) return
        position = if (index in queue.indices) index else 0
        track = queue[position]
        seconds = 0.0
        playing = true
    }

    /** Plays [tracks] from [start]; with shuffle, the others in random order after it. */
    fun playList(tracks: List<Int>, start: Int, context: String) {
        if (tracks.isEmpty()) return
        this.context = context
        if (shuffle) {
            val rest = tracks.toMutableList().also { it.removeAt(start) }.shuffled(random)
            queue = listOf(tracks[start]) + rest
            playPosition(0)
        } else {
            queue = tracks
            playPosition(start)
        }
    }

    fun next() {
        if (queue.isNotEmpty()) playPosition(if (position + 1 < queue.size) position + 1 else 0)
    }

    /** A few seconds in, back to the start of the song; else the one before. */
    fun previous() {
        if (seconds > 3.0 || position == 0) seconds = 0.0 else playPosition(position - 1)
    }

    /** Moves the clock on by [elapsed] seconds while it plays, going on to the next song at the end of one. */
    fun advance(elapsed: Double) {
        val current = currentTrack ?: return
        if (!playing) return
        seconds += elapsed
        if (seconds >= current.seconds) {
            when {
                repeat == Repeat.One -> seconds = 0.0
                position + 1 < queue.size -> playPosition(position + 1)
                repeat == Repeat.All -> playPosition(0)
                else -> {
                    playing = false
                    seconds = 0.0
                }
            }
        }
    }

    /** Whether [tracks] is what plays: a page's play button is a pause only then. */
    fun isPlaying(tracks: List<Int>): Boolean = playing && queue.isNotEmpty() && queue == tracks
}
