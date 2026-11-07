---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0014]
assignee: Paul Hawke
---

# Theming

## Question

How do Raven's themes and styles (`Rtheme_apply`, theme files, `Class`, `Color.<Role>`, `Metric.<Name>`, text colour and size) appear in compose-raven, as the `themes` port needs? Compose's convention is a theme composable with CompositionLocals (`MaterialTheme { }`). Decide whether compose-raven offers a `RavenTheme` over Raven's theme engine, how per-widget colour and metric overrides are spelled (parameters or modifiers), where `Text`'s `color`/`fontSize` land, and whether a Material-like module above foundation is wanted.

## Resolution

Facts (`raven_style.h`): a **look** is app-wide only (`Rlook_set`: Raven, Glass, HighContrast, Windows, macOS, GNOME, Native, macOS Glass, each dark and light). A **theme** is laid over it: named or loaded from XML (`Rtheme_load`/`Rtheme_use`), and a subtree can have its own (`Rwidget_set_theme`). **Styles** are theme rules by widget type or `Class`. **Per-widget overrides** are `Rwidget_set_color`/`_metric`/`_class`/`_font`. A value is looked up widget, then styles, then theme, and the text colour is inherited by children. Raven does the inheritance through the tree itself.

- **`RavenTheme(look: Look? = null, dark: Boolean? = null, theme: String? = null) { content }`**, in Compose's `MaterialTheme { }` shape.
  - `look` + `dark` combine into an `RLook` for `Rlook_set`. This is app-wide, so only the outermost `RavenTheme` may set them, and a nested one doing so logs an error.
  - `theme` names a loaded theme. The outermost `RavenTheme` uses `Rtheme_use`. A nested one emits an internal Box with `Rwidget_set_theme`, giving that subtree its own theme.
  - `Look` is a Kotlin enum without the dark/light suffix: `Raven Glass HighContrast Windows Macos Gnome Native MacosGlass`.
- **Theme files**: `loadTheme(path): String` and `loadThemeFromString(xml): String` (`Rtheme_load`/`_from_memory`) return the name to pass to `theme =`. They are valid only while Raven runs, and Raven's live reload of theme files keeps working.
- **Reading values**: `RavenTheme.colors`, a CompositionLocal with one `Color` per Raven colour role (`accent`, `panel`, `text`, `danger`, `success`, ...). It is read with `Rtheme_get_color` and refilled when the outermost look or theme changes. It holds app-wide values only, because subtree themes aren't visible to Raven's getters (documented).
- **Per-widget overrides, Material-style** (option (a), faithful to Compose; `Modifier` stays placement-only). `XxxDefaults.colors(...)` returns an immutable `XxxColors`, passed as `colors =`. Every default is `Color.Unspecified`, meaning the Raven theme's value. The node calls `Rwidget_set_color(role, ...)` only for the colours given.

  | Composable | Parameter | Raven roles |
  |---|---|---|
  | `Button`, `OutlinedButton`, `TextButton` | `ButtonDefaults.buttonColors(containerColor, contentColor, disabledContentColor)` | `ACCENT` (`CONTROL` for Outlined/Text), `ACCENT_TEXT` (`TEXT`), `TEXT_DISABLED` |
  | `Checkbox` | `CheckboxDefaults.colors(checkedColor, uncheckedColor, checkmarkColor)` | `ACCENT`, `CONTROL_BORDER`, `ACCENT_TEXT` |
  | `RadioButton` | `RadioButtonDefaults.colors(selectedColor, unselectedColor)` | `ACCENT`, `CONTROL_BORDER` |
  | `Switch` | `SwitchDefaults.colors(checkedTrackColor, uncheckedTrackColor, thumbColor)` | `SWITCH`, `TRACK`, `HANDLE` |
  | `Slider` | `SliderDefaults.colors(thumbColor, activeTrackColor, inactiveTrackColor)` | `HANDLE`, `ACCENT`, `TRACK` |
  | `TextField` | `TextFieldDefaults.colors(containerColor, textColor, placeholderColor, focusedIndicatorColor, selectionColor)` | `INPUT`, `TEXT`, `TEXT_PLACEHOLDER`, `FOCUS`, `SELECTION` |
  | `LinearProgressIndicator` | `color`, `trackColor` | `ACCENT`, `TRACK` |
  | `HorizontalDivider`, `VerticalDivider` | `color` | `SEPARATOR` |
  | `Surface` | `contentColor` | `TEXT` on the Border, inherited by children |

  Documented gaps: Raven has no role for a disabled container, so there is no `disabledContainerColor`. Raven has `ACCENT_HOVERED/_PRESSED` and `CONTROL_HOVERED/_PRESSED`. The implementation first checks whether Raven derives them from the base colour, and if not, compose-raven sets slightly lighter and darker shades. Composables with no Compose counterpart (`ComboBox`, `SpinBox`, `Expander`, `CodeEditor`, `HeaderBar`, menus) get no `colors` here.
- **Shape and padding**: `Button`'s `shape` and `contentPadding: PaddingValues`, and `TextField(shape)`. `shape` uses `R_METRIC_CORNER_RADIUS`, which is a single radius, so a non-uniform `RoundedCornerShape` uses `topStart` and warns. `contentPadding` uses `R_METRIC_PADDING_X`/`_Y` (start, top), and non-symmetric values warn. `PaddingValues` is new in `raven.compose.ui`.
- **Text**: `Text(color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified)`, with a new `.sp` unit. 1.sp is one Raven text-size unit (`Rlabel_set_text_size`), and `color` uses `Rlabel_set_color`.
- **Style classes** (`Class="danger"`) are not exposed, because Compose has no such concept. Theme rules by widget type still apply, but rules by class don't reach compose-raven widgets (documented).
- **Material-like module**: ruled out of scope. Raven's looks already imitate each platform.
- **Fonts** (`FontFamily`, `FontWeight`, `Text.style`, `Rtheme_set_font`/`Rwidget_set_font`) go to a new Fonts and text styling fog patch.
