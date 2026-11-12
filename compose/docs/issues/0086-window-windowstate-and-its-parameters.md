---
status: closed
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

**Depends on:** [Color, Shape and BorderStroke](archive/0060-color-shape-and-borderstroke.md) (`background`).

## Deliverables

- [x] `DpSize(width: Dp, height: Dp)` in `raven.compose.ui.unit`, with equality and
      `DpSize.Unspecified` as Compose has it.
- [x] `WindowState` and `rememberWindowState(size, position, placement)`: `size: DpSize`,
      `position`, `placement` (Floating or Maximized) and `isMinimized`. Writes call Raven's
      setters; Raven's resize and move callbacks write back. `Window(state = ...)` and
      `singleWindowApplication(state = ...)` replace phase 1's `width` and `height`, and
      `samples/hello` and `samples/xml-translator` move to them.
- [x] `Window(resizable = true, minimumSize: DpSize? = null)`: `minimumSize` through
      `Rwindow_set_min_size`, rounded to whole logical units; `null` keeps Raven's default, and a
      change calls Raven again.
- [x] `Window(customHeader, transparent, undecorated)`: `R_WINDOW_CUSTOM_HEADER`,
      `R_WINDOW_TRANSPARENT`, `R_WINDOW_BORDERLESS`. The flags are fixed at creation; a changed one
      logs a warning.
- [x] `Window(background)` through `Rwindow_set_clear_color` when it isn't `Unspecified`, and
      `Window(opacity = 1f)` through `Rwindow_set_opacity`, a refusal logging a warning and leaving
      the window opaque.
- [x] `WindowBackdrop(backdrop): Backdrop` inside a window: `Rwindow_set_backdrop`, `None` restored
      when it leaves the composition, and the backdrop in effect returned as snapshot state (`None`
      until the first apply). A warning when the window isn't transparent or the system refuses.
- [x] `LocalWindowInfo.current.isTransparent` from `Rwindow_get_native_handle`'s `transparent`.
- [x] Every parameter is on `singleWindowApplication` too.
- [x] Tests (headless): the state's size and position reach the window and follow Raven's resize
      and move; the minimum, the flags, background and opacity reach Raven; the backdrop follows the
      composition and its return value follows Raven's; the warnings are logged. The look itself is
      checked by running `samples/overlay`.

## Resolution

Done on macOS. 172 tests pass across the build, 18 of them new: 17 in compose-raven-ui
(`WindowTest`, `DpSizeTest`) and 1 in xml-translator.

- **`DpSize`**, in `raven.compose.ui.unit`, is a value class over one `Long` holding both sides'
  bits, as Compose packs it, so `DpSize.Unspecified` equals itself. It has `width`, `height`,
  `component1`/`2`, `copy`, `Zero`, `Unspecified` and `isSpecified`/`isUnspecified` (Compose's
  reading: only both sides unspecified is `Unspecified`).
- **`WindowState`** (`WindowState.kt`, in `raven.compose.ui`) follows Compose Desktop: an interface
  with `placement`, `isMinimized`, `position` and `size`, each snapshot state; `WindowState(...)`
  for `singleWindowApplication` and `rememberWindowState(placement, isMinimized, position, size)`
  for a `Window`, both defaulting to `Floating`, `PlatformDefault` and 800 by 600. `WindowPlacement`
  is `Floating` or `Maximized`; `WindowPosition` is `PlatformDefault` or `Absolute(x, y)`, with
  `WindowPosition(x, y)`. Compose Desktop's `Aligned` and `Fullscreen` are left out: Raven can only
  centre a window, and the spec names only the two placements.
- **Both ways**: the `Window` reads its state in composition, so a write recomposes it, and a
  `SideEffect` gives Raven only what differs from what Raven last had (`Rwindow_set_size`,
  `_set_position`, `_maximize`, `_minimize`, `_restore`). Raven's resize callback writes the size
  back. Raven has **no move or placement callback**, so the host's frame callback has each window
  read `Rwindow_get_position`, `_is_minimized` and `_is_maximized` before it drains, and write what
  changed since the last frame. An app's write not yet applied is never overwritten, since only a
  change on Raven's side counts. A minimised window keeps the placement it comes back to, and a
  resize to nothing (Win32's minimise) is ignored.
- **Opening**: the window is created hidden (with `R_WINDOW_MAXIMIZED` when it opens maximised),
  given its position, minimum size, background and opacity, then shown. Its state then takes the
  real position in place of `PlatformDefault`, and the real size for an unspecified side, as in
  Compose Desktop.
- **`size`** is the content's size, frame left out, as Raven sizes windows (Compose Desktop's
  includes the frame). Sizes and positions are rounded to whole logical units.
- **`Window(onCloseRequest, state, title, customHeader, transparent, undecorated, resizable,
  minimumSize, background, opacity, content)`**: the flags, `resizable = false` as
  `R_WINDOW_NO_RESIZE` among them (Raven takes it only at creation too), are fixed when the window
  opens, and a change logs `Window: transparent can't change once the window is open, so the change
  is ignored`. `minimumSize` calls `Rwindow_set_min_size` on each change, and `null` after a value
  calls it with 0 by 0, Raven's default. `background` calls `Rwindow_set_clear_color`; Raven can't
  clear it, so going back to `Unspecified` keeps the last colour, with a warning. `opacity` calls
  `Rwindow_set_opacity` on each change; a refusal warns once a window. `title` now follows the
  composition too (`Rwindow_set_title`). `onExternalDrop` stays with its own ticket (0082).
- **`singleWindowApplication(state = WindowState(), title, ..., opacity, followSystemAccessibility,
  content)`** takes every parameter and passes it to its `Window`.
- **`WindowBackdrop(backdrop): Backdrop`** (`WindowBackdrop.kt`): a remembered observer applies a
  changed backdrop in a `SideEffect`, keeps `Rwindow_get_backdrop` as the snapshot state it returns
  (`None` until the first apply), and restores `None` when forgotten, which is before the window is
  destroyed. On a window not created transparent it warns and never calls Raven, which would report
  an error; a refusal warns and returns what Raven kept.
- **`LocalWindowInfo.current.isTransparent`**, in `raven.compose.ui.platform` as Compose's, reads
  `RNativeWindow.transparent` once when the window opens (headless, `Rwindow_get_native_handle`
  returns false but still fills it in). Each window's content composition provides it, with an
  internal `LocalRavenWindow` for `WindowBackdrop`.
- **Warnings** go to the layout warnings' sink, given each time rather than once a process.
- **Headless seam**: the null window system keeps no flags, minimum size or clear colour, and never
  refuses opacity or a backdrop, so these calls go through an internal `WindowCalls` that the tests
  replace, as they replace `layoutWarningSink`.
- **Samples**: `samples/hello` uses `rememberWindowState(size = DpSize(420.dp, 220.dp))` and
  `samples/xml-translator` `WindowState(size = DpSize(1200.dp, 760.dp))`. The translator now emits
  `singleWindowApplication(state = WindowState(size = DpSize(800.dp, 500.dp)), title = ...)` for a
  `<Window Width Height>`, with `Dp.Unspecified` for a side left out, so the examples check
  compiles against the new signature.
- **Tests** (`WindowTest`, `DpSizeTest`, `TranslatorTest`):
  - a `DpSize` has its sides and compares by them, and `Unspecified` equals itself;
  - the state's size and position reach the window, rounded, and follow later writes;
  - the state follows Raven's resize and move, and a reader recomposes;
  - an unspecified side keeps the window's own size;
  - the placement and `isMinimized` reach the window and follow Raven's changes;
  - the flags reach `Rwindow_create`, and a changed flag is ignored with a warning;
  - the minimum size reaches Raven rounded, follows changes, and `null` restores the default;
  - the background reaches Raven when specified, with a warning when it goes back to `Unspecified`;
  - the opacity reaches the window, and a refused one leaves it opaque with one warning;
  - the backdrop follows the composition and returns Raven's, and `None` is restored when it leaves;
  - a refused backdrop, and one on an opaque window, return what Raven has, with a warning;
  - `LocalWindowInfo.current.isTransparent` says whether the window is see-through;
  - the title follows the composition;
  - a translated window size becomes its state.

  The look itself is checked by running `samples/overlay` (0072).
