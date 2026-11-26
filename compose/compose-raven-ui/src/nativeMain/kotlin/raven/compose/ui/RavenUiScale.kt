@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.Rget_ui_scale
import raven.cinterop.Rset_ui_scale

/**
 * The scale of the whole interface, on top of each monitor's own, as an app's zoom setting sets it
 * (`Rset_ui_scale`): 1.25 draws everything a quarter larger. Every window is laid out again, its
 * `LocalDensity` follows, and its open popups and menus close. A window keeps its size in logical
 * units, so it shows less at a larger scale.
 *
 * [value] is snapshot state, so a composable that shows it (a "125%" label) recomposes. It follows
 * Raven once a frame, which changes it while it draws a preview. A value set before the
 * application starts applies when it starts, so a saved zoom can be restored before the first
 * frame.
 */
public object RavenUiScale {
    private var state by mutableFloatStateOf(1f)
    private var pending: Float? = null

    /** From 0.25 to 4; 1 is the monitor's own scale. */
    public var value: Float
        get() = state
        set(value) {
            require(value in 0.25f..4f) { "the UI scale must be from 0.25 to 4, not $value" }
            state = value
            if (RavenHost.running != null) Rset_ui_scale(value) else pending = value
        }

    /** At start: the value set before it, if any, then Raven's. */
    internal fun start() {
        pending?.let { Rset_ui_scale(it) }
        pending = null
        followRaven()
    }

    /** Reads Raven's scale, which the host does once a frame. */
    internal fun followRaven() {
        val scale = Rget_ui_scale()
        if (scale != state) state = scale
    }
}
