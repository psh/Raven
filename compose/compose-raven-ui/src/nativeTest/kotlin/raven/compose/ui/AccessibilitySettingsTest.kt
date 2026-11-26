package raven.compose.ui

import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Raccessibility_get_focus_always_visible
import raven.cinterop.Raccessibility_get_high_contrast
import raven.cinterop.Raccessibility_get_text_scale
import raven.cinterop.Raccessibility_set_high_contrast
import raven.cinterop.Raccessibility_set_reduce_motion
import raven.cinterop.Raccessibility_set_text_scale
import raven.cinterop.Rid
import raven.cinterop.Rwindow_is_active
import raven.compose.animation.core.RavenAnimation
import raven.compose.ui.platform.AccessibilitySettings
import raven.compose.ui.platform.LocalAccessibilitySettings
import raven.compose.ui.platform.SystemAccessibility
import raven.compose.ui.platform.SystemAccessibilityCalls
import raven.compose.ui.platform.systemAccessibilityCalls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

/** LocalAccessibilitySettings, and the host following the system's settings while the app runs. */
class AccessibilitySettingsTest {
    /** A system whose settings the test changes; the host follows it although it is headless. */
    private class FakeSystem(var now: SystemAccessibility) : SystemAccessibilityCalls() {
        var reads = 0
        override val followsWhenHeadless: Boolean = true
        override val screenReaderInterval: Duration = Duration.ZERO

        override fun followSystem() {
            Raccessibility_set_text_scale(now.textScale)
            Raccessibility_set_high_contrast(if (now.highContrast) R_TRUE else R_FALSE)
            Raccessibility_set_reduce_motion(if (now.reduceMotion) R_TRUE else R_FALSE)
        }

        override fun readSystem(): SystemAccessibility {
            reads++
            return now
        }

        override fun isScreenReaderActive(): Boolean = now.screenReader
    }

    /** Windows that are active, as the user would leave and come back to the app, while [active]. */
    private class Activity : WindowCalls() {
        var active = true
        override fun isActive(window: Rid): Boolean = active
    }

    private val plain = SystemAccessibility(textScale = 1f, highContrast = false, reduceMotion = false, screenReader = false)

    private fun withSystem(system: SystemAccessibilityCalls, activity: WindowCalls = Activity(), block: () -> Unit) {
        val previousSystem = systemAccessibilityCalls
        val previousCalls = windowCalls
        systemAccessibilityCalls = system
        windowCalls = activity
        try {
            block()
        } finally {
            systemAccessibilityCalls = previousSystem
            windowCalls = previousCalls
        }
    }

    /** A frame, with what it recomposes. */
    private fun RavenHost.next() {
        drawFrame()
        runUntilIdle()
    }

    @Test
    fun theSettingsAreRavensAndItsReadersRecompose() {
        var seen: AccessibilitySettings? = null
        withHost({ Window(onCloseRequest = {}) { seen = LocalAccessibilitySettings.current } }) {
            assertEquals(AccessibilitySettings.Default, seen, "a headless host follows no system")

            Raccessibility_set_high_contrast(R_TRUE)
            Raccessibility_set_text_scale(1.5f)
            RavenAnimation.enabled = false
            next()
            assertEquals(AccessibilitySettings(reduceMotion = true, highContrast = true, textScale = 1.5f, isScreenReaderActive = false), seen)
            RavenAnimation.enabled = true
        }
    }

    @Test
    fun outsideAWindowTheyAreTheDefault() {
        var seen: AccessibilitySettings? = null
        withHost({ seen = LocalAccessibilitySettings.current })
        assertEquals(AccessibilitySettings.Default, seen)
    }

    @Test
    fun theSystemsSettingsApplyAtStart() {
        val system = FakeSystem(plain.copy(textScale = 1.25f, highContrast = true))
        var seen: AccessibilitySettings? = null
        withSystem(system) {
            withHost({ Window(onCloseRequest = {}) { seen = LocalAccessibilitySettings.current } }) {
                assertEquals(AccessibilitySettings(reduceMotion = false, highContrast = true, textScale = 1.25f, isScreenReaderActive = false), seen)
            }
        }
    }

    @Test
    fun aChangedSystemSettingIsAppliedWhenTheAppComesBack() {
        val system = FakeSystem(plain)
        val activity = Activity()
        var seen: AccessibilitySettings? = null
        withSystem(system, activity) {
            withHost({ Window(onCloseRequest = {}) { seen = LocalAccessibilitySettings.current } }) {
                // The app turns its animations off by hand, as samples/animations' switch does.
                RavenAnimation.enabled = false
                next()

                // The user goes to the system's settings and turns high contrast on.
                activity.active = false
                next()
                system.now = system.now.copy(highContrast = true)
                next()
                assertEquals(false, seen!!.highContrast, "not read while the app is away")

                activity.active = true
                next()
                assertEquals(R_TRUE, Raccessibility_get_high_contrast())
                assertEquals(true, seen!!.highContrast, "applied when the app is back")
                assertEquals(true, seen!!.reduceMotion, "the app's own setting stays: the system's didn't change")
                assertEquals(false, RavenAnimation.enabled)

                // Then turns reduced motion on and off again: its value changed, so it applies.
                activity.active = false
                next()
                system.now = system.now.copy(reduceMotion = true)
                activity.active = true
                next()
                activity.active = false
                next()
                system.now = system.now.copy(reduceMotion = false)
                activity.active = true
                next()
                assertEquals(true, RavenAnimation.enabled)
                assertEquals(false, seen!!.reduceMotion)
            }
        }
    }

    @Test
    fun switchingBetweenTheAppsWindowsDoesntReadTheSystem() {
        val system = FakeSystem(plain)
        // The headless window system's own activity: focusing one window takes it from the other.
        withSystem(system, WindowCalls()) {
            withHost({
                Window(onCloseRequest = {}) {}
                Window(onCloseRequest = {}) {}
            }) {
                val reads = system.reads
                windows.first().controller.toFront()
                next()
                assertEquals(R_FALSE, Rwindow_is_active(windows.last().window), "the other window lost it")
                windows.last().controller.toFront()
                next()
                assertEquals(R_TRUE, Rwindow_is_active(windows.last().window), "and has it back")
                assertEquals(reads, system.reads, "the app stayed active")
            }
        }
    }

    @Test
    fun aScreenReaderIsCheckedAndShowsTheFocus() {
        val system = FakeSystem(plain)
        val activity = Activity()
        var seen: AccessibilitySettings? = null
        withSystem(system, activity) {
            withHost({ Window(onCloseRequest = {}) { seen = LocalAccessibilitySettings.current } }) {
                system.now = system.now.copy(screenReader = true)
                next()
                assertEquals(true, seen!!.isScreenReaderActive, "checked without leaving the app")

                activity.active = false
                next()
                activity.active = true
                next()
                assertEquals(R_TRUE, Raccessibility_get_focus_always_visible(), "as Raccessibility_follow_system does")
            }
        }
    }

    @Test
    fun withFollowSystemAccessibilityFalseNothingIsRead() {
        val system = FakeSystem(plain.copy(textScale = 2f))
        withSystem(system) {
            withRavenChecks {
                val host = RavenHost(headless = true, followSystemAccessibility = false)
                try {
                    host.start { Window(onCloseRequest = {}) {} }
                    host.runUntilIdle()
                    assertEquals(1f, Raccessibility_get_text_scale())
                    assertEquals(0, system.reads)
                } finally {
                    host.close()
                }
            }
        }
    }
}
