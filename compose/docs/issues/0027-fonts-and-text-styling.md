---
status: open
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: null
---

# Fonts and text styling

## Question

How do Compose's `FontFamily`, `FontWeight`, `FontStyle` and `TextStyle` (`Text(style = ...)`) map onto Raven's fonts (font files loaded as `Rid`, `Rtheme_set_font`, `Rwidget_set_font`, `Rlabel_set_font`, `Rtext_editor_set_font`)? Decide how font files are loaded and referenced, whether `RavenTheme` gets a typography, what `fontWeight` can mean when Raven only knows font files, and whether Raven's style classes are exposed after all.
