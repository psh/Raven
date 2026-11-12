---
status: wont-fix
---

# Window transparency, opacity and backdrop

The window flags, opacity, backdrop and transparency check the `overlay` port needs. Spec:
[4. Application host](../../spec.md#4-application-host),
[8: glass and BackgroundBlur](../../spec.md#phase-2-glass-and-backgroundblur).

Opacity, the return value and `isTransparent` were decided in
[Window extras the samples use](0076-window-extras-the-samples-use.md).

**Depends on:** [Color, Shape and BorderStroke](0060-color-shape-and-borderstroke.md).

## Deliverables

- [ ] `Window(transparent, undecorated, background)` and the same parameters on
      `singleWindowApplication`: `R_WINDOW_TRANSPARENT`, `R_WINDOW_BORDERLESS`, and
      `Rwindow_set_clear_color` for a `background` that isn't `Unspecified`. The flags are fixed at
      creation; a changed one logs a warning.
- [ ] `WindowBackdrop(backdrop: Backdrop)` inside a window calls `Rwindow_set_backdrop` for it, and
      restores `None` when it leaves the composition. `Backdrop` is `None`, `Transparent`, `Blur`,
      `Acrylic` or `Mica`.
- [ ] `WindowBackdrop` returns the backdrop in effect, read with `Rwindow_get_backdrop` after each
      apply, as snapshot state: `None` until the first apply.
- [ ] A warning is logged when the window isn't transparent or the system refuses the backdrop.
- [ ] `Window(opacity = 1f)`, on `singleWindowApplication` too, through `Rwindow_set_opacity`; a
      refusal logs a warning and leaves the window opaque.
- [ ] `LocalWindowInfo.current.isTransparent` from `Rwindow_get_native_handle`'s `transparent`.
- [ ] Tests (headless): the flags reach `Rwindow_create`, the backdrop follows the composition and its
      return value follows Raven's, opacity reaches the window, and the warnings are logged. The look itself is checked by running `samples/overlay`.

## Resolution

Not done as its own ticket: merged into [Window: WindowState and its parameters](../0086-window-windowstate-and-its-parameters.md)
by [ADR 0001](../../adr/0001-phase-split-after-the-phase-2-sample-gaps.md), so the single `Window` is finished in one place.
