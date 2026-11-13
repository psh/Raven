package raven.compose.ui.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import raven.compose.ui.focus.FocusManager

/** The [FocusManager] of the window the content is in. */
public val LocalFocusManager: ProvidableCompositionLocal<FocusManager> = staticCompositionLocalOf { error("no Window") }
