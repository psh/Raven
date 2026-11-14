package raven.sample.animations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import raven.compose.animation.TransitionRequester
import raven.compose.animation.core.Animatable
import raven.compose.animation.core.RavenAnimation
import raven.compose.animation.core.RavenMotion
import raven.compose.animation.core.SpringSpec
import raven.compose.animation.transitionRequester
import raven.compose.foundation.Button
import raven.compose.foundation.Canvas
import raven.compose.foundation.Island
import raven.compose.foundation.OutlinedButton
import raven.compose.foundation.Slider
import raven.compose.foundation.Surface
import raven.compose.foundation.Switch
import raven.compose.foundation.Text
import raven.compose.foundation.TextButton
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.Spacer
import raven.compose.foundation.layout.VerticalScroll
import raven.compose.foundation.layout.fillMaxHeight
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.height
import raven.compose.foundation.layout.padding
import raven.compose.foundation.layout.size
import raven.compose.foundation.layout.width
import raven.compose.ui.Alignment
import raven.compose.ui.AppearTransition
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.alpha
import raven.compose.ui.appearTransition
import raven.compose.ui.application
import raven.compose.ui.geometry.Offset
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.input.pointer.PointerEventType
import raven.compose.ui.input.pointer.onPointerEvent
import raven.compose.ui.rememberWindowState
import raven.compose.ui.testTag
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.tooltip
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp
import kotlin.time.Duration.Companion.milliseconds

// A port of Raven's examples/animations: everything that moves, and how to ask for it.
//
//   transitions   Modifier.appearTransition (each time a widget appears) and a
//                 TransitionRequester (once, now), the C example's Rwidget_set_transition and
//                 Rwidget_play_transition
//   springs       four Animatables with RavenMotion specs drawn by a Canvas, where the C example
//                 has RSpring and Rdraw_spring in a custom widget; a click kicks them with an
//                 initial velocity
//   island        Window(island), unfolded for a moment when a card comes or goes, as
//                 Risland_expand_for does
//   tuning        RavenAnimation.speed and .enabled
//
// The controls animate by themselves (press a button, flip the switch): that needs no code at all.

fun main() = application {
    AnimationsWindow(onCloseRequest = ::exitApplication)
}

/** The window, its island and its content sharing one [Animations]. */
@Composable
internal fun AnimationsWindow(onCloseRequest: () -> Unit, state: Animations = remember { Animations() }) {
    Window(
        onCloseRequest = onCloseRequest,
        state = rememberWindowState(size = DpSize(960.dp, 640.dp)),
        title = "Raven - animations",
        minimumSize = DpSize(720.dp, 480.dp),
        island = { MotionIsland(state) },
    ) { AnimationsScreen(state) }
}

/** The transitions the list plays, with what each looks like. */
internal val Transitions = listOf(
    AppearTransition.Fade to "Becomes opaque",
    AppearTransition.Pop to "Grows and bounces a little past its size",
    AppearTransition.Zoom to "Shrinks from a larger size",
    AppearTransition.FlyUp to "Flies in from below",
    AppearTransition.FlyDown to "Flies in from above",
    AppearTransition.FlyLeft to "Flies in from the right",
    AppearTransition.FlyRight to "Flies in from the left",
    AppearTransition.Drop to "Falls, squashes on landing and wobbles",
    AppearTransition.SlideLeft to "A page replacing another",
)

private val CardColors = listOf(0xFFF76B15, 0xFF46A758, 0xFFD6409F, 0xFF05A2C2, 0xFF8E4EC6, 0xFFE5484D).map(::Color)

/** A card of the list: its number, and the requester that plays it again for Play them all. */
internal class CardData(val number: Int, val appearDelay: Int) {
    val color: Color = CardColors[(number - 1) % CardColors.size]
    val requester = TransitionRequester()
}

/** The state the window's content and its island share: one per application. */
internal class Animations {
    var cardTitle by mutableStateOf("Pop")
    val card = TransitionRequester()
    val cards = mutableStateListOf<CardData>()
    private var nextCard = 0

    var islandExpanded by mutableStateOf(false)

    /** The springs playground, once it is shown. */
    var springs: Springs? = null

    // Each time a card comes or goes the island unfolds, then folds back after a while.
    var islandShows by mutableIntStateOf(0)
        private set

    init {
        // The first cards arrive one after the other.
        repeat(3) { addCard(appearDelay = 350 + 80 * it) }
    }

    fun addCard(appearDelay: Int = 0) {
        if (cards.size < MAX_CARDS) cards += CardData(++nextCard, appearDelay)
    }

    fun onCardsChanged() {
        islandExpanded = true
        islandShows++
    }

    fun play(transition: AppearTransition) {
        cardTitle = transition.name
        card.play(transition)
    }

    /** Every card of the list arrives again, one after the other, and the stage card drops. */
    fun playAll() {
        cards.forEachIndexed { i, card -> card.requester.play(Transitions[(i + 1) % Transitions.size].first, (60 * i).milliseconds) }
        cardTitle = AppearTransition.Drop.name
        card.play(AppearTransition.Drop)
    }

    companion object {
        const val MAX_CARDS = 40
    }
}

@Composable
internal fun MotionIsland(state: Animations) {
    LaunchedEffect(state.islandShows) {
        if (state.islandShows > 0) {
            delay(1600)
            state.islandExpanded = false
        }
    }
    Island(
        expanded = state.islandExpanded,
        onExpandedChange = { state.islandExpanded = it },
        modifier = Modifier.padding(top = 8.dp),
        folded = {
            val count = state.cards.size
            Text(if (count == 1) "1 card" else "$count cards", Modifier.padding(horizontal = 14.dp, vertical = 6.dp).testTag("island.count"))
        },
        unfolded = {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp).width(300.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Motion", fontSize = 17.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Speed", Modifier.width(60.dp))
                    Slider(RavenAnimation.speed, { RavenAnimation.speed = it }, Modifier.weight(1f).testTag("speed"), valueRange = 0.1f..2f)
                    Text("${(RavenAnimation.speed * 10).toInt() / 10.0}x", Modifier.width(40.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    // For the users who ask their system for less motion.
                    Switch(!RavenAnimation.enabled, { RavenAnimation.enabled = !it }, Modifier.testTag("reduce"))
                    Text("Reduce motion")
                }
            }
        },
    )
}

/** A panel of the window: a card of the theme's, arriving with [appear] after [delay] ms. */
@Composable
private fun Panel(modifier: Modifier, appear: AppearTransition, delay: Int = 0, content: @Composable () -> Unit) {
    Surface(modifier.appearTransition(appear, delay.milliseconds), shape = RoundedCornerShape(14.dp), content = content)
}

@Composable
internal fun AnimationsScreen(state: Animations) {
    Row(Modifier.fillMaxSize().padding(start = 16.dp, top = 56.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel(Modifier.width(170.dp).fillMaxHeight(), AppearTransition.FlyRight) { TransitionList(state) }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Panel(Modifier.weight(1f).fillMaxWidth(), AppearTransition.FlyDown, delay = 80) { Stage(state) }
            Panel(Modifier.height(190.dp).fillMaxWidth(), AppearTransition.FlyUp, delay = 160) { SpringsPanel(state) }
        }
        Panel(Modifier.width(230.dp).fillMaxHeight(), AppearTransition.FlyLeft, delay = 240) { CardList(state) }
    }
}

@Composable
private fun TransitionList(state: Animations) {
    Column(Modifier.padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Transitions", Modifier.padding(start = 6.dp, top = 2.dp, end = 6.dp, bottom = 8.dp), fontSize = 17.sp)
        for ((transition, tip) in Transitions) {
            TextButton(onClick = { state.play(transition) }, Modifier.fillMaxWidth().tooltip(tip).testTag("play.${transition.name}")) {
                Text(transition.name)
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = state::playAll, Modifier.fillMaxWidth().testTag("play.all")) { Text("Play them all") }
    }
}

@Composable
private fun Stage(state: Animations) {
    Box(Modifier.padding(16.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            Modifier.size(240.dp, 150.dp).transitionRequester(state.card).appearTransition(AppearTransition.Pop, 300.milliseconds).testTag("card"),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF3B82F6),
            shadowElevation = 26.dp,
        ) {
            Box(Modifier.padding(18.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.cardTitle, Modifier.testTag("card.title"), fontSize = 26.sp, color = Color.White)
                    Text("Choose a transition on the left", color = Color(0xC0FFFFFF))
                }
            }
        }
    }
}

/** The four kinds of Raven's motion, each drawing a ball on a track. */
private val Motions = listOf<Triple<String, SpringSpec<Nothing>, Color>>(
    Triple("Snappy", RavenMotion.Snappy, Color(0.23f, 0.51f, 0.96f)),
    Triple("Smooth", RavenMotion.Smooth, Color(0.27f, 0.65f, 0.35f)),
    Triple("Bouncy", RavenMotion.Bouncy, Color(0.97f, 0.42f, 0.08f)),
    Triple("Wobbly", RavenMotion.Wobbly, Color(0.84f, 0.25f, 0.62f)),
)

private const val LABEL_WIDTH = 70f

private fun Color.withAlpha(alpha: Float) = Color(red, green, blue, alpha)

/**
 * The springs playground: four balls pulled to the pointer, each by one of Raven's motions, so
 * only the way they get there differs; a click kicks them to the right.
 */
internal class Springs(private val scope: CoroutineScope) {
    /** Where each ball is along its track, in dp. */
    val positions = List(Motions.size) { Animatable(0f) }

    /** How much each ball glows, while the pointer is over the tracks. */
    val glows = List(Motions.size) { Animatable(0f) }

    /** Where on the tracks the balls are pulled to, 0 to 1. */
    var target = 0.5f
        private set

    /** The length of a track, from the last drawing. */
    var track = 0f

    fun moveTo(x: Float, width: Float) {
        target = ((x - LABEL_WIDTH - 8f) / (width - LABEL_WIDTH - 16f)).coerceIn(0f, 1f)
        pull()
    }

    fun pull(kick: Float = 0f) {
        positions.forEachIndexed { i, ball ->
            scope.launch { ball.animateTo(target * track, Motions[i].second, initialVelocity = ball.velocity + kick) }
        }
    }

    fun glow(on: Boolean) {
        glows.forEach { glow -> scope.launch { glow.animateTo(if (on) 1f else 0f, RavenMotion.Bouncy) } }
    }

    companion object {
        /** A click's push to the right, in dp per second. */
        const val KICK = 1400f
    }
}

@Composable
private fun SpringsPanel(state: Animations) {
    val scope = rememberCoroutineScope()
    val springs = remember { Springs(scope).also { state.springs = it } }
    val text = RavenTheme.colors.text
    Column(Modifier.padding(14.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Springs", fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text("Move the pointer over the tracks; click to kick", Modifier.alpha(0.6f))
        }
        var width by remember { mutableStateOf(0f) }
        Canvas(
            Modifier.weight(1f).fillMaxWidth().testTag("springs")
                .onPointerEvent(PointerEventType.Move) { springs.moveTo(it.position.x, width) }
                .onPointerEvent(PointerEventType.Enter) { springs.glow(on = true) }
                .onPointerEvent(PointerEventType.Exit) { springs.glow(on = false) }
                .onPointerEvent(PointerEventType.Press) {
                    springs.pull(kick = Springs.KICK)
                    it.consume()
                },
        ) {
            width = size.width
            val row = size.height / Motions.size
            val track = size.width - LABEL_WIDTH - 16f
            if (track != springs.track) {
                // The first drawing, or a new width: the balls go to their place on the new track.
                springs.track = track
                springs.pull()
            }
            Motions.forEachIndexed { i, (name, _, color) ->
                val y = row * (i + 0.5f)
                val x = LABEL_WIDTH + 8f + springs.positions[i].value
                val glow = springs.glows[i].value
                drawText(name, Offset(0f, y - 9f), text.withAlpha(0.8f))
                drawRoundRect(text.withAlpha(0.12f), Offset(LABEL_WIDTH, y - 2f), raven.compose.ui.geometry.Size(track + 16f, 4f), cornerRadius = 2f)
                drawCircle(color.withAlpha(0.25f), 9f + 5f * glow, Offset(x, y))
                drawCircle(color, 8f, Offset(x, y))
            }
        }
    }
}

@Composable
private fun CardList(state: Animations) {
    Column(Modifier.padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Cards", fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Button(onClick = { state.addCard(); state.onCardsChanged() }, Modifier.testTag("cards.add")) { Text("Add") }
            OutlinedButton(onClick = { state.cards.removeLastOrNull(); state.onCardsChanged() }, Modifier.testTag("cards.remove")) { Text("Remove") }
        }
        VerticalScroll(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (card in state.cards) {
                androidx.compose.runtime.key(card.number) {
                    // It pops in each time it appears: now, and again if its panel is hidden and shown.
                    Surface(
                        Modifier.fillMaxWidth().appearTransition(AppearTransition.Pop, card.appearDelay.milliseconds)
                            .transitionRequester(card.requester).testTag("card${card.number}"),
                        shape = RoundedCornerShape(14.dp),
                        color = card.color,
                    ) { Text("Card ${card.number}", Modifier.padding(12.dp), color = Color.White) }
                }
            }
        }
    }
}
