package raven.compose.ui.platform

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import raven.cinterop.RAccessibilitySettings
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Raccessibility_follow_system
import raven.cinterop.Raccessibility_get_high_contrast
import raven.cinterop.Raccessibility_get_reduce_motion
import raven.cinterop.Raccessibility_get_system_settings
import raven.cinterop.Raccessibility_get_text_scale
import raven.cinterop.Raccessibility_is_screen_reader_active
import raven.cinterop.Raccessibility_set_focus_always_visible
import raven.cinterop.Raccessibility_set_high_contrast
import raven.cinterop.Raccessibility_set_reduce_motion
import raven.cinterop.Raccessibility_set_text_scale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The accessibility settings the app runs with, as Raven has them: the system's, which the host
 * follows unless `application(followSystemAccessibility = false)`, or what the app set. Content
 * reads them to adapt what Raven doesn't do by itself, such as an animation of its own to skip, or
 * a decoration to drop for contrast.
 */
@Immutable
public data class AccessibilitySettings(
    /** Nothing should move by itself: Raven's animations are off (`RavenAnimation.enabled` is false). */
    val reduceMotion: Boolean,
    /** The user asked for more contrast; Raven's looks show their high-contrast colours. */
    val highContrast: Boolean,
    /** How much larger the user wants text, 1 being normal; Raven sizes its text by it. */
    val textScale: Float,
    /** A screen reader is running (VoiceOver, Narrator, NVDA, Orca). Checked every 2 seconds. */
    val isScreenReaderActive: Boolean,
) {
    public companion object {
        /** No preference: what the content sees outside a window. */
        public val Default: AccessibilitySettings = AccessibilitySettings(
            reduceMotion = false, highContrast = false, textScale = 1f, isScreenReaderActive = false,
        )
    }
}

/** The [AccessibilitySettings] the app runs with. Its readers recompose when one changes. */
public val LocalAccessibilitySettings: ProvidableCompositionLocal<AccessibilitySettings> =
    compositionLocalOf { AccessibilitySettings.Default }

/** The system's settings, as the window system reports them. */
internal data class SystemAccessibility(
    val textScale: Float,
    val highContrast: Boolean,
    val reduceMotion: Boolean,
    val screenReader: Boolean,
)

/**
 * The calls that ask the system, which a headless Raven answers with nothing: the tests replace
 * [systemAccessibilityCalls] to play a system whose settings change.
 */
internal open class SystemAccessibilityCalls {
    /** Whether a headless host follows the system too; only a test's fake system does. */
    open val followsWhenHeadless: Boolean = false

    /** How often the screen reader is checked; the other settings are read when the app is activated. */
    open val screenReaderInterval: Duration = 2.seconds

    /** Applies all of the system's settings, at start. */
    open fun followSystem() = Raccessibility_follow_system()

    open fun readSystem(): SystemAccessibility = memScoped {
        val settings = alloc<RAccessibilitySettings>()
        Raccessibility_get_system_settings(settings.ptr)
        SystemAccessibility(settings.text_scale, settings.high_contrast == R_TRUE, settings.reduce_motion == R_TRUE, settings.screen_reader == R_TRUE)
    }

    open fun isScreenReaderActive(): Boolean = Raccessibility_is_screen_reader_active() == R_TRUE
}

internal var systemAccessibilityCalls: SystemAccessibilityCalls = SystemAccessibilityCalls()

/**
 * The host's view of the accessibility settings. [settings] is read from Raven once a frame. While
 * it follows the system, it applies all of the system's settings at start, and when the app is
 * activated (the user may have come back from the system's settings) it reads them again and
 * applies only those whose system value changed, so a setting the app changed itself stays until
 * the system's does. Reading the system is slow on Linux (`gsettings`), so it isn't done each frame.
 */
internal class AccessibilityFollower(private val followSystem: Boolean) {
    private val calls = systemAccessibilityCalls
    private var system: SystemAccessibility? = null
    private var screenReader = false
    private var screenReaderChecked: TimeMark? = null

    var settings: AccessibilitySettings by mutableStateOf(AccessibilitySettings.Default)
        private set

    fun start() {
        if (followSystem) {
            calls.followSystem()
            system = calls.readSystem()
        }
        checkScreenReader()
        settings = readRaven()
    }

    /** The application became the active one: none of its windows was, and one is. */
    fun onAppActivated() {
        val before = system ?: return
        val now = calls.readSystem()
        system = now
        if (now.textScale != before.textScale) Raccessibility_set_text_scale(now.textScale)
        if (now.highContrast != before.highContrast) Raccessibility_set_high_contrast(now.highContrast.toRaven())
        if (now.reduceMotion != before.reduceMotion) Raccessibility_set_reduce_motion(now.reduceMotion.toRaven())
        // As Raccessibility_follow_system: with a screen reader, the focus is always shown.
        if (now.screenReader && !before.screenReader) Raccessibility_set_focus_always_visible(R_TRUE)
    }

    /** Once a frame, after the windows: what Raven has now, and the screen reader when it is time. */
    fun frame() {
        val checked = screenReaderChecked
        if (checked == null || checked.elapsedNow() >= calls.screenReaderInterval) checkScreenReader()
        val read = readRaven()
        if (read != settings) settings = read
    }

    private fun checkScreenReader() {
        screenReader = calls.isScreenReaderActive()
        screenReaderChecked = TimeSource.Monotonic.markNow()
    }

    private fun readRaven() = AccessibilitySettings(
        reduceMotion = Raccessibility_get_reduce_motion() == R_TRUE,
        highContrast = Raccessibility_get_high_contrast() == R_TRUE,
        textScale = Raccessibility_get_text_scale(),
        isScreenReaderActive = screenReader,
    )
}

private fun Boolean.toRaven() = if (this) R_TRUE else R_FALSE
