---
status: open
---

# Component colours, shapes and padding

`XxxDefaults.colors()` on the phase 1 components and `Surface`, the hover and pressed shades they
imply, and `shape` and `contentPadding` on buttons and `TextField`. Spec:
[8: theming](../spec.md#phase-2-theming).

**Depends on:** [Surface, FlowRow and Expander](archive/0064-surface-flowrow-and-expander.md),
[RavenTheme and theme files](0065-raventheme-and-theme-files.md).

## Deliverables

- [ ] `colors =` on `Button`, `OutlinedButton`, `TextButton`, `Checkbox`, `RadioButton`, `Switch`,
      `Slider` and `TextField`, from `ButtonDefaults.buttonColors`, `CheckboxDefaults.colors`,
      `RadioButtonDefaults.colors`, `SwitchDefaults.colors`, `SliderDefaults.colors` and
      `TextFieldDefaults.colors`, mapped onto the Raven roles in the spec's table. Every default is
      `Unspecified`, and only the colours given call `Rwidget_set_color`.
- [ ] `color` and `trackColor` on `LinearProgressIndicator`, `color` on the dividers, and
      `contentColor` on `Surface`, passed down to its children as their `TEXT`.
- [ ] Setting `ACCENT` or `CONTROL` also sets the hovered and pressed roles, following the per-look
      rules of Raven's `rvn_theme_set_accent`, since Raven derives them only for a theme's accent
      ([verified](../../spikes/README.md)).
- [ ] `shape` on the buttons and `TextField` through `R_METRIC_CORNER_RADIUS`; a non-uniform shape
      uses `topStart` and logs a warning.
- [ ] `contentPadding: PaddingValues` on the buttons through `R_METRIC_PADDING_X`/`_Y`;
      non-symmetric values log a warning.
- [ ] Tests: a colour set and then unset returns to the theme's; an accent colour sets its hovered
      and pressed shades; the shape and padding warnings are logged.
