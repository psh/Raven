@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.refTo
import raven.cinterop.R_TRUE
import raven.cinterop.Rfile_is_memory
import raven.cinterop.Rfile_register_memory
import raven.cinterop.Ris_ui_thread
import raven.cinterop.Rtexture_create_from_file_async
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.LocalRavenHost
import raven.compose.ui.RavenHost
import raven.compose.ui.graphics.painter.Painter
import raven.compose.ui.graphics.painter.ResourcePainter

/**
 * The image file at [path], decoded on a worker thread: the image shows nothing until it is ready.
 * A relative path is looked for first among the files given to [registerResource], then in the
 * resource root (`resources/` beside the executable, or `application(resourceRoot)`). Formats are
 * PNG, JPEG, BMP, TGA, GIF, PSD and SVG. The texture is kept by the host for each path until Raven
 * shuts down, and keeps Raven's mipmapped filter.
 */
@Composable
public fun painterResource(path: String): Painter {
    val host = LocalRavenHost.current
    return remember(host, path) {
        host.caches.getOrPut(ResourceImageKey(path)) {
            val resolved = host.resolveResource(path)
            ResourcePainter(resolved, Rtexture_create_from_file_async(resolved), host.epoch)
        }
    }
}

private data class ResourceImageKey(val path: String)

/**
 * Embeds a file under [path] (`Rfile_register_memory`): Raven finds it there before looking on
 * disk, for images, fonts and theme files alike, so resources built into the executable need no
 * file beside it. Call it on the main thread, before `application` or while it runs. The bytes are
 * copied, and kept for every later run of Raven in the process.
 */
public fun registerResource(path: String, bytes: ByteArray) {
    val copy = bytes.copyOf()
    embeddedResources[path] = copy
    if (RavenHost.running != null && Ris_ui_thread() == R_TRUE) registerInRaven(path, copy)
}

// The files embedded with registerResource, which every host gives Raven when it starts.
private val embeddedResources = LinkedHashMap<String, ByteArray>()

/** Gives Raven the embedded files: `Rshutdown` forgets them, so each run of Raven needs them again. */
internal fun registerEmbeddedResources() {
    embeddedResources.forEach { (path, bytes) -> registerInRaven(path, bytes) }
}

private fun registerInRaven(path: String, bytes: ByteArray) {
    // Raven keeps a copy, so the Kotlin array may move or go.
    val ok = if (bytes.isEmpty()) Rfile_register_memory(path, null, 0u, R_TRUE) else Rfile_register_memory(path, bytes.refTo(0), bytes.size.toULong(), R_TRUE)
    check(ok == R_TRUE) { "Raven refused to embed $path" }
}

/**
 * Where Raven finds [path]: as it is when it is absolute or embedded ([registerResource]), and in
 * the host's resource root otherwise.
 */
@InternalComposeRavenApi
public fun RavenHost.resolveResource(path: String): String =
    if (isAbsolutePath(path) || Rfile_is_memory(path) == R_TRUE) path else "${resourceRoot.trimEnd('/')}/$path"

private fun isAbsolutePath(path: String): Boolean =
    path.startsWith("/") || path.startsWith("\\") || (path.length > 2 && path[1] == ':' && (path[2] == '/' || path[2] == '\\'))

/** `resources/` beside the executable, or beside the current directory when the executable can't be found. */
internal fun defaultResourceRoot(): String = (executableDirectory() ?: ".") + "/resources"

/** The directory of the running executable, null when the platform doesn't say. */
internal expect fun executableDirectory(): String?
