---
status: open
---

# Window: WindowState and its parameters

The single `Window` completed in phase 2, so its signature is final before the phase 2 samples are
written against it: `WindowState` replaces phase 1's `width` and `height`, with every flag and
parameter the phase 2 samples use. Phase 3 keeps windows that come and go, `Dialog`, the lifecycle
owners, `icon`, `island`, `onKeyEvent` and `backKeys`. Spec:
[4. Application host](../spec.md#4-application-host),
[6. Layout and Modifiers](../spec.md#6-layout-and-modifiers),
[8: glass and BackgroundBlur](../spec.md#phase-2-glass-and-backgroundblur). Decided in:
[Window extras the samples use](archive/0076-window-extras-the-samples-use.md),
[Window minimum size](archive/0083-window-minimum-size.md) and
[ADR 0001](../adr/0001-phase-split-after-the-phase-2-sample-gaps.md), which merged
[Window transparency, opacity and backdrop](archive/0068-window-transparency-opacity-and-backdrop.md)
and [Window minimum size and DpSize](archive/0085-window-minimum-size-and-dpsize.md) into this
ticket, with the `customHeader` flag from [HeaderBar](0062-headerbar.md).

**Depends on:** [Color, Shape and BorderStroke](0060-color-shape-and-borderstroke.md) (`background`).

## Deliverables

- [ ] `DpSize(width: Dp, height: Dp)` in `raven.compose.ui.unit`, with equality and
      `DpSize.Unspecified` as Compose has it.
- [ ] `WindowState` and `rememberWindowState(size, position, placement)`: `size: DpSize`,
      `position`, `placement` (Floating or Maximized) and `isMinimized`. Writes call Raven's
      setters; Raven's resize and move callbacks write back. `Window(state = ...)` and
      `singleWindowApplication(state = ...)` replace phase 1's `width` and `height`, and
      `samples/hello` and `samples/xml-translator` move to them.
- [ ] `Window(resizable = true, minimumSize: DpSize? = null)`: `minimumSize` through
      `Rwindow_set_min_size`, rounded to whole logical units; `null` keeps Raven's default, and a
      change calls Raven again.
- [ ] `Window(customHeader, transparent, undecorated)`: `R_WINDOW_CUSTOM_HEADER`,
      `R_WINDOW_TRANSPARENT`, `R_WINDOW_BORDERLESS`. The flags are fixed at creation; a changed one
      logs a warning.
- [ ] `Window(background)` through `Rwindow_set_clear_color` when it isn't `Unspecified`, and
      `Window(opacity = 1f)` through `Rwindow_set_opacity`, a refusal logging a warning and leaving
      the window opaque.
- [ ] `WindowBackdrop(backdrop): Backdrop` inside a window: `Rwindow_set_backdrop`, `None` restored
      when it leaves the composition, and the backdrop in effect returned as snapshot state (`None`
      until the first apply). A warning when the window isn't transparent or the system refuses.
- [ ] `LocalWindowInfo.current.isTransparent` from `Rwindow_get_native_handle`'s `transparent`.
- [ ] Every parameter is on `singleWindowApplication` too.
- [ ] Tests (headless): the state's size and position reach the window and follow Raven's resize
      and move; the minimum, the flags, background and opacity reach Raven; the backdrop follows the
      composition and its return value follows Raven's; the warnings are logged. The look itself is
      checked by running `samples/overlay`.
