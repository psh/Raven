@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.animation.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Rget_animation_speed
import raven.cinterop.Rget_animations_enabled
import raven.cinterop.Rset_animation_speed
import raven.cinterop.Rset_animations_enabled
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.RavenHost

/**
 * The animation settings of the whole app, Raven's and compose-raven's alike, as snapshot state.
 * They start as Raven has them when the application starts, which follows the system's reduced
 * motion, and follow Raven if it changes them (the system's setting changing). Set them on the UI
 * thread, while the application runs: a value set before it starts is replaced by Raven's.
 */
public object RavenAnimation {
    private var speedState by mutableStateOf(1f)
    private var enabledState by mutableStateOf(true)

    /**
     * How fast every animation runs: 1 is normal, 0.25 a slow motion. It scales the time of
     * Raven's animations (`Rset_animation_speed`) and of compose-raven's, as Compose's
     * `MotionDurationScale` does. It must be positive.
     */
    public var speed: Float
        get() = speedState
        set(value) {
            require(value > 0f) { "the animation speed must be positive, not $value" }
            speedState = value
            if (RavenHost.running != null) Rset_animation_speed(value)
        }

    /**
     * Whether anything animates. False, every change is immediate: Raven's springs and transitions
     * (`Rset_animations_enabled`), and compose-raven's animations, which jump to their targets.
     * It is what reduced motion turns off.
     */
    public var enabled: Boolean
        get() = enabledState
        set(value) {
            enabledState = value
            if (RavenHost.running != null) Rset_animations_enabled(if (value) R_TRUE else R_FALSE)
        }

    /** Reads Raven's settings, which the host does as it starts and once a frame. */
    internal fun followRaven() {
        val speed = Rget_animation_speed()
        if (speed != speedState) speedState = speed
        val enabled = Rget_animations_enabled() == R_TRUE
        if (enabled != enabledState) enabledState = enabled
    }
}
