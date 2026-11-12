---
status: closed
---

# Component colours, shapes and padding

`XxxDefaults.colors()` on the phase 1 components and `Surface`, the hover and pressed shades they
imply, and `shape` and `contentPadding` on buttons and `TextField`. Spec:
[8: theming](../spec.md#phase-2-theming).

**Depends on:** [Surface, FlowRow and Expander](archive/0064-surface-flowrow-and-expander.md),
[RavenTheme and theme files](archive/0065-raventheme-and-theme-files.md).

## Deliverables

- [x] `colors =` on `Button`, `OutlinedButton`, `TextButton`, `Checkbox`, `RadioButton`, `Switch`,
      `Slider` and `TextField`, from `ButtonDefaults.buttonColors`, `CheckboxDefaults.colors`,
      `RadioButtonDefaults.colors`, `SwitchDefaults.colors`, `SliderDefaults.colors` and
      `TextFieldDefaults.colors`, mapped onto the Raven roles in the spec's table. Every default is
      `Unspecified`, and only the colours given call `Rwidget_set_color`.
- [x] `color` and `trackColor` on `LinearProgressIndicator`, `color` on the dividers, and
      `contentColor` on `Surface`, passed down to its children as their `TEXT`.
- [x] Setting `ACCENT` or `CONTROL` also sets the hovered and pressed roles, following the per-look
      rules of Raven's `rvn_theme_set_accent`, since Raven derives them only for a theme's accent
      ([verified](../../spikes/README.md)).
- [x] `shape` on the buttons and `TextField` through `R_METRIC_CORNER_RADIUS`; a non-uniform shape
      uses `topStart` and logs a warning.
- [x] `contentPadding: PaddingValues` on the buttons through `R_METRIC_PADDING_X`/`_Y`;
      non-symmetric values log a warning.
- [x] Tests: a colour set and then unset returns to the theme's; an accent colour sets its hovered
      and pressed shades; the shape and padding warnings are logged.

## Resolution

Done on macOS. 79 tests pass in foundation, 213 in all.

- **Colours**: `ButtonDefaults.buttonColors`, `CheckboxDefaults`, `RadioButtonDefaults`,
  `SwitchDefaults`, `SliderDefaults` and `TextFieldDefaults.colors` return `@Immutable` data
  classes (`ButtonColors`, ...), passed as `colors =` after each composable's existing parameters.
  All three buttons take `buttonColors`: `Button` maps it to `ACCENT`/`ACCENT_TEXT`, the others to
  `CONTROL`/`TEXT`. `LinearProgressIndicator` takes `color` and `trackColor`, the dividers `color`,
  and `Surface` `contentColor` (after `color`, as in Material). One shared property,
  `WidgetColors` in the new `ComponentStyle.kt`, sets each role's colour with `Rwidget_set_color`,
  or clears the widget's own with `Rwidget_clear_color` when it is `Unspecified`.
- **Shades**: an `ACCENT` or `CONTROL` colour also sets its `_HOVERED` and `_PRESSED` roles with
  `rvn_theme_set_accent`'s per-family rules (Raven, Windows, macOS, GNOME), to the same 8 bits.
  The family is read from `Rlook_get`, `Native` being the platform's, and the darkness from
  `Rtheme_is_dark`. The bound value carries `RavenTheme.colors`, so a look or theme change sets
  the colours again with that look's shades. Raven doesn't tell the family of a theme file that
  names its own look, or of a subtree theme, so those shade as the app look does.
- **Surface's `contentColor`** is the Border's `TEXT`, which Raven passes down while painting.
  `Rwidget_get_color` on a child doesn't show it, so the test checks that the child keeps no
  colour of its own.
- **Shape and padding**: buttons take `shape: Shape? = null` and `contentPadding: PaddingValues? =
  null`, and `TextField` takes `shape`, null being the theme's metric (`Rwidget_clear_metric`).
  `Shape` is sealed and has no "the theme's" value, so null stands in for Material's
  `ButtonDefaults.shape` and `ContentPadding`. A non-uniform shape uses `topStart`, and padding
  that differs between opposite sides uses `start` and `top`; each logs a warning naming the
  composable, every time it is set.
- **`PaddingValues`** is new in `raven.compose.ui`, as the theming decision says, with Compose's
  `PaddingValues(all)`, `(horizontal, vertical)` and `(start, top, end, bottom)` forms.
- **Warnings**: the binding layer exposes the layout warnings' sink as `parameterWarningSink`
  (internal API), so foundation logs to it and its tests collect from it.
- **Tests**:
  - every composable's colours, set and then unset, return to the theme's;
  - a Surface's content colour is its text colour, and its child keeps none of its own;
  - `Button`'s accent shades and `OutlinedButton`'s control shades equal Raven's own shades of the
    same accent under twelve looks (each family, light and dark), switched through `RavenTheme`;
  - shape and padding set the metrics, and return to the theme's;
  - a non-uniform shape and non-symmetric padding log their warnings.
