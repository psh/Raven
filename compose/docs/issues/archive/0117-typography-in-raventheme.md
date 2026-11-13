---
status: closed
---

# Typography in RavenTheme

Eight text roles sized from Raven's base size, and the components' defaults. Spec: [8: Typography](../../spec.md#typography-phase-3).

**Depends on:** [Fonts and text styling](0116-fonts-and-text-styling.md).

## Deliverables

- [x] `Typography.fromTheme()` with `display` to `code`, sized by Raven's Markdown factors and recalculated on theme or look changes; `RavenTheme(typography)` and `RavenTheme.typography`; `copy(...)`.
- [x] Defaults: `Text` and `TextField` use `body`, button content `label`, `CodeEditor` `code`.
- [x] Tests: role sizes follow a theme's font size, and a nested typography changes a subtree.

## Resolution

Done on macOS. 359 tests pass across the build, 4 of them new (`TypographyTest`, in
compose-raven-foundation). No Raven change, and no deviation from the spec.

- **`Typography`** (`compose-raven-ui`, `theme/Typography.kt`): a data class of eight `TextStyle`
  roles, so `copy(...)` is the data class's. `Typography.fromTheme()` is a read-only composable on
  the companion: `display`, `headline`, `title`, `subtitle` and `caption` are sized 1.9, 1.52,
  1.26, 1.1 and 0.92 times the app theme's `R_METRIC_FONT_SIZE`, Raven's own Markdown heading
  factors (`heading_scale` in `src/widgets/markdown.c`); `body` and `label` are `TextStyle.Default`
  and `code` is `FontFamily.Monospace`, all three leaving the size to Raven.
- **Recalculated**: `HostTheme` keeps the base size as snapshot state, `baseFontSize`, read again in
  `refresh()` with the colours, which runs at start, after every app-wide change (look, theme,
  accent, material, font) and after Raven reloads a file. Readers of `fromTheme()` recompose when it
  changes; 0119's metrics will refresh it too.
- **`RavenTheme(typography: Typography? = null)`**, at application level or in a window: it provides
  `LocalTypography` and merges `body` into `LocalTextStyle`; null keeps the outer typography.
  `RavenTheme.typography` is the innermost given, else `Typography.fromTheme()`.
- **Defaults**: `Text` and `TextField` already read `LocalTextStyle`, which is `body`; button content
  gets `ProvideTextStyle(label)` (merged, as Material's buttons do); `CodeEditor(textStyle)` defaults
  to `code`. The spec says how nesting and the button merge work.
- **Not tested**: `CodeEditor`'s `code` default, since Raven has no getter for an editor's font.
- **Tests**: every role's size follows the theme's font size, before and after a look change to
  HighContrast, which has larger text; a title is taller and a caption shorter than the body; a
  nested typography changes its subtree's Text and nothing outside; button content uses `label`.
