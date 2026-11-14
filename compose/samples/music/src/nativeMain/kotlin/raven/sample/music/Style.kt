package raven.sample.music

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.Material
import raven.compose.ui.theme.Look

// The player's own design system: what the C example's theme files say of its classes ("surface",
// "muted", "chip"...), as a palette for each of its themes and one for any look of Raven. The
// composables of Widgets.kt read it; Color.Unspecified is the theme's colour.

/** A button's colours: at rest, under the pointer, pressed, and its content. */
@Immutable
data class ButtonStyle(
    val container: Color = Color.Unspecified,
    val hovered: Color = Color.Unspecified,
    val pressed: Color = Color.Unspecified,
    val content: Color = Color.Unspecified,
)

@Immutable
data class MusicStyle(
    /** The panels: a background over the moving colours, or a material, and an outline. */
    val surface: Color = Color.Unspecified,
    val surfaceMaterial: Material? = null,
    val surfaceBorder: Color = Color.Unspecified,
    /** The player bar. */
    val bar: Color = Color.Unspecified,
    val barMaterial: Material? = null,
    /** Secondary text, and how see-through it is when it has no colour of its own. */
    val muted: Color = Color.Unspecified,
    val mutedAlpha: Float = 1f,
    /** The small capitals over a section. */
    val eyebrow: Color = Color.Unspecified,
    val eyebrowAlpha: Float = 1f,
    /** The side bar's entries, and the one of the page shown. */
    val nav: Color = Color.Unspecified,
    val navAlpha: Float = 1f,
    val navActive: Color = Color.Unspecified,
    /** Round icon buttons: their glyph, and under the pointer. */
    val icon: Color = Color.Unspecified,
    val iconHovered: Color = Color.Unspecified,
    /** The white play button. */
    val white: ButtonStyle = ButtonStyle(),
    val chip: ButtonStyle = ButtonStyle(),
    val chipOn: ButtonStyle = ButtonStyle(),
    val card: ButtonStyle = ButtonStyle(),
    val cardRadius: Float = 10f,
    val quick: ButtonStyle = ButtonStyle(),
    val quickRadius: Float = 6f,
    val row: ButtonStyle = ButtonStyle(),
    val rowRadius: Float = 6f,
    val link: Color = Color.Unspecified,
    val linkAlpha: Float = 1f,
    val seekTrack: Color = Color.Unspecified,
    /** The header's search field. */
    val search: Color = Color.Unspecified,
    val searchMaterial: Material? = null,
    val searchBorder: Color = Color.Unspecified,
    /** The queue's card, and Search's top result. */
    val cardFlat: Color = Color.Unspecified,
    val top: ButtonStyle = ButtonStyle(),
)

private fun c(argb: Long) = Color(argb)

/** nocturne.xml: nearly black, with surfaces that let the colours of the background through. */
val Nocturne = MusicStyle(
    surface = c(0xC80D0D12), surfaceBorder = c(0x0DFFFFFF), bar = c(0xE6050508),
    muted = c(0xFFA3A3B4), eyebrow = c(0xFFD6D6E2), nav = c(0xFFA3A3B4), navActive = c(0xFFFFFFFF),
    icon = c(0xFFC4C4D2), iconHovered = c(0xFFFFFFFF),
    white = ButtonStyle(c(0xFFF4F4F8), c(0xFFFFFFFF), c(0xFFD9D9E3), c(0xFF060609)),
    chip = ButtonStyle(c(0x14FFFFFF), c(0x24FFFFFF)),
    chipOn = ButtonStyle(c(0xFFF4F4F8), c(0xFFFFFFFF), c(0xFFD9D9E3), c(0xFF060609)),
    card = ButtonStyle(hovered = c(0x12FFFFFF), pressed = c(0x1CFFFFFF)), cardRadius = 10f,
    quick = ButtonStyle(c(0x14FFFFFF), c(0x26FFFFFF)), quickRadius = 6f,
    row = ButtonStyle(hovered = c(0x10FFFFFF), pressed = c(0x1AFFFFFF)), rowRadius = 6f,
    link = c(0xFFA3A3B4), seekTrack = c(0x2EFFFFFF),
    search = c(0xFF1E1E28), cardFlat = c(0x0AFFFFFF),
    top = ButtonStyle(c(0x0DFFFFFF), c(0x17FFFFFF)),
)

/** aurora.xml: frosted glass over the moving colours. */
val Aurora = MusicStyle(
    surfaceMaterial = Material.FrostedGlass, barMaterial = Material.FrostedGlass,
    muted = c(0xFFC8C8D8), eyebrow = c(0xFFE4E4EE), nav = c(0xFFC8C8D8), navActive = c(0xFFFFFFFF),
    icon = c(0xFFDCDCE8), iconHovered = c(0xFFFFFFFF),
    white = ButtonStyle(c(0xFFF7F7FB), c(0xFFFFFFFF), c(0xFFDCDCE6), c(0xFF0A0A10)),
    chipOn = ButtonStyle(c(0xFFF7F7FB), c(0xFFFFFFFF), content = c(0xFF0A0A10)),
    cardRadius = 14f, quickRadius = 8f, rowRadius = 8f,
    link = c(0xFFC8C8D8), searchMaterial = Material.FrostedGlass, cardFlat = c(0x10FFFFFF),
)

/** daylight.xml: a light window, white panels. */
val Daylight = MusicStyle(
    surface = c(0xD9FFFFFF), surfaceBorder = c(0x0F000000), bar = c(0xE6FFFFFF),
    muted = c(0xFF5E5E6C), eyebrow = c(0xFF3A3A46), nav = c(0xFF5E5E6C), navActive = c(0xFF16161D),
    icon = c(0xFF3A3A46), iconHovered = c(0xFF16161D),
    white = ButtonStyle(c(0xFF16161D), c(0xFF2A2A35), c(0xFF000000), c(0xFFFFFFFF)),
    chipOn = ButtonStyle(c(0xFF16161D), c(0xFF2A2A35), content = c(0xFFFFFFFF)),
    quick = ButtonStyle(c(0xB3FFFFFF), c(0xFFFFFFFF)),
    link = c(0xFF5E5E6C), search = c(0xFFFFFFFF), searchBorder = c(0x14000000), cardFlat = c(0x0A000000),
    top = ButtonStyle(c(0xFFFFFFFF), c(0xFFFAFAFA)),
)

/** styles.xml: over any look of Raven, its colours kept, the classes only given their shapes. */
val OverTheLook = MusicStyle(
    surfaceMaterial = Material.Solid, searchMaterial = Material.Solid,
    mutedAlpha = 0.7f, eyebrowAlpha = 0.85f, navAlpha = 0.75f, linkAlpha = 0.75f,
)

val LocalMusicStyle = staticCompositionLocalOf { Nocturne }

/** A look the player offers: one of its three themes, or a look of Raven with its classes over it. */
data class MusicLook(val name: String, val theme: String?, val look: Look, val dark: Boolean, val style: MusicStyle)

/** The player's looks, in the C example's order; the themes are named as their files name them. */
val MusicLooks = listOf(
    MusicLook("Nocturne", "Nocturne", Look.Raven, true, Nocturne),
    MusicLook("Aurora glass", "Aurora glass", Look.Glass, true, Aurora),
    MusicLook("Daylight", "Daylight", Look.Raven, false, Daylight),
    MusicLook("Raven dark", null, Look.Raven, true, OverTheLook),
    MusicLook("Raven light", null, Look.Raven, false, OverTheLook),
    MusicLook("Raven glass dark", null, Look.Glass, true, OverTheLook),
    MusicLook("Raven glass light", null, Look.Glass, false, OverTheLook),
    MusicLook("High contrast", null, Look.HighContrast, true, OverTheLook),
    MusicLook("Windows 11 dark", null, Look.Windows, true, OverTheLook),
    MusicLook("Windows 11 light", null, Look.Windows, false, OverTheLook),
    MusicLook("macOS dark", null, Look.Macos, true, OverTheLook),
    MusicLook("macOS light", null, Look.Macos, false, OverTheLook),
    MusicLook("macOS 26 glass dark", null, Look.MacosGlass, true, OverTheLook),
    MusicLook("GNOME dark", null, Look.Gnome, true, OverTheLook),
    MusicLook("GNOME light", null, Look.Gnome, false, OverTheLook),
    MusicLook("This system", null, Look.Native, true, OverTheLook),
)
