---
status: closed
wayfinder: grilling
parent: 0023
blocked_by: [0027]
assignee: Paul Hawke
---

# Typography in RavenTheme

## Question

What rigorous typography does `RavenTheme` offer beyond a single `fontFamily` and the starting `LocalTextStyle` from [Fonts and text styling](0027-fonts-and-text-styling.md)? Decide:
- whether there is a scale of named text styles, and what its roles are called. Material 3's `displayLarge` ... `labelSmall` is familiar but Material is out of scope, so a smaller Raven-native set (for example body, label, title, heading, code) is the alternative.
- how the scale is read (`RavenTheme.typography.body`), and how components pick their default style (for example `Text` uses body, `Button` uses label, `CodeEditor` uses code)
- how the scale derives from Raven's own metrics (`R_METRIC_FONT_SIZE`) and theme files, so that a Raven look or theme file still sets the sizes
- how it follows the system's text scale (`Raccessibility_set_text_scale`, already followed by the host)
- how `MarkdownText`'s headings relate to the scale

## Resolution

Facts: Raven has one font-size metric (`R_METRIC_FONT_SIZE`), set by looks and theme files, and theme styles set `FontSize` per widget type or class. Raven applies the accessibility text scale itself. Markdown headings scale the base size by 1.9, 1.52, 1.26, 1.1, 1.0 and 0.92 for h1 to h6 (`src/widgets/markdown.c`).

- **Roles**: compose-raven's own small set (Material's fifteen stay out of scope), sized from Raven's base size *B* with Raven's Markdown factors, so composables and `MarkdownText` match:

  | Role | Size | Markdown |
  |---|---|---|
  | `display` | 1.9 B | h1 |
  | `headline` | 1.52 B | h2 |
  | `title` | 1.26 B | h3 |
  | `subtitle` | 1.1 B | h4 |
  | `body` | B | text |
  | `label` | B | |
  | `caption` | 0.92 B | h6 |
  | `code` | B, monospace | code |
- **API**: `Typography(display, headline, title, subtitle, body, label, caption, code: TextStyle)`, an immutable data class with `copy`. `RavenTheme(typography = Typography.fromTheme())` sets it, and `RavenTheme.typography.<role>` reads it, as a CompositionLocal like `RavenTheme.colors`.
  - In the default, `body`, `label` and `code` keep `fontSize = TextUnit.Unspecified`, so Raven's sizes (theme files, per-type styles, looks) still apply. The scaled roles get explicit sizes (factor × `Rtheme_get_metric(FONT_SIZE)`), recalculated when the outermost theme or look changes.
  - The accessibility text scale is not applied twice, because Raven scales all text. **Verify first** that Raven also scales explicit sizes (`Rlabel_set_text_size`), and if not, compose-raven multiplies them itself.
- **Component defaults**: `LocalTextStyle` starts as `typography.body`. `Button`/`OutlinedButton`/`TextButton` wrap their content in `ProvideTextStyle(typography.label)`. `TextField` defaults to `typography.body` and `CodeEditor` to `typography.code`. Raven-drawn text (menu items, tab titles, tree and list rows, header bar and dock panel titles) keeps Raven's theme font and size (documented).
- **Custom typography**: `RavenTheme(typography = Typography.fromTheme().copy(display = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 32.sp), ...))`. A nested `RavenTheme(typography)` changes it for a subtree through the CompositionLocal, with no Raven call. Explicit sizes win over a theme file's `FontSize` (documented).
- **`MarkdownText`**: headings follow Raven's fixed factors, which the default typography matches. A customised typography does not reach Markdown headings, because Raven takes no per-level fonts (documented). Its `style` still sets the base font and size.
