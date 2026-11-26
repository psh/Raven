---
status: closed
---

# Clipboard

compose-raven has no way to read or write the clipboard; Raven's `Rclipboard_get_text` and
`Rclipboard_set_text` (and `Rclipboard_set_html`) are unbound. Bind them behind Compose's
`ClipboardManager` so an app can copy and paste. One of the everyday features in the [Raven API coverage](../../api-coverage.md) review (2026-11-14).

## Deliverables

- [x] `ClipboardManager` (`getText`, `setText`, `hasText`) and `LocalClipboardManager`, provided by `Window`.
- [x] Tests: text set and read back.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 563 tests, 1 of them new in ui's `WindowTest`.

- **`ClipboardManager` and `LocalClipboardManager`**, in `platform/ClipboardManager.kt`, are built
  like `UriHandler` ([LocalUriHandler](0094-localurihandler.md)): an interface with
  Compose's three members, a local that errors outside a `Window`, and the host's
  `RavenClipboardManager`, which `Window` provides beside `RavenUriHandler`. They take a `String`,
  compose-raven having no `AnnotatedString`. `getText()` reads Raven's "" for an empty clipboard
  as null, as Compose does, and `hasText()` is its default, non-empty text.
- **Compose's newer `Clipboard`**: Compose has deprecated `ClipboardManager` for the suspending
  `LocalClipboard` and `ClipEntry`, whose entry is platform-specific. The deprecated shape is
  bound because it maps onto Raven's synchronous calls directly; `Clipboard` can be added over it
  if a port needs it. Section 12 says so.
- **HTML**: `Rclipboard_set_html` stays unbound, as Compose's interface has nothing for it.
- **Test**: the headless window system keeps its own clipboard, empty at start, so the test
  writes through the manager and reads with `Rclipboard_get_text`, writes with
  `Rclipboard_set_text` (as a field's copy does) and reads through the manager, and empties it.
- **Spec**: the `LocalClipboardManager` bullet in section 4 and a row in section 12.
