package raven.sample.viewportvulkan

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen

/** The example's two shaders as SPIR-V words: triangle.vert and triangle.frag. */
class TriangleShaders(val vertex: UIntArray, val fragment: UIntArray)

/**
 * Reads the SPIR-V of shaders_spv.h, the C example's `triangle_vert_spv` and `triangle_frag_spv`
 * arrays, where it is rather than a copy, as the other samples read their example's files.
 */
fun readTriangleShaders(directory: String = EXAMPLE_DIR): TriangleShaders {
    val path = "$directory/shaders_spv.h"
    val text = readText(path) ?: error("viewport-vulkan: can't read $path")
    return TriangleShaders(spirvArray(text, "triangle_vert_spv"), spirvArray(text, "triangle_frag_spv"))
}

/** The words of the C array [name] in [header]: `static const uint32_t name[n] = { 0x..., ... };`. */
fun spirvArray(header: String, name: String): UIntArray {
    val start = header.indexOf("$name[")
    require(start >= 0) { "no array $name in the header" }
    val size = header.substring(header.indexOf('[', start) + 1, header.indexOf(']', start)).trim().toInt()
    val body = header.substring(header.indexOf('{', start) + 1, header.indexOf('}', start))
    val words = body.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { it.removePrefix("0x").toUInt(16) }
    check(words.size == size) { "$name has ${words.size} words, not the $size it declares" }
    check(words.firstOrNull() == 0x07230203u) { "$name isn't SPIR-V" }
    return words.toUIntArray()
}

private fun readText(path: String): String? {
    val file = fopen(path, "r") ?: return null
    try {
        return buildString {
            memScoped {
                val line = allocArray<ByteVar>(4096)
                while (fgets(line, 4096, file) != null) append(line.toKString())
            }
        }
    } finally {
        fclose(file)
    }
}
