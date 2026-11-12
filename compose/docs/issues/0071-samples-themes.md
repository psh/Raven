---
status: open
---

# samples/themes

Port Raven's `examples/themes`: a gallery of controls under a choice of look, theme and dark mode,
with theme files loaded at start. Spec: [10. Samples](../spec.md#10-samples),
[8: theming](../spec.md#phase-2-theming).

**Depends on:** [ComboBox, SpinBox and codeEditorLanguages](0063-combobox-spinbox-and-codeeditorlanguages.md),
[RavenTheme and theme files](0065-raventheme-and-theme-files.md),
[Component colours, shapes and padding](0066-component-colors-shapes-and-padding.md).

## Deliverables

- [ ] `samples/themes` with the C example's look: the controls of `themes.xml` in a splitter, the
      look and theme ComboBoxes, and a subtree using `paper.xml`.
- [ ] `ocean.xml`, `paper.xml` and `tweaks.xml` loaded with `loadTheme` and chosen through an
      application-level `RavenTheme`.
- [ ] The `danger` class becomes `colors =` on the buttons it styles.
- [ ] Runs from Gradle like `samples/hello`, documented in `compose/README.md`.
- [ ] A headless test: choosing a look or theme changes Raven's, and `RavenTheme.colors` follows.
- [ ] Each gap below is settled by [compose-raven phase 2 sample gaps](0073-compose-raven-phase-2-sample-gaps.md)
      before this ticket starts: brought into phase 2, or a port gap naming the phase that
      completes it.

## Gaps

- **Phase 3:** `ListView` ([Lists and trees](archive/0033-lists-and-trees.md)) and the Markdown
  label and editor (`MarkdownText`, [Fonts and text styling](archive/0027-fonts-and-text-styling.md)).
- **Phase 4 fog:** `ColorPicker`, which picks the accent.
- **Not in the spec:** `Rtheme_set_accent` (an accent with its derived shades), `Rtheme_save`,
  theme classes, and reloading the files when they change on disk.
