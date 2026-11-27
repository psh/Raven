@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui.res

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value
import raven.cinterop.Rfile_read
import raven.cinterop.Rmemory_free
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.RavenHost
import raven.compose.ui.graphics.painter.Painter
import raven.compose.ui.text.font.Font
import raven.compose.ui.text.font.FontStyle
import raven.compose.ui.text.font.FontWeight

/**
 * An image under `composeResources/drawable/`, as the generated `Res.drawable.<name>` gives it. Its
 * [path] is under `composeResources/`, which resolves against the resource root (where the
 * `raven.compose-resources` plugin copies the folder) or among the embedded files.
 */
public class DrawableResource(public val path: String) {
    override fun equals(other: Any?): Boolean = other is DrawableResource && path == other.path

    override fun hashCode(): Int = path.hashCode()

    override fun toString(): String = "DrawableResource($path)"
}

/** A font file under `composeResources/font/`, as the generated `Res.font.<name>` gives it. */
public class FontResource(public val path: String) {
    override fun equals(other: Any?): Boolean = other is FontResource && path == other.path

    override fun hashCode(): Int = path.hashCode()

    override fun toString(): String = "FontResource($path)"
}

/** The image of [resource], as `painterResource(path)` gives it: decoded on a worker, kept by the host. */
@Composable
public fun painterResource(resource: DrawableResource): Painter = painterResource(resource.path)

/** The font of [resource] drawing [weight] and [style], for a `FontFamily`. */
@Composable
public fun Font(resource: FontResource, weight: FontWeight = FontWeight.Normal, style: FontStyle = FontStyle.Normal): Font =
    Font(resource.path, weight, style)

/**
 * The bytes of the file at [path] under `composeResources/` (`"files/notes.txt"`), as the generated
 * `Res.readBytes` reads them: among the embedded files first, then in the resource root of the
 * running application, or `resources/` beside the executable before one runs. A file that can't be
 * read throws.
 */
public suspend fun readResourceBytes(path: String): ByteArray {
    val resolved = RavenHost.running?.resolveResource(path) ?: if (path.startsWith("/")) path else "${defaultResourceRoot()}/$path"
    return memScoped {
        val size = alloc<ULongVar>()
        val data = Rfile_read(resolved, size.ptr) ?: throw IllegalStateException("can't read the resource $path ($resolved)")
        try {
            if (size.value == 0uL) ByteArray(0) else data.readBytes(size.value.toInt())
        } finally {
            Rmemory_free(data)
        }
    }
}
