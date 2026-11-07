---
status: open
wayfinder: grilling
parent: 0023
blocked_by: [0026]
assignee: null
---

# Toolbar

## Question

What is the shape of a `Toolbar { }` composable for `editor`, `workspace` and `studio`: a Row with toolbar spacing and padding, `TextButton`s as its usual content, and `R_ROLE_TOOLBAR` set for accessibility? Decide its defaults, whether it has its own scope (for example for separators between groups), and how it sits inside a `HeaderBar` or under a `MenuBar`.

## Resolution

Facts: Raven has no toolbar widget. `examples/editor/editor.xml` builds one from an HBox (`Spacing="4"`, padding `6,4`) of flat buttons, a filling Spacer and a status Label, with a Separator below. [Accessibility semantics](0026-accessibility-semantics.md) provides the toolbar role.

- **`Toolbar(modifier, contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { }`** is a compose-raven composable over one HBox, with `ToolbarScope : RowScope`.
  - The HBox gets `R_ROLE_TOOLBAR`.
  - Children are vertically centred by default, unlike `Row`, and `Modifier.align` overrides it.
  - `contentPadding` goes on the outer edges of the children's slots, because an HBox has no padding of its own.
  - Child styles are left alone: `TextButton` is already Raven's flat toolbar style, and apps choose each control.
- **`ToolbarSeparator()`**, in `ToolbarScope` only, is a `VerticalDivider` with `Modifier.padding(horizontal = 4.dp).fillMaxHeight()` between groups of tools.
- **Combining**: plain layout. `Column { MenuBar { }; Toolbar { }; HorizontalDivider() }` as in `editor`, a `Toolbar` inside a `HeaderBar` for a toolbar in the title bar, and a `Toolbar` inside `Surface(Modifier.material(Material.TintedGlass), shape = CircleShape)` for a floating glass toolbar, as in `glass`. The spec shows these as examples.
