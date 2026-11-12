---
status: open
---

# Transparent windows and WindowBackdrop

The window flags and backdrop the `overlay` port needs. Spec:
[4. Application host](../spec.md#4-application-host),
[8: glass and BackgroundBlur](../spec.md#phase-2-glass-and-backgroundblur).

**Depends on:** [Color, Shape and BorderStroke](0060-color-shape-and-borderstroke.md).

## Deliverables

- [ ] `Window(transparent, undecorated, background)` and the same parameters on
      `singleWindowApplication`: `R_WINDOW_TRANSPARENT`, `R_WINDOW_BORDERLESS`, and
      `Rwindow_set_clear_color` for a `background` that isn't `Unspecified`. The flags are fixed at
      creation; a changed one logs a warning.
- [ ] `WindowBackdrop(backdrop: Backdrop)` inside a window calls `Rwindow_set_backdrop` for it, and
      restores `None` when it leaves the composition. `Backdrop` is `None`, `Transparent`, `Blur`,
      `Acrylic` or `Mica`.
- [ ] A warning is logged when the window isn't transparent or the system refuses the backdrop.
- [ ] Tests (headless): the flags reach `Rwindow_create`, the backdrop follows the composition, and
      the warnings are logged. The look itself is checked by running `samples/overlay`.
