package raven.mcp

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import platform.posix.O_CREAT
import platform.posix.O_RDONLY
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.fchmod
import platform.posix.getenv
import platform.posix.mkdir
import platform.posix.open
import platform.posix.read
import platform.posix.write

/**
 * The bearer token of each app, stored once per app and user in ~/.raven/mcp/<name>.json with the
 * URL, readable by that user alone (0600), so an agent is set up once and the token survives launches.
 */
@OptIn(ExperimentalForeignApi::class)
internal object McpTokens {
    /** The app's token, made the first time; null when the file can't be written. */
    fun storedToken(name: String, url: String): String? {
        val home = getenv("HOME")?.toKString() ?: return null
        val folder = "$home/.raven/mcp"
        mkdir("$home/.raven", 0x1C0u)  // 0700; an existing folder is fine
        mkdir(folder, 0x1C0u)
        val path = "$folder/${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json"
        val token = readFile(path)?.let { text ->
            runCatching { (Json.parseToJsonElement(text) as JsonObject)["token"]?.jsonPrimitive?.content }.getOrNull()
        } ?: newToken() ?: return null
        val content = JsonObject(mapOf("url" to JsonPrimitive(url), "token" to JsonPrimitive(token))).toString() + "\n"
        return if (writeFile(path, content)) token else null
    }

    private fun newToken(): String? {
        val fd = open("/dev/urandom", O_RDONLY)
        if (fd < 0) return null
        val bytes = ByteArray(32)
        val got = bytes.usePinned { read(fd, it.addressOf(0), bytes.size.convert()) }
        close(fd)
        if (got.toInt() != bytes.size) return null
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private fun readFile(path: String): String? {
        val fd = open(path, O_RDONLY)
        if (fd < 0) return null
        val text = memScoped {
            val buffer = allocArray<ByteVar>(4096)
            val count = read(fd, buffer, 4095.convert())
            if (count <= 0) null else buffer.readBytes(count.toInt()).decodeToString()
        }
        close(fd)
        return text
    }

    private fun writeFile(path: String, text: String): Boolean {
        val fd = open(path, O_WRONLY or O_CREAT or O_TRUNC, 0x180)  // 0600
        if (fd < 0) return false
        fchmod(fd, 0x180u)
        val bytes = text.encodeToByteArray()
        val written = bytes.usePinned { write(fd, it.addressOf(0), bytes.size.convert()) }
        close(fd)
        return written.toInt() == bytes.size
    }
}

