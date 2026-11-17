package raven.spike.maps

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import platform.posix.getenv
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapsTest {
    @Test
    fun jsonReadsAStyleShapedDocument() {
        val json = parseJson("""{"a": [1, -2.5e1, "x\"y", true, null], "b": {"c": "é"}}""") as Map<*, *>
        assertEquals(listOf(1.0, -25.0, "x\"y", true, null), json["a"])
        assertEquals(mapOf("c" to "é"), json["b"])
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun theDiskCacheKeepsTilesEmptyOnesIncluded() = runBlocking {
        val directory = "${getenv("TMPDIR")?.toKString()?.trimEnd('/') ?: "/tmp"}/compose-raven-maps-test-${Random.nextInt(1_000_000)}"
        val cache = DiskCache(directory)
        // Any bytes do: the cache doesn't read them.
        val tile = byteArrayOf(0x1A, 2, 3, 4)
        assertNull(cache.read("14/1/2.pbf"))
        cache.write("14/1/2.pbf", tile)
        cache.write("14/1/3.pbf", ByteArray(0))
        assertEquals(tile.toList(), cache.read("14/1/2.pbf")?.toList())
        assertEquals(0, cache.read("14/1/3.pbf")?.size, "an empty tile is kept, as a tile with nothing in it")
        // Found in the cache, a tile is not fetched: the client is never used.
        val source = HttpTileSource(HttpTileSource.client(), "http://localhost:1/{z}/{x}/{y}.pbf", 0, 14, cache)
        assertEquals(tile.toList(), source.fetch(TileId(14, 1, 2))?.toList())
        assertEquals(1, source.cached)
        assertEquals(0, source.fetched)
    }
}
