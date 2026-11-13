---
status: closed
---

# Fonts and text styling

Font families, text styles, and the text parameters phase 2 left out; completes `Text(softWrap)` and `Text(fontFamily)` for `samples/overlay` and `samples/themes`. Spec: [8: Fonts and text styling](../../spec.md#fonts-and-text-styling-phase-3).

**Depends on:** [Images and resources](0115-images-and-resources.md).

## Deliverables

- [x] `FontFamily(vararg Font)` with `Font(path or bytes, weight, style)`, variants attached with `Rfont_set_variant`, cached per host; `FontFamily.Default` and `.Monospace`.
- [x] Two-step `FontWeight`, `FontStyle`, and `TextStyle` with `LocalTextStyle`/`ProvideTextStyle`.
- [x] `Text(fontFamily, fontWeight, fontStyle, textAlign, softWrap, style)`; `TextField(textStyle)` and `CodeEditor(textStyle)`.
- [x] `RavenTheme(fontFamily)` at application level (`Rtheme_set_font`) and for a subtree (`Rwidget_set_font`), as subtree themes do.
- [x] `MarkdownText(style)`, completing phase 2's.
- [x] Tests: a family's regular and bold variants, a style overridden by explicit parameters, wrapping, and a subtree font.

## Resolution

Done on macOS. 355 tests pass across the build, 12 of them new (`FontsTest`, in
compose-raven-foundation). No Raven change.

- **Fonts** (`compose-raven-ui`, `raven.compose.ui.text.font`): `FontWeight` (a class with W100 to
  W900 and the named weights; 600 and above is bold), `FontStyle` (`Normal`, `Italic`),
  `Font(path or bytes, weight, style)`, and `FontFamily(vararg Font)` with `FontFamily.Default`
  (`Rfont_get_default`) and `.Monospace` (`Rfont_get_monospace`).
  - A family is loaded on first use and cached in `HostCaches`, keyed by the family, which compares
    by its fonts (bytes by content), so equal families are one Raven font. The regular file (or the
    first, when none is regular) is the family's font; each other file is attached with
    `Rfont_set_variant` for its style, the first file of a style winning. A path resolves through
    `RavenHost.resolveResource`, as images do. A regular file Raven can't read falls back to Raven's
    default font, and Raven reports it.
  - `rememberRavenFont(family, weight, style, base)` (internal API) is the font a text widget gets:
    `R_INVALID_ID` (inherit) with no family and the regular style, the family's variant
    (`Rfont_get_variant`) with one, and, for bold or italic without a family, that style of the
    family a `RavenTheme` gives (`LocalFontFamily`), else of the theme's font, read once.
- **`TextStyle`** (`raven.compose.ui.text`): a data class of `color`, `fontSize`, `fontFamily`,
  `fontWeight`, `fontStyle` and `textAlign`, with `merge`; `LocalTextStyle` starts as
  `TextStyle.Default` (all the theme's), and `ProvideTextStyle` merges with the outer style.
  `TextAlign` has `Left`, `Right`, `Center`, `Start` and `End`; Raven has no justified text.
- **`Text(text, modifier, color, fontSize, fontFamily, fontWeight, fontStyle, textAlign, softWrap,
  style)`**: explicit parameters win over `style`. `Rlabel_set_font`, `_text_align` and `_wrap`.
- **`TextField(textStyle)`**, after `readOnly` as in Compose: Raven's text input has no font or size
  setter, so its font is the widget's own `Rwidget_set_font` and its size the widget's
  `R_METRIC_FONT_SIZE`. The node gained an own font (`RavenNode.setOwnFont`, internal API) that wins
  over its subtree's, since a subtree font uses the same call. The text colour wins over
  `colors.textColor`.
- **`CodeEditor(textStyle)`**, last: `Rtext_editor_set_font`, and its colour as `R_COLOR_TEXT`. The
  default is `TextStyle.Default`, the theme's in the fixed-width font, until 0117's `code` role.
- **`RavenTheme(fontFamily)`**: at application level, `HostTheme` sets `Rtheme_set_font`, again
  after each look or theme change, as with the accent; null leaves Raven's font, as the other
  app-wide settings do. In a window, `SubtreeSettings` gained `font`, which each node gives its own
  widget with `Rwidget_set_font`, as subtree themes do. Both provide `LocalFontFamily`.
- **`MarkdownText(markdown, modifier, style, onLinkClick)`**, in the spec's order: two tests that
  passed `onLinkClick` by position now name it.
- **Deviations**, in spec section 8 and the table of section 12:
  - `Text(softWrap)` is false by default, since a Raven label keeps to one line unless told to
    wrap, and Compose's true would change every existing layout.
  - A `TextField` keeps the theme's control height whatever its text size, and has no alignment;
    a `CodeEditor` has no alignment either.
- **Not observable**: Raven has no getters for a label's font, size, alignment or wrapping, or an
  editor's font, so the tests compare laid-out sizes. Raven's synthesised bold keeps the regular
  advances, so a bold Text as wide as the bold file alone, and wider than the regular, is drawn
  with the bold file. The alignment and the editor's style are only checked to reach Raven without
  error.
- **Test fonts**: Instrument Sans regular and bold from `tools/designer/fonts`, found from the
  working directory up.
- **Tests**: a family's regular and bold variants, 600 bold and 500 regular; the default and
  monospace families; explicit parameters winning over the style, and nested styles merging;
  wrapping only with `softWrap`; alignment; a window-level family as the subtree's font, a Text's
  own winning; bold without a family as the subtree family's bold; an application-level family as
  the theme's font; a `TextField`'s size, font, and own font winning over the subtree's; a
  `CodeEditor`'s style; a `MarkdownText`'s style; equal families loaded once.
