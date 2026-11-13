---
status: open
---

# Theming extras

The colour, metric and header additions this map decided. Spec: [8: theming](../spec.md#phase-2-theming), [8: menus and the header bar](../spec.md#phase-2-menus-and-the-header-bar).

**Depends on:** [Typography in RavenTheme](archive/0117-typography-in-raventheme.md).

## Deliverables

- [ ] Explicit hovered and pressed colours on `ButtonDefaults.buttonColors`, `CheckboxDefaults.colors`, `RadioButtonDefaults.colors` and `SliderDefaults.colors`, each used as given when set ([Music's class-styled themes](archive/0099-musics-class-styled-themes.md)).
- [ ] `TextFieldDefaults.colors(unfocusedIndicatorColor)` over `CONTROL_BORDER` and `BORDER`.
- [ ] `RavenTheme(metrics: Map<ThemeMetric, Float>)` at application level, applied again after look and theme changes; a changed `FontSize` moves `Typography.fromTheme()`'s base ([The editor's run-time extras](archive/0102-the-editors-run-time-extras.md)).
- [ ] `HeaderBar(background)` over `Rheader_bar_set_background` ([DockArea and HeaderBar extras](archive/0105-dockarea-and-headerbar-extras.md)).
- [ ] Tests: explicit and derived shades, a frameless TextField, metrics surviving a look change, a header bar without background.
