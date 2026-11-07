---
status: open
wayfinder: grilling
parent: 0023
blocked_by: [0027]
assignee: null
---

# Typography in RavenTheme

## Question

What rigorous typography does `RavenTheme` offer beyond a single `fontFamily` and the starting `LocalTextStyle` from [Fonts and text styling](archive/0027-fonts-and-text-styling.md)? Decide:
- whether there is a scale of named text styles, and what its roles are called. Material 3's `displayLarge` ... `labelSmall` is familiar but Material is out of scope, so a smaller Raven-native set (for example body, label, title, heading, code) is the alternative.
- how the scale is read (`RavenTheme.typography.body`), and how components pick their default style (for example `Text` uses body, `Button` uses label, `CodeEditor` uses code)
- how the scale derives from Raven's own metrics (`R_METRIC_FONT_SIZE`) and theme files, so that a Raven look or theme file still sets the sizes
- how it follows the system's text scale (`Raccessibility_set_text_scale`, already followed by the host)
- how `MarkdownText`'s headings relate to the scale
