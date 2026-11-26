---
status: closed
---

# System accessibility settings

The host already calls `Raccessibility_follow_system()` at start, so Raven follows the system's text
scale, contrast and reduced motion ([Accessibility semantics](archive/0026-accessibility-semantics.md)),
and reduced motion is `RavenAnimation.enabled` (Raven's `Raccessibility_set_reduce_motion` is
`Rset_animations_enabled`). What's missing is a Kotlin view of the settings: an app can't read
`Raccessibility_get_high_contrast`, `_get_text_scale` or `_is_screen_reader_active`, which 0026
deferred as `LocalAccessibilitySettings` until something needed it. One of the everyday
features in the [Raven API coverage](../api-coverage.md) review (2026-11-14).

## Deliverables

- [x] `LocalAccessibilitySettings` (reduce motion, high contrast, text scale, screen reader), provided by `Window`, recomposing when a setting changes.
- [x] Settings changed while the app runs are followed: the host reads `Raccessibility_get_system_settings` again when a window regains focus (and every few seconds, if focus alone misses changes), and when a system value differs from the last one read, applies that setting. Only a setting whose system value changed is applied, so one the app set by hand (the reduce-motion switch in [samples' animations](archive/0137-samples-animations.md)) stays until the system's value changes. Off with `followSystemAccessibility = false`, as the start-up call is.
- [x] Spec updated (the host's start-up paragraph).
- [x] Tests with the settings set through Raven's setters, and a changed system value applied while one left alone isn't.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 601 tests, 7 of them in ui's new
`AccessibilitySettingsTest`.

- **`LocalAccessibilitySettings`**, in `raven.compose.ui.platform`: an
  `AccessibilitySettings(reduceMotion, highContrast, textScale, isScreenReaderActive)` that `Window`
  provides, read from Raven once a frame, so it is what the app runs with (the system's, or what
  the app set). A dynamic local, so only its readers recompose. `AccessibilitySettings.Default`
  outside a window.
- **Following the system while the app runs**, in a host-owned `AccessibilityFollower`: at start
  it calls `Raccessibility_follow_system()` as before and remembers the system's settings; when the
  app becomes active again it reads them (`Raccessibility_get_system_settings`) and applies only
  those whose system value changed, so `samples/animations`' reduce-motion switch stays. A screen
  reader starting shows the focus always, as `follow_system` does.
- **When it reads**: the ticket proposed a window regaining focus, plus a timer. Reading the system
  spawns `gsettings` on Linux, so neither is done often: the trigger is the *app* activating (no
  window active at the last frame, one now), not a switch between its own windows, and there is no
  timer, since changing a system setting means leaving the app. Only the screen reader, which
  VoiceOver's Cmd+F5 toggles while the app keeps the focus, is checked on a 2-second timer, as it is
  cheap. The activation check runs before `RavenAnimation` reads Raven, or a reduced motion applied
  on activation reached `RavenAnimation.enabled` a frame late (a test caught it).
- **Seams**: `SystemAccessibilityCalls` plays the system in tests (and lets a headless host follow
  it); `WindowCalls.isActive` lets a test leave the app and come back, as the null window system has
  no way to make every window inactive.
- **Tests**: Raven's settings reach the local and its readers; the system's apply at start; a
  changed system setting applies when the app comes back while one the app set stays; switching
  between two windows reads nothing (with the null window system's own focus); the screen reader is
  checked and shows the focus; `followSystemAccessibility = false` reads nothing.
- **Spec**: section 4's start-up paragraph and a row in section 12.
