---
status: closed
---

# The phase 2 samples completed

What phase 3 completes in the phase 2 samples, per their tickets' gaps. Spec: [10. Samples](../../spec.md#10-samples).

**Depends on:** [Fonts and text styling](0116-fonts-and-text-styling.md), [Lists and trees](0120-lists-and-trees.md), [Dialog and the default dialogs](0130-dialog-and-the-default-dialogs.md), [ColorPicker](0132-colorpicker.md).

## Deliverables

- [x] `samples/themes`: its theme list as a `ListView`, `ColorPicker(popup = true, alpha = false)` replacing the swatches, the monospace label in `FontFamily.Monospace`, the status line wrapping ([samples/themes](0071-samples-themes.md)).
- [x] `samples/overlay`: the explanation wrapping with `Text(softWrap)` ([samples/overlay](0072-samples-overlay.md)).
- [x] `samples/notepad`: the default dialogs (no `native = true`), and headless tests of its open, save and discard flows ([samples/notepad](0069-samples-notepad.md)).
- [x] Each sample ticket's gap list updated.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 511 tests, 3 of them new in `NotepadTest`. Themes was
launched and shows its accent picker as the C example does.

- **`samples/themes`**: the accent is a `ColorPicker(popup = true, alpha = false)` showing the C
  example's colour until one is picked (the look's accent stays until then), beside its label,
  `RavenTheme(accent)`, in `FontFamily.Monospace` at 12 sp, where the C example's names
  `Rtheme_set_accent`; the status line wraps (`Text(softWrap = true)`). `ThemesTest` picks the
  accent with `performColorChange`. **The ticket's "theme list as a ListView"**: the C example's
  theme list is a ComboBox, as the port's is; the ListView the gap list meant is its
  First/Second/Third list, which [Lists and trees](0120-lists-and-trees.md) already added.
- **`samples/overlay`**: the explanation wraps, across the panel.
- **`samples/notepad`**: the discard prompt and the file dialogs are the default ones (no
  `native = true`), over the window; the header comment says the C example's are the system's.
  The editor takes the focus back after a language, an Edit command or a file opens, with a
  `FocusRequester`, the other gap its ticket listed. Headless tests: discarding asks first, Cancel
  keeping the text and OK clearing it; a file opened through the dialog's `FileBrowser`, with the
  language of its name; Save as writing the proposed name where the dialog is, and a cancelled
  Save as writing nothing.
- **Gap lists**: [samples/themes](0071-samples-themes.md), [samples/overlay](0072-samples-overlay.md)
  and [samples/notepad](0069-samples-notepad.md) say what phase 3 filled; overlay's `WindowDrag`
  still has no counterpart.
