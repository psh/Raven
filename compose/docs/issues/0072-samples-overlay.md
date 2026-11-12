---
status: open
---

# samples/overlay

Port Raven's `examples/overlay`: a transparent window with a backdrop, and the blur styles side by
side. Spec: [10. Samples](../spec.md#10-samples),
[8: glass and BackgroundBlur](../spec.md#phase-2-glass-and-backgroundblur).

**Depends on:** [ComboBox, SpinBox and codeEditorLanguages](archive/0063-combobox-spinbox-and-codeeditorlanguages.md),
[Modifier.material and BackgroundBlur](0067-material-and-backgroundblur.md),
[Window: WindowState and its parameters](0086-window-windowstate-and-its-parameters.md).

## Deliverables

- [ ] `samples/overlay` as a transparent window with the C example's look: a `BackgroundBlur`
      per `BlurStyle` over coloured stripes, and a ComboBox choosing the `WindowBackdrop`.
- [ ] The opacity slider as `Window(opacity)`; the backdrop obtained, from `WindowBackdrop`'s
      return value; and the C example's two explanations, chosen by
      `LocalWindowInfo.current.isTransparent`.
- [ ] Runs from Gradle like `samples/hello`, documented in `compose/README.md`, with what each
      platform shows (a compositor on X11, Mica and Acrylic on Windows 11 only).
- [ ] A headless test: choosing a backdrop reaches the window.

## Gaps

None: [Window extras the samples use](archive/0076-window-extras-the-samples-use.md) brought the
opacity, the backdrop obtained and the transparency check into phase 2.
