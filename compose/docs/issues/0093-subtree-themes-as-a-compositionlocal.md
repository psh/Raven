---
status: closed
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

- [x] A window-level `RavenTheme(theme = ...)` emits no node: it provides the subtree theme through
      a CompositionLocal, and the internal theme Box is gone.
- [x] The binding layer applies it to every node: each node calls `Rwidget_set_theme` on its own
      outer widget when the subtree theme it reads changes, with null when it leaves a themed
      subtree, so no composable handles it and content nodes inherit through Raven. Written so
      phase 3's subtree `fontFamily` (`Rwidget_set_font`) can follow the same pattern.
- [x] Nested window-level `RavenTheme`s: the innermost wins; leaving it returns to the outer one.
- [x] `samples/themes`' Paper and GlassDark cards fill the column as the C example's do; its
      ticket's note about the theme Box is corrected.
- [x] Tests: several children inside a window-level `RavenTheme` lay out as their parent lays them
      out (no stacking); every widget in the subtree is drawn with the theme (`Rwidget_get_theme`);
      changing the theme, nesting, and removing it; a theme name Raven doesn't know is reported as
      before.

## Resolution

Done and run on macOS. `./gradlew build allTests` passes: 313 tests, 5 of them new in ui's
`RavenThemeTest` and 1 in the themes sample's `ThemesTest`.

- **`RavenTheme` inside a window** emits no node: it provides `LocalSubtreeSettings` with its
  `theme`, and a null one keeps the outer value, so the innermost theme wins and an unset one
  follows the outer theme. The provider is there even for null, so changing the theme to or from
  null keeps the content's state. `ThemeBoxWidget` and its `WidgetTheme` property are gone.
- **The binding layer**: `SubtreeSettings(theme)` and the static `LocalSubtreeSettings` live in
  `Binding.kt`. `RavenWidget` is the one place every node is emitted through (no main-source
  composable emits a `ComposeNode` another way). It reads the local and binds it with
  `set(subtree) { setSubtreeSettings(it) }` next to the modifier. `RavenNode.setSubtreeSettings`
  diffs against what it last applied, as `setModifier` does, and calls `Rwidget_set_theme` on the
  node's outer widget only when the theme changed. A node outside a themed subtree never calls
  it, and a node leaving one calls it with null. Content nodes don't apply it: they inherit it
  from their widget through Raven. Phase 3's `fontFamily` becomes one more `SubtreeSettings`
  property with its own diff line (`Rwidget_set_font`).
- **An unknown name**: Raven reports it, once. `HostTheme.setWidgetTheme` remembers the names
  Raven refused and doesn't ask for them again, so one name gets one report instead of one per
  widget. The widgets keep their themes, as Raven leaves them. The set is cleared when
  `themeNames` changes (a theme file loaded or renamed), since the name may then exist.
- **`samples/themes`**: its source is unchanged, since its `Card` already has `fillMaxWidth()`.
  Without the Box, the Paper and GlassDark cards fill the column. The note in
  [samples/themes](archive/0071-samples-themes.md) says this ticket fixed it.
- **Checked by eye** on macOS: in the release executable, the paper and glass cards are as wide as
  the controls card, as the C example's are.
- **Harness**: ui's `TestSupport` has `takeRavenErrors()`, for a test that expects Raven to
  report an error. `RavenThemeTest`'s labels now go through `RavenWidget`, so they apply the
  subtree theme as every composable's widget does.
- **Tests**:
  - two labels in a window-level `RavenTheme` are children of the column itself, one under the
    other. The first fills the column's width through its Modifier, and both have the theme;
  - a column, a button, the button's content node and label, and a nested label are all drawn
    with the theme. The widgets outside it have the app theme;
  - nested themes: the innermost wins and both follow their changes. An unset inner theme follows
    the outer one, and with neither set everything follows the app theme;
  - a node moved out of a theme (`movableContentOf`, the same widget) follows its parent again;
  - an unknown name gives one Raven error for two labels, and applies once it is loaded;
  - the sample's paper and glass cards are children of the cards' column, as wide as the
    controls card.
