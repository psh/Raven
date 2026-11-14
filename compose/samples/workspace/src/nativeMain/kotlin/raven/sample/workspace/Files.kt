package raven.sample.workspace

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.fclose
import platform.posix.ferror
import platform.posix.fopen
import platform.posix.fread

/**
 * The text of the file at [path], or null when it can't be read. Reading stops past [limit]
 * bytes, so a text longer than that says the file is too large.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun readTextFile(path: String, limit: Int): String? {
    val file = fopen(path, "rb") ?: return null
    try {
        val chunks = mutableListOf<ByteArray>()
        var total = 0
        val buffer = ByteArray(64 * 1024)
        buffer.usePinned { pinned ->
            while (total <= limit) {
                val read = fread(pinned.addressOf(0), 1u, buffer.size.convert(), file).toInt()
                if (read <= 0) break
                chunks += buffer.copyOf(read)
                total += read
            }
        }
        if (ferror(file) != 0) return null
        val bytes = ByteArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(bytes, offset)
            offset += chunk.size
        }
        return bytes.decodeToString()
    } finally {
        fclose(file)
    }
}
