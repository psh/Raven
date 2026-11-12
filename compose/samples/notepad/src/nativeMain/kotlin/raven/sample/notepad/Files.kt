package raven.sample.notepad

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.fclose
import platform.posix.ferror
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite

/** The text of the file at [path], or null when it can't be read. */
@OptIn(ExperimentalForeignApi::class)
internal fun readTextFile(path: String): String? {
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
        if (ferror(file) != 0) return null
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

/** Writes [text] to the file at [path], replacing it, and returns whether it was all written. */
@OptIn(ExperimentalForeignApi::class)
internal fun writeTextFile(path: String, text: String): Boolean {
    val bytes = text.encodeToByteArray()
    val file = fopen(path, "wb") ?: return false
    val written = if (bytes.isEmpty()) 0 else bytes.usePinned { fwrite(it.addressOf(0), 1u, bytes.size.convert(), file).toInt() }
    return (fclose(file) == 0) and (written == bytes.size)
}
