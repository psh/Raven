---
status: closed
---

# Modifier.alpha and clipToBounds

Two one-call modifiers the phase 2 ports found missing: `Modifier.alpha` for the C examples'
`Opacity` (`samples/components`' and `samples/notepad`'s dimmed labels, approximated with colours
for now) and `Modifier.clipToBounds` for their `Clip` (`samples/overlay`'s stripes, which run past
the panel's padding). Spec: [6. Layout and Modifiers](../../spec.md#6-layout-and-modifiers) (the
modifier table). Decided on 2026-11-12, from the gaps the sample ports reported.

**Depends on:** nothing open.

## Deliverables

- [x] `Modifier.alpha(alpha: Float)` in `raven.compose.ui`, folded like `material` (last one
      wins), through `Rwidget_set_opacity` on the outer widget only when it changes; 1 when left
      out of the chain. A value outside 0 to 1 fails.
- [x] `Modifier.clipToBounds()`, folded the same way, through `Rwidget_set_clipping`; off when left
      out.
- [x] The ports use them: `samples/components`' and `samples/notepad`'s dimmed labels take
      `Modifier.alpha` with the theme's text colour, and `samples/overlay`'s stripes take
      `clipToBounds()`; their Resolutions' approximation notes are corrected.
- [x] Tests: each reaches Raven and returns to its default when removed; a padding change makes no
      call.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 298 tests, 6 of them `DrawTest`'s.

- **API**, in `raven.compose.ui` (`Draw.kt`, in `compose-raven-ui`), next to `material` and
  `appearTransition`: `Modifier.alpha(alpha: Float)` and `Modifier.clipToBounds()`. An alpha
  outside 0 to 1 (or NaN) is refused. The last `alpha` in the chain wins; `clipToBounds` is on if
  it is anywhere in the chain.
- **When they are set**: `FoldedModifier` gains `alpha` (1 when left out) and `clipToBounds` (false
  when left out), and `RavenNode.setModifier` calls `Rwidget_set_opacity` and
  `Rwidget_set_clipping` on the outer widget only when the folded value changed. A widget that
  never has them makes no call, so it keeps Raven's defaults.
- **Raven's own clipping**: some Raven classes clip their children from the start (scroll boxes,
  dock panels, islands). A `clipToBounds()` added to one of them and then removed turns its
  clipping off, as the spec's "off when left out" says; none of the composables do this today.
- **Tests** (`DrawTest`, in `compose-raven-ui`): Raven has no getter for either, so the calls go
  through `DrawCalls`, which the tests replace with a recorder, as `AppearCalls` is for
  `appearTransition`.
  - each reaches Raven, and removing it gives Raven 1 or no clipping;
  - a padding change makes no call, and a widget without them makes none;
  - the last alpha in the chain wins, and one outside 0 to 1 fails.
- **Samples**:
  - `samples/components`: the `Color.Gray` muted labels are `Modifier.alpha(0.6f)` (the stat
    names, "Regions:" and the roles) and `alpha(0.7f)` (the status line), in the theme's text colour,
    as the C example's `Opacity`.
  - `samples/notepad`: the status line's labels are `alpha(0.8f)` and `alpha(0.6f)`, no longer the
    theme's text colour with that alpha.
  - `samples/overlay`: the stripes' `Row` takes `clipToBounds()`.
  - The approximation notes in the Resolutions of 0069, 0070 and 0072 say this ticket replaced them.
- **Checked by eye** on macOS: `samples/overlay` running, the stripes stop at the panel's right
  padding instead of running to the window's edge.
