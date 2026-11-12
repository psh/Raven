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
- [ ] The theme ComboBox lists `RavenTheme.themeNames`; Save writes `my-theme.xml` with
      `saveTheme`; the accent comes from a row of preset colour swatches (`Button`s with
      `colors =`) into `RavenTheme(accent)`.
- [ ] The `danger` class becomes `colors =` on the buttons it styles. `tweaks.xml`'s class rules
      don't reach compose-raven widgets; its rules by widget type do.
- [ ] The Markdown editor is `CodeEditor(language = "Markdown", wordWrap = true)`, and `notes.md` is
      read again by a polling `LaunchedEffect`. Theme files reload through Raven by themselves.
- [ ] Runs from Gradle like `samples/hello`, documented in `compose/README.md`.
- [ ] A headless test: choosing a look or theme changes Raven's, and `RavenTheme.colors` follows.

## Gaps

- **Phase 3:** `ListView` ([Lists and trees](archive/0033-lists-and-trees.md)) and the Markdown
  labels (`MarkdownText`, [Fonts and text styling](archive/0027-fonts-and-text-styling.md)).
- **Phase 4** (the specialist widgets): `ColorPicker`, replacing the accent swatches.

The status line's "Loaded again" for interface files has no counterpart, since the interface is
Kotlin, and is no gap ([Theme extras the themes sample uses](archive/0077-theme-extras-the-themes-sample-uses.md)).
