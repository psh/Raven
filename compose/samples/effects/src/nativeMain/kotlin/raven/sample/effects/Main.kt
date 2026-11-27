package raven.sample.effects

import androidx.compose.runtime.Composable
import raven.compose.foundation.Surface
import raven.compose.foundation.Text
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.height
import raven.compose.foundation.layout.padding
import raven.compose.ui.Alignment
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.alpha
import raven.compose.ui.application
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.Effect
import raven.compose.ui.graphics.EffectStyle
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.graphics.ShaderEffect
import raven.compose.ui.rememberWindowState
import raven.compose.ui.testTag
import raven.compose.ui.theme.Look
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp

// A port of Raven's examples/effects: the effects that come with Raven, one card each, and two
// effects of the application.
//
//   Surface(effect = ShaderEffect.builtin(Effect.Aurora))   the C example's <Border Effect="aurora"/>
//   EffectStyle(color, color2, params)                      its EffectColor, EffectColor2, EffectParams
//   ShaderEffect.fromFile("wave.spv", animated = true)      a shader of the application (wave.frag)
//   ShaderEffect.fromFile("liquid.spv", backdrop = true)    one that reads what is behind it
//
// The .spv files are Raven's examples/effects' own, read where they are; the .frag files next to
// them are their source, compiled with glslangValidator (docs/shaders.html is the guide). Card
// stands for the XML's <Widget Name="Card">, and two rows of four for its UniformGrid.

fun main() = application {
    RavenTheme(look = Look.Raven, dark = true) {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(size = DpSize(1180.dp, 720.dp)),
            title = "Raven effects",
        ) { EffectsScreen() }
    }
}

/** The ripple card's rings: a light blue tint; speed, strength, density and centre. */
val rippleStyle: EffectStyle = EffectStyle(color = Color(0x2064D2FF), params = listOf(1f, 5f, 1.4f, 0.7f))

/** wave.spv's two colours. */
val waveStyle: EffectStyle = EffectStyle(color = Color(0xFF1C1C1E), color2 = Color(0xFF0A84FF))

/** liquid.spv's tint and its one param, the strength of the bend. */
val liquidStyle: EffectStyle = EffectStyle(color = Color(0x18FFFFFF), params = listOf(8f))

/** The application's two shaders, from the folder of Raven's examples/effects. */
fun waveEffect(directory: String = EXAMPLE_DIR): ShaderEffect = ShaderEffect.fromFile("$directory/wave.spv", animated = true)

fun liquidEffect(directory: String = EXAMPLE_DIR): ShaderEffect =
    ShaderEffect.fromFile("$directory/liquid.spv", backdrop = true, animated = true)

private val CardShape = RoundedCornerShape(18.dp)
private val CardHeight = 190.dp

@Composable
fun EffectsScreen(directory: String = EXAMPLE_DIR) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Effects", Modifier.weight(1f), fontSize = 28.sp)
            Text(
                "Surface(effect = ...)  -  your own: a .spv written with raven_effect.glsl",
                Modifier.align(Alignment.CenterVertically).alpha(0.6f),
            )
        }
        val builtins = listOf(Effect.Aurora, Effect.Mesh, Effect.Plasma, Effect.Grain, Effect.Spotlight, Effect.Shimmer)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                for (effect in builtins.take(4)) Card(ShaderEffect.builtin(effect), effect.caption, Modifier.weight(1f).height(CardHeight))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                for (effect in builtins.drop(4)) Card(ShaderEffect.builtin(effect), effect.caption, Modifier.weight(1f).height(CardHeight))
                // Neon is an edge: it wants a dark surface inside it.
                Card(ShaderEffect.builtin(Effect.Neon), Effect.Neon.caption, Modifier.weight(1f).height(CardHeight), color = Color(0xFF16161A))
                RippleCard(Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Card(waveEffect(directory), "wave.spv: an effect of the application", Modifier.weight(1f).fillMaxSize(), effectStyle = waveStyle, tag = "wave")
            Box(Modifier.weight(1f).fillMaxSize()) {
                Surface(Modifier.fillMaxSize().testTag("liquid-under"), shape = CardShape, effect = ShaderEffect.builtin(Effect.Mesh)) {}
                // Inset, so the mesh shows round it and through it, bent.
                Card(
                    liquidEffect(directory), "liquid.spv: reads what is behind", Modifier.fillMaxSize().padding(22.dp),
                    effectStyle = liquidStyle, tag = "liquid",
                )
            }
        }
    }
}

/** An effect on a rounded card, its [caption] at the bottom left: the XML's Card. Its size is the [modifier]'s. */
@Composable
fun Card(
    effect: ShaderEffect,
    caption: String,
    modifier: Modifier = Modifier,
    effectStyle: EffectStyle = EffectStyle(),
    color: Color = Color.Unspecified,
    tag: String = caption,
) {
    Surface(modifier.testTag(tag), shape = CardShape, color = color, effect = effect, effectStyle = effectStyle) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            Text(caption, Modifier.padding(16.dp), fontSize = 18.sp)
        }
    }
}

/** Ripple bends what is behind it, so it runs over a card of text. */
@Composable
private fun RippleCard(modifier: Modifier = Modifier) {
    Box(modifier.height(CardHeight)) {
        Surface(Modifier.fillMaxSize(), shape = CardShape, color = Color(0xFF1C1C1E)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Rings run over", fontSize = 22.sp)
                Text("what is behind,", fontSize = 22.sp)
                Text("like water.", color = Color(0xFF64D2FF), fontSize = 22.sp)
            }
        }
        Card(ShaderEffect.builtin(Effect.Ripple), Effect.Ripple.caption, Modifier.fillMaxSize(), effectStyle = rippleStyle)
    }
}

/** An effect's name as the C example writes it. */
val Effect.caption: String get() = name.lowercase()
