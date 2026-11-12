---
status: closed
---

# samples/themes

Port Raven's `examples/themes`: a gallery of controls under a choice of look, theme and dark mode,
with theme files loaded at start. Spec: [10. Samples](../../spec.md#10-samples),
[8: theming](../../spec.md#phase-2-theming).

**Depends on:** [ComboBox, SpinBox and codeEditorLanguages](0063-combobox-spinbox-and-codeeditorlanguages.md),
[RavenTheme and theme files](0065-raventheme-and-theme-files.md),
[Component colours, shapes and padding](0066-component-colors-shapes-and-padding.md),
[MarkdownText](0087-markdowntext.md).

## Deliverables

- [x] `samples/themes` with the C example's look: the controls of `themes.xml` in a splitter, the
      look and theme ComboBoxes, and a subtree using `paper.xml`.
- [x] `ocean.xml`, `paper.xml` and `tweaks.xml` loaded with `loadTheme` and chosen through an
      application-level `RavenTheme`.
- [x] The theme ComboBox lists `RavenTheme.themeNames`; Save writes `my-theme.xml` with
      `saveTheme`; the accent comes from a row of preset colour swatches (`Button`s with
      `colors =`) into `RavenTheme(accent)`.
- [x] The `danger` class becomes `colors =` on the buttons it styles. `tweaks.xml`'s class rules
      don't reach compose-raven widgets; its rules by widget type do.
- [x] The Markdown labels are `MarkdownText`, a link firing the C example's status message.
- [x] The Markdown editor is `CodeEditor(language = "Markdown", wordWrap = true)`, and `notes.md` is
      read again by a polling `LaunchedEffect`. Theme files reload through Raven by themselves.
- [x] Runs from Gradle like `samples/hello`, documented in `compose/README.md`.
- [x] A headless test: choosing a look or theme changes Raven's, and `RavenTheme.colors` follows.

## Gaps

- **Phase 3:** `ListView` ([Lists and trees](0033-lists-and-trees.md)).
- **Phase 5** (the specialist widgets): `ColorPicker`, replacing the accent swatches.
- **Phase 3** ([Fonts and text styling](0027-fonts-and-text-styling.md)): `Text(softWrap)`
  for the wrapped status line, and `fontFamily` for the C example's monospace label. Found while
  porting; not yet on `roadmap.md`.

The status line's "Loaded again" for interface files has no counterpart, since the interface is
Kotlin, and is no gap ([Theme extras the themes sample uses](0077-theme-extras-the-themes-sample-uses.md)).

## Resolution

Done and run on macOS. `./gradlew build allTests` passes: 290 tests, 8 of them the sample's.

- **The sample** (`raven.sample.themes`, executable `themes.kexe`): `main` is
  `application(liveReload = true) { ThemesApp(directory, onCloseRequest = ::exitApplication) }`.
  Live reload is on in a release build too, since watching the files is what the sample shows.
  - `ThemesApp` loads `ocean.xml`, `paper.xml` and `tweaks.xml` with `loadTheme` in a `remember`,
    and holds the look, the theme and the accent as state for an application-level
    `RavenTheme(look, dark, theme, accent)` around a 1180 by 760 `Window` (minimum 820 by 520).
  - The window is a `Row`: the appearance column (250 dp), a `VerticalDivider`, the cards in a
    `VerticalScroll`, another divider, and a 400 dp `VerticalSplitter` of the notes over the editor.
  - **Look**: a `ComboBox` of the C example's 16 looks, each a `Look` with `dark`. As Raven does,
    choosing a look leaves a theme that names its own (Ocean, Paper, a built-in one), so the theme
    becomes null and its ComboBox shows nothing; Tweaks, which names no look, stays.
  - **Theme**: a `ComboBox` of `RavenTheme.themeNames`, its selection the index of the theme.
  - **Accent**: six round swatches, `Button(shape = CircleShape, colors = buttonColors(...))`, the
    first the C example's `#22D3EE`, into `RavenTheme(accent)`. compose-raven applies it again after
    each look or theme change, where the C example's is reset.
  - **Save** (a `TextButton`) calls `saveTheme("my-theme.xml", "Mine")`, with the C example's
    status messages.
  - **Cards**: `Surface`s with the `card` class's corners. `danger` is `colors =` from
    `RavenTheme.colors.danger` on the Delete button and the Danger switch; the paper card uses
    paper.xml's `#B91C1C`, since `RavenTheme.colors` is the app theme's. The `eyebrow` labels are
    12 sp in the accent colour. The paper card is `RavenTheme(theme = "Paper")` and the glass one
    `RavenTheme(theme = "GlassDark")`, both inside the window.
  - **Markdown**: the labels are `MarkdownText`. The notes' `action:themes.docs` link gives the C
    example's status message through `onLinkClick`. The editor is
    `CodeEditor(language = "Markdown", wordWrap = true)`, starting with the C example's text.
    `notes.md` is read every 500 ms by a `LaunchedEffect`, with `delay`.
  - Opacity labels are the theme's text colour at alpha 0.65.
- **Files**: as `samples/xml-translator` reads Raven's `examples/`, the sample reads
  `examples/themes` where it is. A Gradle task writes its absolute path into the sample as
  `EXAMPLE_DIR`, as the C example compiles in `RAVEN_EXAMPLE_DIR`, so the executable works when
  started directly. A first argument names another folder.
- **Not counterparts, not gaps**: themes.xml's reload and the "Loaded again" status (the
  interface is Kotlin); the help text names `notes.md` instead of `themes.xml`.
- **Found here**:
  - Inside a window, `RavenTheme(theme = ...)` puts its content in a Box that takes no Modifier.
    In a `Column`, the Box takes the Column's alignment, so the paper and glass cards are as wide
    as their content, where the C example's fill the column. A `modifier`, or a theme Box that
    fills its slot, would fix it. The library is unchanged.
  - A non-null `onLinkClick` replaces Raven's handling of every link of the label, so a web link
    added to `notes.md` would show a status line instead of opening. compose-raven has no
    `UriHandler` to open it.
  - `Text` has no `softWrap` until phase 3, so the status line doesn't wrap (see Gaps).
- **Checked by eye** on macOS: the release executable starts with Ocean over Raven dark, and
  shows the paper and glass cards, the rendered notes and the editor, laid out as the C example.
- **README**: how to run it, and where it finds its files.
- **Tests** (`ThemesTest`, headless, on the real theme files):
  - at start the look is Raven dark and the theme Ocean. Choosing Windows light changes Raven's
    look and leaves Ocean; choosing Paper, then Tweaks, changes Raven's theme; Tweaks survives a
    look change. After each step, the status line's colour (from `RavenTheme.colors.text`) and the
    Delete button's (from `RavenTheme.colors.danger`) are the app theme's;
  - a swatch sets Raven's accent, and it survives a look change;
  - Save writes the theme in use under the name Mine;
  - the paper and glass cards keep their themes when the app theme changes;
  - `notes.md` rewritten on disk shows within a second;
  - the C example's notes are shown, its docs link gives its status message, and the 16 looks
    follow its order.
