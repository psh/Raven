---
status: open
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

- [ ] `LocalAccessibilitySettings` (reduce motion, high contrast, text scale, screen reader), provided by `Window`, recomposing when a setting changes.
- [ ] Settings changed while the app runs are followed: the host reads `Raccessibility_get_system_settings` again when a window regains focus (and every few seconds, if focus alone misses changes), and when a system value differs from the last one read, applies that setting. Only a setting whose system value changed is applied, so one the app set by hand (the reduce-motion switch in [samples' animations](archive/0137-samples-animations.md)) stays until the system's value changes. Off with `followSystemAccessibility = false`, as the start-up call is.
- [ ] Spec updated (the host's start-up paragraph).
- [ ] Tests with the settings set through Raven's setters, and a changed system value applied while one left alone isn't.
