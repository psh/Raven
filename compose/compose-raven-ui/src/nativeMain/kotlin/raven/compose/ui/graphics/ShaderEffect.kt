@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui.graphics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pointed
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import raven.cinterop.REffect
import raven.cinterop.RShaderLook
import raven.cinterop.R_EFFECT_AURORA
import raven.cinterop.R_EFFECT_GRAIN
import raven.cinterop.R_EFFECT_MESH
import raven.cinterop.R_EFFECT_NEON
import raven.cinterop.R_EFFECT_PLASMA
import raven.cinterop.R_EFFECT_RIPPLE
import raven.cinterop.R_EFFECT_SHIMMER
import raven.cinterop.R_EFFECT_SPOTLIGHT
import raven.cinterop.R_FALSE
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_SHADER_ANIMATED
import raven.cinterop.R_SHADER_BACKDROP
import raven.cinterop.R_TRUE
import raven.cinterop.Reffect_get
import raven.cinterop.Rid
import raven.cinterop.Rshader_create
import raven.cinterop.Rshader_create_from_file
import raven.cinterop.Rshader_look_defaults
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.LocalRavenHost
import raven.compose.ui.RavenHost

/** Raven's built-in effects. */
public enum class Effect(internal val raven: REffect) {
    /** Soft lights that drift slowly: color, color2; params speed, scale, intensity. */
    Aurora(R_EFFECT_AURORA),

    /** A band of light sweeping across, for loading placeholders: color the base, color2 the light; params speed, width, angle. */
    Shimmer(R_EFFECT_SHIMMER),

    /** A glowing edge whose colours run around the shape: color, color2; params speed, width, glow. */
    Neon(R_EFFECT_NEON),

    /** Rings that ripple what is behind, like water: color the tint; params speed, strength, density, centre x. */
    Ripple(R_EFFECT_RIPPLE),

    /** Bands of colour flowing into each other: color, color2; params speed, scale, contrast. */
    Plasma(R_EFFECT_PLASMA),

    /** A gradient with the grain of film, color at the top left to color2 at the bottom right; params grain, angle. */
    Grain(R_EFFECT_GRAIN),

    /** A soft light at a point: color the light, color2 the background; params x, y, radius (0 to 1). */
    Spotlight(R_EFFECT_SPOTLIGHT),

    /** Four colours blended across the surface, slowly moving: color, color2 and their mix; params speed. */
    Mesh(R_EFFECT_MESH),
}

/**
 * A fragment shader drawn as an effect: on a `Surface` (its background) or by `DrawScope.drawShader`.
 * The shader is made once per host, the first time it is drawn, and kept until the host closes.
 */
@Immutable
public class ShaderEffect private constructor(private val source: Source) {
    private sealed interface Source
    private data class Builtin(val effect: Effect) : Source
    private data class File(val path: String, val flags: UInt) : Source
    private class Bytes(val spirv: ByteArray, val flags: UInt) : Source {
        // One array is one shader: its contents aren't compared.
        override fun equals(other: Any?): Boolean = other is Bytes && spirv === other.spirv && flags == other.flags

        override fun hashCode(): Int = spirv.hashCode() * 31 + flags.hashCode()
    }

    /** The shader in [host]; `R_INVALID_ID` when the code isn't a shader, which Raven reports. */
    @InternalComposeRavenApi
    public fun shader(host: RavenHost): Rid = host.caches.getOrPut(ShaderKey(this)) {
        val shader = when (source) {
            is Builtin -> Reffect_get(source.effect.raven)
            is File -> Rshader_create_from_file(source.path, source.flags)
            is Bytes -> source.spirv.usePinned { Rshader_create(it.addressOf(0), source.spirv.size.toULong(), source.flags) }
        }
        ShaderHandle(shader)
    }.shader

    override fun equals(other: Any?): Boolean = other is ShaderEffect && source == other.source

    override fun hashCode(): Int = source.hashCode()

    override fun toString(): String = when (source) {
        is Builtin -> "ShaderEffect.builtin(${source.effect})"
        is File -> "ShaderEffect.fromFile(${source.path})"
        is Bytes -> "ShaderEffect.fromBytes(${source.spirv.size} bytes)"
    }

    public companion object {
        /** One of Raven's built-in effects. */
        public fun builtin(effect: Effect): ShaderEffect = ShaderEffect(Builtin(effect))

        /**
         * The SPIR-V shader in the file at [path], written with `raven_effect.glsl`. [backdrop] says it
         * reads what is drawn behind it, and [animated] that it changes with time, so its window keeps
         * drawing while it is shown.
         */
        public fun fromFile(path: String, backdrop: Boolean = false, animated: Boolean = false): ShaderEffect =
            ShaderEffect(File(path, flags(backdrop, animated)))

        /** The SPIR-V shader [spirv], as [fromFile] reads one. Two effects of one array are one shader. */
        public fun fromBytes(spirv: ByteArray, backdrop: Boolean = false, animated: Boolean = false): ShaderEffect =
            ShaderEffect(Bytes(spirv, flags(backdrop, animated)))

        private fun flags(backdrop: Boolean, animated: Boolean): UInt =
            (if (backdrop) R_SHADER_BACKDROP else 0u) or (if (animated) R_SHADER_ANIMATED else 0u)
    }
}

/** The shader of [effect] in this composition's host, or `R_INVALID_ID` for none. */
@InternalComposeRavenApi
@Composable
public fun rememberShader(effect: ShaderEffect?): Rid {
    val host = LocalRavenHost.current
    return remember(host, effect) { effect?.shader(host) ?: R_INVALID_ID }
}

private data class ShaderKey(val effect: ShaderEffect)

private class ShaderHandle(val shader: Rid)

/**
 * How an effect is drawn: its two colours and four numbers (their meaning is the effect's; see
 * [Effect]), each the effect's own when left unspecified. [animate] null moves as the shader does,
 * and true or false forces it; [pointer] false keeps the effect from following the pointer. A changed
 * style only replaces the look, so an effect whose colours follow state keeps moving unbroken.
 */
@Immutable
public data class EffectStyle(
    val color: Color = Color.Unspecified,
    val color2: Color = Color.Unspecified,
    val params: List<Float>? = null,
    val animate: Boolean? = null,
    val pointer: Boolean = true,
) {
    init {
        require(params == null || params.size <= 4) { "an effect has four params at most, not ${params?.size}" }
    }

    /** Fills [look] with the shader's defaults, then this style, with corners of [radius]. */
    @InternalComposeRavenApi
    public fun fill(look: CPointer<RShaderLook>, shader: Rid, radius: Float) {
        Rshader_look_defaults(shader, look)
        val values = look.pointed
        if (color.isSpecified) color.toRaven { r, g, b, a -> values.color.setAll(r, g, b, a) }
        if (color2.isSpecified) color2.toRaven { r, g, b, a -> values.color2.setAll(r, g, b, a) }
        params?.forEachIndexed { i, value -> values.params[i] = value }
        values.radius = radius
        values.animate = when (animate) {
            null -> -1
            true -> R_TRUE
            false -> R_FALSE
        }
        values.pointer = if (pointer) R_TRUE else R_FALSE
    }
}

private fun CPointer<kotlinx.cinterop.FloatVar>.setAll(r: Float, g: Float, b: Float, a: Float) {
    this[0] = r
    this[1] = g
    this[2] = b
    this[3] = a
}
