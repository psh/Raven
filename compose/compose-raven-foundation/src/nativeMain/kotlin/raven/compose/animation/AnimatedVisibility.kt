package raven.compose.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import raven.compose.animation.core.Animatable
import raven.compose.animation.core.spring
import raven.compose.foundation.layout.Box
import raven.compose.ui.Modifier
import raven.compose.ui.alpha
import raven.compose.ui.appearTransition

/**
 * Shows [content] while [visible], in a Box placed by [modifier]: it arrives with [enter], played
 * as one of Raven's appear transitions, and leaves with [exit], a fade, after which it is removed.
 * Content visible from the start is shown without its transition, as in Compose. Becoming visible
 * again while fading out brings it back, without the transition.
 */
@Composable
public fun AnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn(),
    exit: ExitTransition = fadeOut(),
    label: String = "AnimatedVisibility",
    content: @Composable () -> Unit,
) {
    val alpha = remember { Animatable(1f) }
    // Shown from the time it is visible until its exit has ended.
    var shown by remember { mutableStateOf(visible) }
    // Whether it has been hidden since it was first composed: an appearance after that plays `enter`.
    var hiddenOnce by remember { mutableStateOf(!visible) }
    val currentExit by rememberUpdatedState(exit)
    LaunchedEffect(visible) {
        if (visible) {
            shown = true
            // An exit cut short: the content comes back.
            if (alpha.value < 1f) alpha.animateTo(1f, currentExit.fade?.spec ?: spring())
        } else if (shown) {
            hiddenOnce = true
            currentExit.fade?.let { alpha.animateTo(it.targetAlpha, it.spec) }
            shown = false
            alpha.snapTo(1f)
        }
    }
    if (visible || shown) {
        val transition = enter.appearTransition.takeIf { hiddenOnce }
        val appear = if (transition != null) Modifier.appearTransition(transition) else Modifier
        // A spring can overshoot a little past either end.
        Box(modifier.then(appear).alpha(alpha.value.coerceIn(0f, 1f))) { content() }
    }
}
