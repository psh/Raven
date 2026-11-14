---
status: closed
---

# samples/overlay

Port Raven's `examples/overlay`: a transparent window with a backdrop, and the blur styles side by
side. Spec: [10. Samples](../../spec.md#10-samples),
[8: glass and BackgroundBlur](../../spec.md#phase-2-glass-and-backgroundblur).

**Depends on:** [ComboBox, SpinBox and codeEditorLanguages](0063-combobox-spinbox-and-codeeditorlanguages.md),
[Modifier.material and BackgroundBlur](0067-material-and-backgroundblur.md),
[Window: WindowState and its parameters](0086-window-windowstate-and-its-parameters.md).

## Deliverables

- [x] `samples/overlay` as a transparent window with the C example's look: a `BackgroundBlur`
      per `BlurStyle` over coloured stripes, and a ComboBox choosing the `WindowBackdrop`.
- [x] The opacity slider as `Window(opacity)`; the backdrop obtained, from `WindowBackdrop`'s
      return value; and the C example's two explanations, chosen by
      `LocalWindowInfo.current.isTransparent`.
- [x] Runs from Gradle like `samples/hello`, documented in `compose/README.md`, with what each
      platform shows (a compositor on X11, Mica and Acrylic on Windows 11 only).
- [x] A headless test: choosing a backdrop reaches the window.

## Gaps

None: [Window extras the samples use](0076-window-extras-the-samples-use.md) brought the
opacity, the backdrop obtained and the transparency check into phase 2.

Found while porting (see the Resolution): the stripes' `Clip="true"` and the explanation's
`Wrap="true"` have no phase 2 counterpart, and `WindowDrag` has none at all.

**Filled in phase 3** ([The phase 2 samples completed](0133-phase-2-samples-completed.md)): the
explanation wraps with `Text(softWrap = true)`, and the stripes clip with `Modifier.clipToBounds()`
(by [Images and resources](0115-images-and-resources.md)). `WindowDrag` still has no counterpart.

## Resolution

Done and run on macOS. `./gradlew build allTests` passes: 283 tests, 4 of them the sample's.

- **The sample** (`raven.sample.overlay`, `overlay.kexe`): `main` is `application { }` holding the
  opacity as state, with `RavenTheme(dark = true)` (the C example's `Rtheme_apply(R_THEME_DARK)`)
  around a 640 by 420 `Window(transparent = true, undecorated = true, opacity = opacity)`.
  `undecorated` is the XML's `Borderless`.
- **`Overlay(opacity, onOpacityChange, onClose)`** is the window's content:
  - the panel is a `BackgroundBlur(style = Native, RoundedCornerShape(24.dp), tint = #10141C99)`
    with the content padded 22 dp, 12 dp apart;
  - the title row: a 24 sp "Seen through" and Close as a `TextButton` (`Style="Flat"`);
  - the explanation, chosen by `LocalWindowInfo.current.isTransparent`;
  - the backdrop row: a `ComboBox` of Transparent, Blur, Acrylic and Mica (Blur at first), whose
    choice is passed to `WindowBackdrop`, and "obtained: ..." from its return value, worded as
    the C example's `backdrop_name`;
  - the opacity row: a `Slider` over `0.2f..1f` that writes the app's state, which `main` passes to
    `Window(opacity)`. A refusal is the library's warning, the slider keeping the app's value
    (decision 0076), where the C example moved the slider back;
  - the blurs: a `Box` taking the rest of the height, with a Row of 40 16 dp `Surface` stripes in
    the C example's four colours under a Row of four weighted `BackgroundBlur`s (Plain, Frosted,
    Glass, Native with tint #00000030), 14 dp in, 12 dp apart, 14 dp corners, each with its
    centred name.
- **Deviations from the C example**, none a library change:
  - **`WindowDrag`**: the C panel has `WindowDrag="true"` and its text says "Drag the panel to move
    the window." compose-raven has no counterpart, and in Raven it acts only in a
    `R_WINDOW_CUSTOM_HEADER` window (`rvn_window_region` answers "client" otherwise), which the C
    example doesn't create, so the C window can't be dragged either. The port leaves the sentence
    out rather than promise it. A `Modifier` for it would be a new phase item (the coordinator's
    call); with `customHeader` it is what `HeaderBar` does already.
  - **`Clip="true"`** on the stripes: no clipping modifier exists in phase 2 (`Modifier.clip` is
    phase 3, spec section 8, for `Image`). The 40 stripes (640 dp) are wider than the panel, so
    they run past its right padding to the window's edge, under the Native panel's rounding. Seen
    on the capture; the four blurs are unaffected.
    [0091](0091-modifier-alpha-and-cliptobounds.md) gave the stripes' Row `clipToBounds()`, so
    they stop at the panel's padding, as the C example's do.
  - **`Wrap="true"`** on the explanation: `Text` has no `softWrap` until phase 3's text styling.
    The see-through sentence fits on one line; the opaque one (a window system with no
    transparency) is wider than the panel and is cut off.
- **Checked by eye** on macOS: the window shows through (the desktop behind it, unblurred), the
  four blur styles over the stripes look as the C example's, and "obtained: the desktop, as it
  is" shows that macOS's Raven gives Transparent when Blur is asked. X11 and Windows aren't run.
- **README**: how to run it, and what each platform shows.
- **Tests** (`OverlayTest`), the test opening its own `Window(transparent = true)` through
  `RavenHost.start` (opting in to the internal API), since the harness's `setContent` window is
  opaque:
  - choosing Mica in the ComboBox reaches the window (`Rwindow_get_backdrop`), starting from Blur,
    and the label says what was obtained;
  - a see-through window explains what shows through;
  - an opaque window explains the plain background and obtains nothing;
  - Close calls `onClose`.
