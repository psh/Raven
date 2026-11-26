---
status: closed
---

# Selectable text

A `Text` can't be selected and copied. Bind `Rlabel_set_selectable` behind Compose's
`SelectionContainer`, so text inside one is selectable. One of the everyday features in the [Raven API coverage review](https://claude.ai/artifact/J56iXnntbqm8dmAUXWAEeh) (2026-11-14).

## Deliverables

- [x] `SelectionContainer { }` and `DisableSelection { }`, a composition local the `Text` composables read.
- [x] Tests: a `Text` inside is selectable, one outside or under `DisableSelection` isn't.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 577 tests, 5 of them in foundation's new
`SelectionTest`.

- **`SelectionContainer(modifier, content)` and `DisableSelection(content)`**, in
  `raven.compose.foundation.text.selection` as Compose's are, provide an internal
  `LocalTextSelectable`; `Text` and `MarkdownText` bind it to `Rlabel_set_selectable`. The container
  is a `Box`, since Compose's lays its content out as one; `DisableSelection` lays nothing out. A
  text that leaves a container is no longer selectable, and Raven drops its selection and the focus
  it took.
- **Raven's selection is per label**, not one selection across the container as Compose's is: a
  drag doesn't run from one `Text` into the next, and Ctrl+A selects the focused text. Section 12
  says so.
- **A selectable text takes the mouse**, so a `clickable` around it no longer gets the click; a
  test shows both sides. Copying Markdown gives the text without its marks, and links stay links.
- **Tests**: selectable inside, not outside or under `DisableSelection`, for `Text` and
  `MarkdownText`; Ctrl+C copies plain and Markdown text; leaving the container ends the selection;
  the click a `clickable` loses.
- **Spec**: a bullet in section 8's Fonts and text styling and a row in section 12.
