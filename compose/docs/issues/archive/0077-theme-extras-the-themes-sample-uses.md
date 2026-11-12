---
status: closed
wayfinder: grilling
parent: 0073
blocked_by: []
assignee: Paul Hawke
---

# Theme extras the themes sample uses

## Question

`themes` uses theme features the spec doesn't cover: `Rtheme_set_accent` (one colour and the
shades Raven derives from it), `Rtheme_save`, style classes (`Class="danger"`, which phase 3's
[Fonts and text styling](0027-fonts-and-text-styling.md) kept out), and reloading theme
and content files when they change on disk (`Rxml_set_reload_callback`). For each: its
compose-raven shape (a `RavenTheme` parameter, a function, a deliberate non-goal with Compose's
answer instead), and is it phase 2 or a named later phase? Read `examples/themes/` for what each
does there.

## Resolution

The accent, saving and listing themes come into phase 2, in
[RavenTheme and theme files](0065-raventheme-and-theme-files.md); the rest needs nothing new.

- **Accent**: `RavenTheme(accent: Color?)`, application level only, through `Rtheme_set_accent`,
  so Raven derives the hovered, pressed, focus, selection and link colours. A look or theme
  change resets it, so compose-raven applies it again.
- **The picker**: `ColorPicker` stays phase 4 (the specialist widgets, named on the roadmap). The
  phase 2 port picks from a row of preset swatches, `Button`s with `colors =`, which phase 4
  replaces.
- **Saving**: `saveTheme(path, name): Boolean` over `Rtheme_save`, the accent included.
- **Listing**: `RavenTheme.themeNames`, read-only snapshot state refreshed after `loadTheme`, a
  look change and Raven's reload of a theme file. Found missing here: the theme ComboBox needs it.
- **Reloading**: theme files reload through Raven by themselves (`Rtheme_load` watches them). The
  interface is Kotlin, so interface-file reload and the C example's "Loaded again" status have no
  counterpart, which is no port gap; `notes.md` is polled by a `LaunchedEffect`. No public reload
  callback.
- **Classes** stay as [Theming](0021-theming.md) settled them: `danger` becomes `colors =`, and
  `tweaks.xml`'s rules by widget type still apply.
- **The Markdown editor** is `CodeEditor(language = "Markdown", wordWrap = true)`, no gap. The
  Markdown labels stay a port gap of phase 3's `MarkdownText`, and `ListView` one of phase 3's
  lists, both now on `roadmap.md`'s list of what phase 3 completes.
