---
status: open
---

# System accessibility settings

An app can't read the system's reduce motion, high contrast or text scale, or whether a screen
reader is on. Bind `Raccessibility_get_reduce_motion`, `_get_high_contrast`, `_get_text_scale`
and `_is_screen_reader_active` as composition locals that follow the system. One of the everyday
features in the [Raven API coverage review](https://claude.ai/artifact/J56iXnntbqm8dmAUXWAEeh) (2026-11-14).

## Deliverables

- [ ] Composition locals (or one `AccessibilitySettings` local) provided by `Window`, recomposing when the system changes.
- [ ] `RavenAnimation.enabled` follows reduce motion by default ([samples' animations](archive/0137-samples-animations.md) set it by hand).
- [ ] Tests with the settings set through Raven's setters.
