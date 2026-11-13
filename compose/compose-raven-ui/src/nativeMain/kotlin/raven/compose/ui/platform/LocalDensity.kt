package raven.compose.ui.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.Rid
import raven.cinterop.Rwindow_get_scale
import raven.compose.ui.unit.Density

/**
 * The [Density] of the window the content is in: its scale (`Rwindow_get_scale`). It changes when
 * the window moves to a monitor of another scale, or Raven's interface scale changes, and its
 * readers recompose.
 */
public val LocalDensity: ProvidableCompositionLocal<Density> = compositionLocalOf { error("no Window") }

/**
 * A window's density as snapshot state. Raven reports no change of scale, so the window has it
 * [follow] the scale once a frame.
 */
internal class WindowDensity(private val window: Rid) {
    var current: Density by mutableStateOf(Density(Rwindow_get_scale(window)))
        private set

    fun follow() {
        val scale = Rwindow_get_scale(window)
        if (scale != current.density) current = Density(scale)
    }
}
