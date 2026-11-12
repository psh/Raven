---
status: open
---

# Modifier.alpha and clipToBounds

Two one-call modifiers the phase 2 ports found missing: `Modifier.alpha` for the C examples'
`Opacity` (`samples/components`' and `samples/notepad`'s dimmed labels, approximated with colours
for now) and `Modifier.clipToBounds` for their `Clip` (`samples/overlay`'s stripes, which run past
the panel's padding). Spec: [6. Layout and Modifiers](../spec.md#6-layout-and-modifiers) (the
modifier table). Decided on 2026-11-12, from the gaps the sample ports reported.

**Depends on:** nothing open.

## Deliverables

- [ ] `Modifier.alpha(alpha: Float)` in `raven.compose.ui`, folded like `material` (last one
      wins), through `Rwidget_set_opacity` on the outer widget only when it changes; 1 when left
      out of the chain. A value outside 0 to 1 fails.
- [ ] `Modifier.clipToBounds()`, folded the same way, through `Rwidget_set_clipping`; off when left
      out.
- [ ] The ports use them: `samples/components`' and `samples/notepad`'s dimmed labels take
      `Modifier.alpha` with the theme's text colour, and `samples/overlay`'s stripes take
      `clipToBounds()`; their Resolutions' approximation notes are corrected.
- [ ] Tests: each reaches Raven and returns to its default when removed; a padding change makes no
      call.
