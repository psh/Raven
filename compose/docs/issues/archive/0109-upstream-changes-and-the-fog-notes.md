---
status: closed
wayfinder: task
parent: 0095
blocked_by: [0106]
assignee: Paul Hawke
---

# Upstream changes and the fog notes

## Question

The upstream changes pulled in on 2026-11-12 leave two fog notes on
[compose-raven phase 3 spec](../0023-compose-raven-phase-3-spec.md#not-yet-specified) out of date.
**Popups** says Raven has only menu and tooltip popups (now `Rpopup_open`; update it as
[Menus opened from a button, and Raven's popups](0106-menus-opened-from-a-button-and-ravens-popups.md)
decides). **App bundling** expects a fork change so Raven finds MoltenVK in
`Contents/Frameworks`; upstream's loader now finds MoltenVK in the app bundle and honours
`RAVEN_VULKAN_LIBRARY`, so check whether that need is gone and rewrite the note. Also note
whether `Rwindow_focus` (and borderless windows that can become key) gives phase 3's
`application { Window { } }` anything it lacks, such as bringing a window to the front, and add it
to the spec or the fog. Update the roadmap's Later section to match.

## Resolution

Done 2026-11-12. The fog notes on [compose-raven phase 3 spec](../0023-compose-raven-phase-3-spec.md#not-yet-specified)
and the roadmap's Later section now match what upstream brought in on 2026-11-12:

- **Popups**: rewritten as decided in
  [Menus opened from a button, and Raven's popups](0106-menus-opened-from-a-button-and-ravens-popups.md):
  Raven has `Rpopup_open` (light-dismiss, a `closed` callback, the content kept by the app) and
  `Rpopup_close`/`Rpopup_is_open`, which map to `Popup(alignment, offset, onDismissRequest)`. It
  waits only for a sample that needs a popup of composables.
- **App bundling**: the expected fork change isn't needed. Raven's Vulkan loader
  (`src/render/vulkan/vk_loader.c`) tries `RAVEN_VULKAN_LIBRARY` first, then, on macOS,
  `libvulkan.1.dylib` and `libMoltenVK.dylib` in `<app>.app/Contents/Frameworks`, then the system
  places. What's left of the patch is the packaging itself.
- **`Rwindow_focus`**: phase 3 has no way to bring a window to the front with the keyboard, and
  Compose Desktop does it through the AWT window (`window.toFront()`), not a composable parameter.
  No sample needs it, so it went to the fog as its own patch ("A window brought to the front") rather
  than into the spec.
- **Borderless windows that can become key** (Cocoa): `undecorated = true` windows take the
  keyboard on macOS now; spec section 4 notes it.
