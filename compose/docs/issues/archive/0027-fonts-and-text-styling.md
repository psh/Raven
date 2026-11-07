---
status: closed
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: Paul Hawke
---

# Fonts and text styling

## Question

How do Compose's `FontFamily`, `FontWeight`, `FontStyle` and `TextStyle` (`Text(style = ...)`) map onto Raven's fonts (font files loaded as `Rid`, `Rtheme_set_font`, `Rwidget_set_font`, `Rlabel_set_font`, `Rtext_editor_set_font`)? Decide how font files are loaded and referenced, whether `RavenTheme` gets a typography, what `fontWeight` can mean when Raven only knows font files, and whether Raven's style classes are exposed after all.

## Resolution

Facts (`raven_resources.h`, `raven_widget.h`): a font is an `Rid` loaded from a `.ttf`/`.otf` file or bytes (`Rfont_create_from_file`, `Rfont_create`). Raven embeds Roboto (`Rfont_get_default`) and Roboto Mono (`Rfont_get_monospace`). There are exactly four styles, Regular, Bold, Italic and BoldItalic. `Rfont_set_variant` attaches the file for a style, and without one Raven synthesises bold and italic, keeping advances. `Rfont_set_fallback` chains fonts, and Raven also falls back to system fonts. `Rwidget_set_font` is inherited by the subtree, `Rtheme_set_font` is app-wide, and labels can render Markdown.

- **`FontFamily(vararg fonts: Font)`**, with `Font(path: String, weight = FontWeight.Normal, style = FontStyle.Normal)` and `Font(bytes: ByteArray, ...)`:
  - The regular font is the family's Raven handle, and the others attach with `Rfont_set_variant`.
  - It is loaded on first use and cached per family until shutdown.
  - `FontFamily.Default` is Roboto and `FontFamily.Monospace` is Roboto Mono.
  - Relative paths resolve as [Image and resources](0035-image-and-resources.md) decides. Until then they are passed to Raven as given.
- **Weight and style**: `FontWeight.W100` to `W500` mean Regular and `W600` to `W900` mean Bold, because Raven has only those two (documented). `FontStyle.Normal`/`Italic` pass through. Missing files get Raven's synthesised styles.
- **Text**:
  - `Text(text, ..., fontFamily, fontWeight, fontStyle, style: TextStyle = LocalTextStyle.current)`. Explicit parameters override `style`, as in Compose. The node calls `Rlabel_set_font(Rfont_get_variant(family, style))` and `Rlabel_set_text_size`.
  - `TextStyle(color, fontSize, fontFamily, fontWeight, fontStyle, textAlign)`.
  - `LocalTextStyle`, with `ProvideTextStyle(style) { }`.
  - `TextField(textStyle = ...)` and `CodeEditor(textStyle = ...)` use `Rwidget_set_font` and `Rtext_editor_set_font`.
- **Theme**: `RavenTheme(fontFamily: FontFamily? = null)`. The outermost uses `Rtheme_set_font`, and a nested one uses `Rwidget_set_font` on its internal Box, which Raven passes down to its subtree. The starting `LocalTextStyle` comes from the theme (Raven's font size metric). There is no scale of named styles here: a more rigorous typography is its own ticket, [Typography in RavenTheme](0040-typography-in-raventheme.md).
- **`MarkdownText(markdown: String, modifier, style, onLinkClick: ((String) -> Unit)? = null)`** uses `Rlabel_set_markdown` and `Rlabel_set_link_callback`, drawing with the family's real bold and italic variants. `AnnotatedString` is left out, because Raven can't draw spans (documented).
- **Style classes** stay unexposed, as [Theming](0021-theming.md) decided. This settles the open note in the fog.
