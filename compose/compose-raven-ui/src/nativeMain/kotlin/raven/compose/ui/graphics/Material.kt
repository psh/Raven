package raven.compose.ui.graphics

import raven.cinterop.RMaterial
import raven.cinterop.R_MATERIAL_DEFAULT
import raven.cinterop.R_MATERIAL_FROSTED_GLASS
import raven.cinterop.R_MATERIAL_GLASS
import raven.cinterop.R_MATERIAL_SOLID
import raven.cinterop.R_MATERIAL_TINTED_GLASS

/**
 * What a surface is made of, from the clearest glass to opaque. On glass, a colour's alpha is the
 * strength of its tint.
 */
public enum class Material {
    /** The theme's: removes a widget's own material. */
    Default,

    /** Opaque, filled with the theme's colours. */
    Solid,

    /** Clear glass: what is behind stays sharp enough to recognise, strongly bent at the rim. */
    Glass,

    /** Frosted glass: what is behind is blurred into soft colours. The glass looks' material. */
    FrostedGlass,

    /** Frosted glass coloured by the accent, for what must stand out. */
    TintedGlass,
}

internal fun Material.toRaven(): RMaterial = when (this) {
    Material.Default -> R_MATERIAL_DEFAULT
    Material.Solid -> R_MATERIAL_SOLID
    Material.Glass -> R_MATERIAL_GLASS
    Material.FrostedGlass -> R_MATERIAL_FROSTED_GLASS
    Material.TintedGlass -> R_MATERIAL_TINTED_GLASS
}
