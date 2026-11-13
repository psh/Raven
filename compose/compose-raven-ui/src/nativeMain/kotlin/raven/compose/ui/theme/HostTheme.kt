@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.RLook
import raven.cinterop.RMaterial
import raven.cinterop.R_COLOR_LAST_
import raven.cinterop.R_COLOR_WINDOW
import raven.cinterop.R_LOOK_RAVEN_DARK
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Ris_ui_thread
import raven.cinterop.Rlook_get
import raven.cinterop.Rlook_set
import raven.cinterop.Rtheme_get_color
import raven.cinterop.Rtheme_get_count
import raven.cinterop.Rtheme_get_material
import raven.cinterop.Rtheme_get_name_at
import raven.cinterop.Rtheme_set_accent
import raven.cinterop.Rtheme_set_font
import raven.cinterop.Rtheme_set_material
import raven.cinterop.Rtheme_use
import raven.cinterop.Rwidget_set_theme
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.RavenHost
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.Material
import raven.compose.ui.graphics.ravenColor
import raven.compose.ui.graphics.toRaven
import raven.native.toRavenString

/**
 * The app theme as one host sees it: the app-wide settings its application-level `RavenTheme`
 * made, and what Raven has from them, [colors] and [themeNames], as snapshot state. Raven's own
 * changes (a theme file loaded, or reloaded and reported to the host's reload callback) reach it
 * through [refresh].
 */
internal class HostTheme {
    var colors: RavenColors by mutableStateOf(RavenColors(List(COLOR_COUNT) { Color.Unspecified }))
        private set

    var themeNames: List<String> by mutableStateOf(emptyList())
        private set

    // What the application-level RavenTheme last asked for; null is "left as Raven has it".
    private var look: Look? = null
    private var dark: Boolean? = null
    private var theme: String? = null
    private var accent: Color? = null
    private var material: Material? = null
    private var font: Rid? = null

    // The subtree themes Raven refused, by name, so it reports each once and not once per widget.
    // Forgotten when Raven's themes change, since the name can then be one.
    private val refusedThemes = HashSet<String>()

    // The material the look and theme in use give, which Material.Default goes back to: read before
    // the first material is set over them, and again after each change of them.
    private var themeMaterial: RMaterial? = null

    /**
     * Makes the app-wide settings that changed: [look] and [dark] as one `Rlook_set`, then
     * [theme], then [material], [accent] and [font], which Raven resets on every look or theme
     * change, so they are applied again after one.
     */
    fun apply(look: Look?, dark: Boolean?, theme: String?, accent: Color?, material: Material?, font: Rid? = null) {
        val lookChanged = (look != null || dark != null) && (look != this.look || dark != this.dark)
        val themeChanged = theme != null && (theme != this.theme || lookChanged)
        this.look = look
        this.dark = dark
        this.theme = theme
        val accentChanged = accent != null && (accent != this.accent || lookChanged || themeChanged)
        this.accent = accent
        val materialChanged = material != null && (material != this.material || lookChanged || themeChanged)
        this.material = material
        val fontChanged = font != null && (font != this.font || lookChanged || themeChanged)
        this.font = font
        if (lookChanged) Rlook_set(resolve(look, dark))
        // A look change lays a theme file over the new look, or replaces a theme that names its own.
        if (themeChanged) Rtheme_use(theme)
        if (lookChanged || themeChanged) themeMaterial = null
        if (materialChanged) setMaterial()
        if (accentChanged) setAccent()
        if (fontChanged) Rtheme_set_font(font!!)
        if (lookChanged || themeChanged || accentChanged || materialChanged) refresh(restoreAccent = false)
    }

    /**
     * Reads [colors] and [themeNames] from Raven again, and writes the state that changed. With
     * [restoreAccent], a change of the app theme's colours is taken for a theme Raven replaced (a
     * theme file loaded or reloaded under the name in use), which reset the accent and the
     * material: they are applied again first.
     */
    fun refresh(restoreAccent: Boolean = true) {
        var read = readColors()
        if (restoreAccent && (accent != null || material != null) && read != colors) {
            themeMaterial = null
            setMaterial()
            setAccent()
            read = readColors()
        }
        if (read != colors) colors = read
        val names = List(Rtheme_get_count()) { Rtheme_get_name_at(it).toRavenString() }
        if (names != themeNames) {
            themeNames = names
            refusedThemes.clear()
        }
    }

    /**
     * Gives [widget] and its descendants the theme [name], null following the parent again. A
     * name Raven refused is not asked for again: the widget keeps its theme, as Raven would leave it.
     */
    fun setWidgetTheme(widget: Rid, name: String?) {
        if (name != null && name in refusedThemes) return
        if (Rwidget_set_theme(widget, name) != R_TRUE && name != null) refusedThemes += name
    }

    /** Sets [material] app-wide; [Material.Default] is the look's and theme's own. */
    private fun setMaterial() {
        val material = material ?: return
        val own = themeMaterial ?: Rtheme_get_material().also { themeMaterial = it }
        Rtheme_set_material(if (material == Material.Default) own else material.toRaven())
    }

    private fun setAccent() {
        accent?.toRaven { red, green, blue, _ -> Rtheme_set_accent(red, green, blue) }
    }

    private fun readColors(): RavenColors = RavenColors(
        List(COLOR_COUNT) { index ->
            ravenColor { r, g, b, a -> Rtheme_get_color(R_COLOR_WINDOW + index.toUInt(), r, g, b, a) }
        },
    )
}

/**
 * The look of [look] in [dark], each null keeping what Raven has: the look family in use, or
 * whether the look in use is dark. Each family is a dark look followed by its light one.
 */
private fun resolve(look: Look?, dark: Boolean?): RLook {
    val current = (Rlook_get() - R_LOOK_RAVEN_DARK).toInt()
    val family = look?.ordinal ?: (current / 2)
    val isDark = dark ?: (current % 2 == 0)
    return R_LOOK_RAVEN_DARK + (family * 2 + if (isDark) 0 else 1).toUInt()
}

/** The host Raven runs under now, for what is valid only while it runs; [what] names the caller. */
internal fun runningHost(what: String): RavenHost {
    val host = RavenHost.running
    check(host != null && Ris_ui_thread() == R_TRUE) { "$what is valid only while Raven runs, on its UI thread" }
    return host
}

/** Raven's colour roles, from `R_COLOR_WINDOW` on. */
internal val COLOR_COUNT: Int = (R_COLOR_LAST_ - R_COLOR_WINDOW).toInt()
