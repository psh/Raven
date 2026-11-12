---
status: open
---

# MarkdownText

`MarkdownText` arrives early, in phase 2, for `samples/themes`' Markdown labels, without the
`style` parameter that waits for phase 3's `TextStyle`. Spec:
[Fonts and text styling](../spec.md#fonts-and-text-styling-phase-3). Decided in:
[Fonts and text styling](archive/0027-fonts-and-text-styling.md), and brought into phase 2 by
[ADR 0001](../adr/0001-phase-split-after-the-phase-2-sample-gaps.md).

**Depends on:** nothing open.

## Deliverables

- [ ] `MarkdownText(markdown, modifier, onLinkClick: ((String) -> Unit)? = null)` over a Label
      with `Rlabel_set_markdown`, links through `Rlabel_set_link_callback` inside the C-boundary
      catch. Phase 3 adds `style` with `TextStyle`.
- [ ] Tests: the Markdown reaches the label, a changed text is set again, and a link click reaches
      `onLinkClick`.
