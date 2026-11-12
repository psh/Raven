package raven.sample.themes

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread

/** The text of the file at [path], read as UTF-8, or null when it can't be opened. */
fun readTextFile(path: String): String? {
    val file = fopen(path, "rb") ?: return null
    try {
        val chunks = mutableListOf<ByteArray>()
        val buffer = ByteArray(4096)
        while (true) {
            val read = buffer.usePinned { fread(it.addressOf(0), 1u, buffer.size.convert(), file) }.toInt()
            if (read <= 0) break
            chunks += buffer.copyOf(read)
        }
        val bytes = ByteArray(chunks.sumOf { it.size })
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
