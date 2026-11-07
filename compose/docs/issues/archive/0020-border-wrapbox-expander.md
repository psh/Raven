---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0014]
assignee: Paul Hawke
---

# Border, WrapBox and Expander

## Question

How do Raven's Border (padding, background, border colour and width, corner radius, shadow), WrapBox and Expander appear in compose-raven, as the `components` port needs? Border overlaps Compose's `Modifier.background`/`border`/`clip`, but v1 has no drawing modifiers and one node per widget. Decide between a container composable (`Surface`/`Card`-like) and drawing modifiers, and map WrapBox to `FlowRow` and Expander to a composable.

## Resolution

Facts: Border has background colour, outline (width and colour), four corner radii, shadow (blur and offset), inner padding and material, and its child's slot padding applies inside it. WrapBox uses **one** spacing for both the gap between items and the gap between lines (`src/widgets/wrap_box.c`). Expander has a string title, an expanded flag and a change callback. Border and Expander take a single child.

- **`Surface(modifier, shape: Shape = RectangleShape, color: Color = Color.Unspecified, border: BorderStroke? = null, shadowElevation: Dp = 0.dp) { content }`** uses Border. It is a container, not Compose Foundation's `background`/`border` modifiers, which would need a hidden extra node (the same reason as the scroll containers). `Color.Unspecified` means the theme's panel colour. Inner padding is the content's own `Modifier.padding`, applied by Raven inside the Border, so there is no `contentPadding`, as in Compose. `shadowElevation` maps to Raven's blur = elevation and offset y = elevation / 2, a documented approximation. Material (glass) is for [Glass and BackgroundBlur](0022-glass-and-background-blur.md).
- **Several children in single-child widgets** (a v1 gap): a composable over a single-child widget emits two nodes, the widget and an internal content node holding the content. `Surface` and `Expander` use a **Box** (Overlay), because Compose's `Surface` stacks like a `Box`. `Button`, `OutlinedButton` and `TextButton` use a **Row** (HBox) with vertical centring, because Compose's button content is a `RowScope`. This amends [v1 component vocabulary](0011-v1-component-vocabulary.md), and the single-child handling of [Node model and Applier](0007-node-model-and-applier.md) still covers brief replacement.
- **Graphics types** in `raven.compose.ui.graphics`:
  - `Color` is a value class: `Color(0xFF3B82F6)`, `Color(red, green, blue, alpha)` as floats, and the constants `Black White Gray Red Green Blue Yellow Transparent Unspecified`.
  - `Shape` is sealed, because Raven only has rounded rectangles: `RectangleShape`, `RoundedCornerShape(all)`, `RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)`, and `CircleShape` (a very large radius that Raven clamps). Start/End mean left/right (no RTL).
  - `BorderStroke(width: Dp, color: Color)` uses `Rborder_set_outline`.

  [Theming](0021-theming.md) builds on `Color`.
- **`FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(x)) { }`** uses WrapBox. Raven's single spacing is both the gap between items and the gap between lines. There is no `verticalArrangement` (documented).
- **`Expander(title: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier) { content }`** uses Expander. It is a compose-raven name, controlled, with its content in an internal Box.
