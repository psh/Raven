@file:OptIn(InternalComposeRavenApi::class)

package raven.sample.effects

import raven.cinterop.R_EFFECT_AURORA
import raven.cinterop.R_EFFECT_GRAIN
import raven.cinterop.R_EFFECT_MESH
import raven.cinterop.R_EFFECT_NEON
import raven.cinterop.R_EFFECT_PLASMA
import raven.cinterop.R_EFFECT_RIPPLE
import raven.cinterop.R_EFFECT_SHIMMER
import raven.cinterop.R_EFFECT_SPOTLIGHT
import raven.cinterop.R_INVALID_ID
import raven.cinterop.Rborder_get_effect
import raven.cinterop.Reffect_get
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.EffectStyle
import raven.compose.ui.test.runRavenTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class EffectsTest {
    @Test
    fun eachCardHasItsBuiltInEffect() = runRavenTest {
        setContent { EffectsScreen() }
        val expected = mapOf(
            "aurora" to R_EFFECT_AURORA, "mesh" to R_EFFECT_MESH, "plasma" to R_EFFECT_PLASMA, "grain" to R_EFFECT_GRAIN,
            "spotlight" to R_EFFECT_SPOTLIGHT, "shimmer" to R_EFFECT_SHIMMER, "neon" to R_EFFECT_NEON, "ripple" to R_EFFECT_RIPPLE,
            "liquid-under" to R_EFFECT_MESH,
        )
        for ((tag, effect) in expected) {
            assertEquals(Reffect_get(effect), Rborder_get_effect(onNodeWithTag(tag).fetchWidget()), tag)
        }
    }

    @Test
    fun bothOfTheApplicationsShadersLoad() = runRavenTest {
        setContent { EffectsScreen() }
        val builtins = listOf(R_EFFECT_AURORA, R_EFFECT_MESH, R_EFFECT_RIPPLE).map { Reffect_get(it) }
        for (tag in listOf("wave", "liquid")) {
            val shader = Rborder_get_effect(onNodeWithTag(tag).fetchWidget())
            assertNotEquals(R_INVALID_ID, shader, "$tag.spv is read from $EXAMPLE_DIR")
            assertEquals(false, shader in builtins, tag)
        }
    }

    @Test
    fun theStylesAreTheCExamples() {
        // EffectColor="#64D2FF20" EffectParams="1,5,1.4,0.7"; the XML's colours are RGBA.
        assertEquals(EffectStyle(color = Color(0x64 / 255f, 0xD2 / 255f, 0xFF / 255f, 0x20 / 255f), params = listOf(1f, 5f, 1.4f, 0.7f)), rippleStyle)
        assertEquals(EffectStyle(color = Color(0x1C / 255f, 0x1C / 255f, 0x1E / 255f), color2 = Color(0x0A / 255f, 0x84 / 255f, 0xFF / 255f)), waveStyle)
        assertEquals(EffectStyle(color = Color(1f, 1f, 1f, 0x18 / 255f), params = listOf(8f)), liquidStyle)
    }
}
