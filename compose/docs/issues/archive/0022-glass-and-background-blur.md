---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0014, 0021]
assignee: Paul Hawke
---

# Glass and BackgroundBlur

## Question

How do Raven's glass themes, the `Material` attribute (Solid, Glass, FrostedGlass, TintedGlass) and the BackgroundBlur panel appear in compose-raven, as the `overlay` and `glass` ports need? Decide whether material is a parameter, a modifier or part of the theme, and what BackgroundBlur's composable looks like.

## Resolution

Facts: `Rwidget_set_material` works on any widget, and one that draws no surface gets one. Materials are `Solid Glass FrostedGlass TintedGlass Default`, and on glass a colour's alpha is the strength of the tint. `Rtheme_set_material` sets the material app-wide. BackgroundBlur is a separate single-child panel widget with a style (`Plain Frosted Glass Native`), radius, tint, saturation, grain, corner radii, padding and shadow. `overlay` also needs a transparent, borderless window (`R_WINDOW_TRANSPARENT`, `R_WINDOW_BORDERLESS`) and a backdrop that changes at run time (`Rwindow_set_backdrop`: `None Transparent Blur Acrylic Mica`).

- **`Modifier.material(material: Material)`** sets `Rwidget_set_material` on the composable's own widget, or on the outer widget for composables with an internal content node (`Button`, `Surface`, `Expander`, ...). Any widget can have a material, which makes it decoration in the sense of Compose's `background`/`border` modifiers, and it needs no extra node. The node folds it apart from the layout values and calls Raven only when it changes. `Material.Default` clears it. `Material` is an enum: `Default Solid Glass FrostedGlass TintedGlass`. This widens the glossary's **Modifier** to include material. Colours for a single component stay in parameters ([Theming](0021-theming.md)).
- **`Surface`** has no `material` parameter: `Surface(Modifier.material(Material.FrostedGlass), color = tint)`, where `color` tints the glass.
- **`RavenTheme(..., material: Material? = null)`** uses `Rtheme_set_material`. It is app-wide, so like `look` only the outermost `RavenTheme` may set it. Buttons and other controls get glass from the Glass and macOS Glass looks.
- **`BackgroundBlur(modifier, style: BlurStyle = BlurStyle.Frosted, shape: Shape = RectangleShape, tint: Color = Color.Unspecified, radius: Dp = Dp.Unspecified, saturation: Float? = null, grain: Float? = null, shadowElevation: Dp = 0.dp) { content }`** stays a container, because it is its own widget and a modifier would need a hidden wrapper node. Compose has no backdrop blur either (`Modifier.blur` blurs the element itself).
  - `BlurStyle` is `Plain Frosted Glass Native`.
  - The content goes in an internal Box, so inner padding is the content's own `Modifier.padding`.
  - `shape` uses the four corner radii, `radius = Unspecified` means the theme's `R_METRIC_BLUR_RADIUS`, and `shadowElevation` uses `Rbackground_blur_set_shadow`.
  - Guidance: use `Modifier.material(FrostedGlass)` for an ordinary glass panel, and `BackgroundBlur` for `Native` (the OS blurs the desktop), `Plain`, or control over radius, grain, saturation and tint.
- **Window**:
  - `ravenApplication(..., transparent: Boolean = false, undecorated: Boolean = false)` uses `R_WINDOW_TRANSPARENT` and `R_WINDOW_BORDERLESS`. `undecorated` is Compose Desktop's name and here means exactly that. `customHeader` keeps the OS's resizing.
  - `WindowBackdrop(backdrop: Backdrop)` is a declarative composable that calls `Rwindow_set_backdrop` whenever its value changes. It logs a warning when the window isn't transparent or the system refuses. `Backdrop` is `None Transparent Blur Acrylic Mica`.
- **Left out**: the theme's glass metrics (frost, refraction, saturation, rim, shadow) stay in theme files. Window background, wallpaper and clear colour join the Multiple windows fog.
