---
status: open
---

# Clipboard

compose-raven has no way to read or write the clipboard; Raven's `Rclipboard_get_text` and
`Rclipboard_set_text` (and `Rclipboard_set_html`) are unbound. Bind them behind Compose's
`ClipboardManager` so an app can copy and paste. One of the everyday features in the [Raven API coverage review](https://claude.ai/artifact/J56iXnntbqm8dmAUXWAEeh) (2026-11-14).

## Deliverables

- [ ] `ClipboardManager` (`getText`, `setText`, `hasText`) and `LocalClipboardManager`, provided by `Window`.
- [ ] Tests: text set and read back.
