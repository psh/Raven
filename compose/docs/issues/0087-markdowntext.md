---
status: closed
---

# MarkdownText

`MarkdownText` arrives early, in phase 2, for `samples/themes`' Markdown labels, without the
`style` parameter that waits for phase 3's `TextStyle`. Spec:
[Fonts and text styling](../spec.md#fonts-and-text-styling-phase-3). Decided in:
[Fonts and text styling](archive/0027-fonts-and-text-styling.md), and brought into phase 2 by
[ADR 0001](../adr/0001-phase-split-after-the-phase-2-sample-gaps.md).

**Depends on:** nothing open.

## Deliverables

- [x] `MarkdownText(markdown, modifier, onLinkClick: ((String) -> Unit)? = null)` over a Label
      with `Rlabel_set_markdown`, links through `Rlabel_set_link_callback` inside the C-boundary
      catch. Phase 3 adds `style` with `TextStyle`.
- [x] Tests: the Markdown reaches the label, a changed text is set again, and a link click reaches
      `onLinkClick`.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 266 tests, 5 of them new in foundation's
`MarkdownTextTest`.

- **`MarkdownText(markdown, modifier, onLinkClick = null)`**, in `Text.kt`: a Label of its own
  descriptor whose `LabelMarkdown` property calls `Rlabel_set_markdown(R_TRUE)`, a no-op after the
  first time, then `Rlabel_set_text`. Raven wraps a Markdown label on its width unless told
  otherwise, so nothing sets the wrap. No `style` until phase 3's `TextStyle`.
- **Links**: `Rlabel_set_link_callback` replaces what Raven does with a link (fire an `action:`
  link's action, open an http, https or mailto link), and `samples/themes` relies on that for its
  `action:` link. So a null `onLinkClick` keeps Raven's own handling: the binding layer gains a
  self-contained section with a `LabelLinkClicked` event, kept out of the descriptor's events, and
  `bindLinkClick(onLinkClick)`, which registers the shared callback while the node holds a lambda
  and clears it when the lambda becomes null. The callback dispatches through `dispatchEvent`, so
  what the lambda throws goes to the host's error path, not into C.
- **Harness**: unchanged. The tests click a link with `Rwindow_inject_mouse_move` and
  `Rwindow_inject_mouse_button` a few points in from the label's left edge, where its one link
  starts.
- **Tests**:
  - the Markdown reaches the label, which is in Markdown mode and shows the text without its marks;
  - a changed text is set again;
  - a link click reaches `onLinkClick` with the link's address, and a replaced lambda gets the next
    one;
  - with `onLinkClick` set to null, Raven follows an `action:` link itself and fires the action;
  - what `onLinkClick` throws ends the app through the host rather than crossing into C.
