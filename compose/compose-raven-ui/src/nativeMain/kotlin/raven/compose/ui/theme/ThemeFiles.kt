@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui.theme

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import raven.cinterop.R_TRUE
import raven.cinterop.Rerror_get_last
import raven.cinterop.Rtheme_load
import raven.cinterop.Rtheme_load_from_memory
import raven.cinterop.Rtheme_save
import raven.compose.ui.InternalComposeRavenApi
import raven.native.toRavenString

/**
 * Loads the theme file at [path] and returns its name, for `RavenTheme(theme = ...)`: its `Name`,
 * or else the file's name. A theme of that name already loaded is replaced, the app theme too if
 * it is the one in use. While live reload is on, Raven loads the file again when it is saved, and
 * every widget that uses the theme follows. It is valid only while Raven runs, on its UI thread,
 * and throws when Raven can't read the theme.
 */
public fun loadTheme(path: String): String {
    val host = runningHost("loadTheme")
    val name = loaded(Rtheme_load(path)) { "the theme file $path" }
    host.theme.refresh()
    return name
}

/** Loads a theme from the XML text [xml], as [loadTheme] loads a file, and returns its name. */
public fun loadThemeFromString(xml: String): String {
    val host = runningHost("loadThemeFromString")
    val name = loaded(Rtheme_load_from_memory(xml, 0.convert())) { "the theme" }
    host.theme.refresh()
    return name
}

/**
 * Writes the app theme as Raven has it, the accent and every other change included, as a theme
 * file at [path] named [name], or the name of the theme in use. False when the file can't be
 * written. It is valid only while Raven runs, on its UI thread.
 */
public fun saveTheme(path: String, name: String? = null): Boolean {
    runningHost("saveTheme")
    return Rtheme_save(path, name) == R_TRUE
}

/** The theme name Raven returned, or an error naming [what] with Raven's reason. */
private inline fun loaded(name: CPointer<ByteVar>?, what: () -> String): String {
    if (name != null) return name.toRavenString()
    val detail = memScoped {
        val text = alloc<CPointerVar<ByteVar>>()
        Rerror_get_last(text.ptr, null)
        text.value.toRavenString()
    }
    throw IllegalArgumentException("Raven could not load ${what()}: $detail")
}
