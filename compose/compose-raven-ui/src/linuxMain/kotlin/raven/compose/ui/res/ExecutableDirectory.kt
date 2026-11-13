package raven.compose.ui.res

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import platform.posix.readlink

/** The directory of `/proc/self/exe`, the running executable. */
internal actual fun executableDirectory(): String? = memScoped {
    val size = 4096
    val buffer = allocArray<ByteVar>(size)
    val length = readlink("/proc/self/exe", buffer, size.toULong())
    if (length <= 0 || length >= size) return null
    buffer.readBytes(length.toInt()).decodeToString().substringBeforeLast('/', missingDelimiterValue = ".")
}
