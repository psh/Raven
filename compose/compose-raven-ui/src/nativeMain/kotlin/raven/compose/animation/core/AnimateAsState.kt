package raven.compose.animation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import raven.compose.ui.geometry.Offset
import raven.compose.ui.graphics.Color
import raven.compose.ui.unit.Dp

/**
 * A Float that animates to [targetValue] with [animationSpec] each time the target changes, as
 * Compose's: it starts at the first target, without animating, and a new target mid-way changes
 * course, keeping the velocity. [finishedListener] gets the value each time an animation ends.
 */
@Composable
public fun animateFloatAsState(
    targetValue: Float,
    animationSpec: AnimationSpec<Float> = spring(),
    visibilityThreshold: Float = Spring.DefaultDisplacementThreshold,
    label: String = "FloatAnimation",
    finishedListener: ((Float) -> Unit)? = null,
): State<Float> = animateValueAsState(targetValue, FloatConverter, animationSpec, visibilityThreshold, finishedListener)

/** A [Dp] that animates to [targetValue], as [animateFloatAsState] does. */
@Composable
public fun animateDpAsState(
    targetValue: Dp,
    animationSpec: AnimationSpec<Dp> = spring(),
    label: String = "DpAnimation",
    finishedListener: ((Dp) -> Unit)? = null,
): State<Dp> = animateValueAsState(targetValue, DpConverter, animationSpec, null, finishedListener)

/**
 * A [Color] that animates to [targetValue], as [animateFloatAsState] does, channel by channel in
 * sRGB. `Color.Unspecified` has no channels, so it can't be a target.
 */
@Composable
public fun animateColorAsState(
    targetValue: Color,
    animationSpec: AnimationSpec<Color> = spring(),
    label: String = "ColorAnimation",
    finishedListener: ((Color) -> Unit)? = null,
): State<Color> = animateValueAsState(targetValue, ColorConverter, animationSpec, null, finishedListener)

/** An [Offset] that animates to [targetValue], as [animateFloatAsState] does. */
@Composable
public fun animateOffsetAsState(
    targetValue: Offset,
    animationSpec: AnimationSpec<Offset> = spring(),
    label: String = "OffsetAnimation",
    finishedListener: ((Offset) -> Unit)? = null,
): State<Offset> = animateValueAsState(targetValue, OffsetConverter, animationSpec, null, finishedListener)

@Composable
private fun <T> animateValueAsState(
    targetValue: T,
    converter: VectorConverter<T>,
    animationSpec: AnimationSpec<T>,
    visibilityThreshold: T?,
    finishedListener: ((T) -> Unit)?,
): State<T> {
    val animatable = remember { Animatable(targetValue, converter, visibilityThreshold) }
    val listener by rememberUpdatedState(finishedListener)
    val spec by rememberUpdatedState(animationSpec)
    // The targets in the order they were composed; only the last one waiting counts.
    val targets = remember { Channel<T>(Channel.CONFLATED) }
    SideEffect { targets.trySend(targetValue) }
    LaunchedEffect(targets) {
        for (target in targets) {
            val latest = targets.tryReceive().getOrNull() ?: target
            // Launched, so the next target cancels this animation instead of waiting for it.
            launch {
                if (latest != animatable.targetValue) {
                    animatable.animateTo(latest, spec, animatable.velocityVector())
                    listener?.invoke(animatable.value)
                }
            }
        }
    }
    return animatable.asState()
}
