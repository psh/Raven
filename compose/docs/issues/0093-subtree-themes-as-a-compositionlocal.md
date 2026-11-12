---
status: open
---

# Subtree themes as a CompositionLocal

A window-level `RavenTheme(theme = ...)` puts its content in an internal Box (an Overlay widget)
with `Rwidget_set_theme`, so its children stack, it doesn't fill its slot, and it takes no
`Modifier`: `samples/themes`' Paper and GlassDark cards are only as wide as their content, where
the C example's fill the column. Compose's `MaterialTheme` emits no node and only provides
CompositionLocals. This ticket does the same for subtree themes. Spec:
[8: theming](../spec.md#phase-2-theming). Decided on 2026-11-12, from the gap the themes port
reported ([samples/themes](archive/0071-samples-themes.md)); it replaces the "internal Box" of
[RavenTheme and theme files](archive/0065-raventheme-and-theme-files.md).

**Depends on:** nothing open.

## Deliverables

- [ ] A window-level `RavenTheme(theme = ...)` emits no node: it provides the subtree theme through
      a CompositionLocal, and the internal theme Box is gone.
- [ ] The binding layer applies it to every node: each node calls `Rwidget_set_theme` on its own
      outer widget when the subtree theme it reads changes, with null when it leaves a themed
      subtree, so no composable handles it and content nodes inherit through Raven. Written so
      phase 3's subtree `fontFamily` (`Rwidget_set_font`) can follow the same pattern.
- [ ] Nested window-level `RavenTheme`s: the innermost wins; leaving it returns to the outer one.
- [ ] `samples/themes`' Paper and GlassDark cards fill the column as the C example's do; its
      ticket's note about the theme Box is corrected.
- [ ] Tests: several children inside a window-level `RavenTheme` lay out as their parent lays them
      out (no stacking); every widget in the subtree is drawn with the theme (`Rwidget_get_theme`);
      changing the theme, nesting, and removing it; a theme name Raven doesn't know is reported as
      before.
