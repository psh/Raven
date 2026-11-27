package raven.xml2compose

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.EEXIST
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.closedir
import platform.posix.errno
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.mkdir
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.stat

// The few file operations the tool needs, over POSIX (macOS and Linux).

internal fun isDirectory(path: String): Boolean = memScoped {
    val info = alloc<stat>()
    stat(path, info.ptr) == 0 && (info.st_mode.toInt() and S_IFMT) == S_IFDIR
}

/** The `.xml` files under [root], as `/`-separated paths inside it, sorted; hidden entries are skipped. */
internal fun xmlFiles(root: String): List<String> {
    val found = mutableListOf<String>()
    fun walk(relative: String) {
        val dir = opendir(if (relative.isEmpty()) root else "$root/$relative") ?: return
        val names = mutableListOf<String>()
        try {
            while (true) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed.d_name.toKString()
                if (!name.startsWith(".")) names += name
            }
        } finally {
            closedir(dir)
        }
        for (name in names.sorted()) {
            val path = if (relative.isEmpty()) name else "$relative/$name"
            when {
                isDirectory("$root/$path") -> walk(path)
                name.endsWith(".xml") -> found += path
            }
        }
    }
    walk("")
    return found.sorted()
}

/** The file's text as UTF-8, or null when it can't be read. */
internal fun readText(path: String): String? {
    if (isDirectory(path)) return null
    val file = fopen(path, "rb") ?: return null
    try {
        val bytes = mutableListOf<ByteArray>()
        memScoped {
            val buffer = allocArray<ByteVar>(65536)
            while (true) {
                val read = fread(buffer, 1u, 65536u, file).toInt()
                if (read <= 0) break
                bytes += buffer.readBytes(read)
            }
        }
        val all = ByteArray(bytes.sumOf { it.size })
        var offset = 0
        for (chunk in bytes) {
            chunk.copyInto(all, offset)
            offset += chunk.size
        }
        return all.decodeToString()
    } finally {
        fclose(file)
    }
}

internal fun writeText(path: String, text: String): Boolean {
    val bytes = text.encodeToByteArray()
    val file = fopen(path, "wb") ?: return false
    try {
        if (bytes.isEmpty()) return true
        return bytes.usePinned { fwrite(it.addressOf(0), 1u, bytes.size.convert(), file).toInt() } == bytes.size
    } finally {
        fclose(file)
    }
}

/** Makes [path] and its parents, like `mkdir -p`. */
internal fun makeDirectories(path: String): Boolean {
    if (path.isEmpty() || isDirectory(path)) return true
    val parent = path.substringBeforeLast('/', "")
    if (parent.isNotEmpty() && parent != path && !makeDirectories(parent)) return false
    return mkdir(path, 0x1EDu.convert()) == 0 || errno == EEXIST
}
