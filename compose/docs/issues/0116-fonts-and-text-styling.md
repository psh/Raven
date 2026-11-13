---
status: open
---

# Fonts and text styling

Font families, text styles, and the text parameters phase 2 left out; completes `Text(softWrap)` and `Text(fontFamily)` for `samples/overlay` and `samples/themes`. Spec: [8: Fonts and text styling](../spec.md#fonts-and-text-styling-phase-3).

**Depends on:** [Images and resources](archive/0115-images-and-resources.md).

## Deliverables

- [ ] `FontFamily(vararg Font)` with `Font(path or bytes, weight, style)`, variants attached with `Rfont_set_variant`, cached per host; `FontFamily.Default` and `.Monospace`.
- [ ] Two-step `FontWeight`, `FontStyle`, and `TextStyle` with `LocalTextStyle`/`ProvideTextStyle`.
- [ ] `Text(fontFamily, fontWeight, fontStyle, textAlign, softWrap, style)`; `TextField(textStyle)` and `CodeEditor(textStyle)`.
- [ ] `RavenTheme(fontFamily)` at application level (`Rtheme_set_font`) and for a subtree (`Rwidget_set_font`), as subtree themes do.
- [ ] `MarkdownText(style)`, completing phase 2's.
- [ ] Tests: a family's regular and bold variants, a style overridden by explicit parameters, wrapping, and a subtree font.
