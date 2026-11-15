package raven.spike.maps

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.mkdir
import platform.posix.rename

/** Where tiles come from: their bytes, or null where there is no tile. */
interface TileSource {
    /** The zooms the source has tiles for; past [maxZoom], a map draws the deepest tiles larger. */
    val minZoom: Int
    val maxZoom: Int

    /** The tile's bytes, or null if the source has none there. Throws when it can't tell (a network error). */
    suspend fun fetch(id: TileId): ByteArray?
}

/** The tiles fetch-data downloaded: [directory]/tiles/z-x-y.pbf, those listed in [directory]/tiles.txt. */
class FileTileSource(private val directory: String) : TileSource {
    /** The tiles listed. */
    val tiles: Set<TileId> = readFile("$directory/tiles.txt")?.decodeToString().orEmpty().lines().mapNotNull { line ->
        line.trim().split(' ').takeIf { it.size == 3 }?.let { TileId(it[0].toInt(), it[1].toInt(), it[2].toInt()) }
    }.toSet()

    override val minZoom: Int = tiles.minOfOrNull { it.z } ?: 0
    override val maxZoom: Int = tiles.maxOfOrNull { it.z } ?: 0

    val isEmpty: Boolean get() = tiles.isEmpty()

    override suspend fun fetch(id: TileId): ByteArray? =
        if (id !in tiles) null else withContext(Dispatchers.IO) { readFile("$directory/tiles/${id.z}-${id.x}-${id.y}.pbf") }
}

/**
 * Tiles over HTTP, from a TileJSON's URL [template] (`{z}`, `{x}`, `{y}`), with at most
 * [parallel] requests at a time, kept in [cache] so a tile is fetched once.
 */
class HttpTileSource(
    private val client: HttpClient,
    private val template: String,
    override val minZoom: Int,
    override val maxZoom: Int,
    private val cache: DiskCache?,
    parallel: Int = 8,
) : TileSource {
    private val requests = Semaphore(parallel)

    /** Tiles fetched over the network, and tiles found in the cache. */
    var fetched = 0
        private set
    var cached = 0
        private set

    override suspend fun fetch(id: TileId): ByteArray? {
        val key = "${id.z}/${id.x}/${id.y}.pbf"
        cache?.read(key)?.let {
            cached++
            return it
        }
        val bytes = requests.withPermit {
            val response = client.get(template.replace("{z}", "${id.z}").replace("{x}", "${id.x}").replace("{y}", "${id.y}"))
            when {
                response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.NoContent -> ByteArray(0)
                response.status.isSuccess() -> response.bodyAsBytes()
                else -> error("tile $key: HTTP ${response.status.value}")
            }
        }
        fetched++
        cache?.write(key, bytes)
        // An empty tile is a tile with nothing in it, kept so it isn't asked for again.
        return bytes
    }

    companion object {
        /** A client for tiles: OpenFreeMap asks for a User-Agent of the application's own. */
        fun client(): HttpClient = HttpClient {
            install(UserAgent) { agent = USER_AGENT }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
            }
        }

        /**
         * The source the style's vector source names: its TileJSON at [url], fetched with
         * [client], its tiles cached under [cacheDirectory], by the TileJSON's tile URL, so a
         * new planet build starts a cache of its own.
         */
        suspend fun fromTileJson(client: HttpClient, url: String, cacheDirectory: String?): HttpTileSource {
            @Suppress("UNCHECKED_CAST")
            val json = parseJson(client.get(url).bodyAsText()) as Map<String, Any?>
            val template = (json["tiles"] as List<*>).first() as String
            val cache = cacheDirectory?.let { DiskCache("$it/${template.hashCode().toUInt().toString(16)}") }
            return HttpTileSource(client, template, (json["minzoom"] as? Double)?.toInt() ?: 0, (json["maxzoom"] as? Double)?.toInt() ?: 14, cache)
        }
    }
}

/** What the spike tells the servers it is. */
const val USER_AGENT = "compose-raven-maps-spike/0.1"

/** Files under [directory], made on demand: written whole to a temporary name, then renamed, so a reader never sees half of one. */
@OptIn(ExperimentalForeignApi::class)
class DiskCache(private val directory: String) {
    suspend fun read(key: String): ByteArray? = withContext(Dispatchers.IO) { readFile("$directory/$key") }

    suspend fun write(key: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val path = "$directory/$key"
            makeDirectories(path.substringBeforeLast('/'))
            val temporary = "$path.part"
            val file = fopen(temporary, "wb") ?: return@withContext
            try {
                if (bytes.isNotEmpty()) bytes.usePinned { fwrite(it.addressOf(0), 1u, bytes.size.convert(), file) }
            } finally {
                fclose(file)
            }
            rename(temporary, path)
        }
    }

    private fun makeDirectories(path: String) {
        var at = path.indexOf('/', 1)
        while (at > 0) {
            mkdir(path.substring(0, at), 0x1EDu)
            at = path.indexOf('/', at + 1)
        }
        mkdir(path, 0x1EDu)
    }
}
