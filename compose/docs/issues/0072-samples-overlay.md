---
status: open
---

# samples/overlay

Port Raven's `examples/overlay`: a transparent window with a backdrop, and the blur styles side by
side. Spec: [10. Samples](../spec.md#10-samples),
[8: glass and BackgroundBlur](../spec.md#phase-2-glass-and-backgroundblur).

**Depends on:** [ComboBox, SpinBox and codeEditorLanguages](0063-combobox-spinbox-and-codeeditorlanguages.md),
[Modifier.material and BackgroundBlur](0067-material-and-backgroundblur.md),
[Window transparency, opacity and backdrop](0068-window-transparency-opacity-and-backdrop.md).

## Deliverables

- [ ] `samples/overlay` as a transparent window with the C example's look: a `BackgroundBlur`
      per `BlurStyle` over coloured stripes, and a ComboBox choosing the `WindowBackdrop`.
- [ ] The opacity slider as `Window(opacity)`; the backdrop obtained, from `WindowBackdrop`'s
      return value; and the C example's two explanations, chosen by
      `LocalWindowInfo.current.isTransparent`.
- [ ] Runs from Gradle like `samples/hello`, documented in `compose/README.md`, with what each
      platform shows (a compositor on X11, Mica and Acrylic on Windows 11 only).
- [ ] A headless test: choosing a backdrop reaches the window.
- [ ] Each gap below is settled by [compose-raven phase 2 sample gaps](0073-compose-raven-phase-2-sample-gaps.md)
      before this ticket starts: brought into phase 2, or a port gap naming the phase that
      completes it.

## Gaps

None: [Window extras the samples use](archive/0076-window-extras-the-samples-use.md) brought the
opacity, the backdrop obtained and the transparency check into phase 2.
