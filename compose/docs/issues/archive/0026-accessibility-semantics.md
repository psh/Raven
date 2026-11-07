---
status: closed
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: Paul Hawke
---

# Accessibility semantics

## Question

How do Compose's semantics (`Modifier.semantics`, `contentDescription`, roles, `Modifier.clearAndSetSemantics`) map onto Raven's accessibility API (`Rwidget_set_accessible_role`, `_name`, `_description`, `_hidden`, `_labelled_by`, `RAccessibleRole`)? Decide which roles composables set by default, how a `Text` next to an input becomes its label, and whether the test harness finders should use semantics.

## Resolution

Facts (`raven_accessibility.h`): every Raven window is exposed to the platform screen reader (UI Automation, VoiceOver, AT-SPI) with nothing to set up. Widgets report their role, name, value and state themselves. A name is "the text they show, or the label just before them". Overrides: `Rwidget_set_accessible_name`, `_description`, `_role` (about 35 roles, including `NONE`, which flattens a widget into its parent, `HEADING` and `TOOLBAR`), `_hidden` (the whole subtree) and `_labelled_by`. Also `Raccessibility_announce`, `Raccessibility_follow_system` (text scale, contrast, motion), and right-to-left layout (`Rset_layout_direction`, which the phase 1 spec had left out by choice).

- **Defaults**: real widgets get nothing, because Raven describes them. compose-raven's **internal content nodes** (the Box or Row inside `Button`, `Surface`, `Expander`, `ContextMenuArea`, nested `RavenTheme`, ...) get `R_ROLE_NONE`, so the screen reader sees the tree the app wrote.
- **`Modifier.semantics { }`**, with Compose's properties:
  - `contentDescription` uses `Rwidget_set_accessible_name`.
  - `role = Role.Button | Checkbox | Switch | RadioButton | Tab | Image | DropdownList` uses the matching Raven role.
  - `heading()` uses `R_ROLE_HEADING`.
  - `hideFromAccessibility()` uses `Rwidget_set_accessible_hidden` (the subtree).
  - **compose-raven extensions** (documented): `accessibleRole = AccessibleRole.<any Raven role>` (Toolbar, StatusBar, Pane, ...) and `accessibleDescription = "..."`.
  - Left out (documented): `clearAndSetSemantics` and `mergeDescendants`, because Raven can't hide children apart from their parent.
- **Automatic labels** (option (b)):
  - **Inputs covered**: `Checkbox`, `Switch`, `RadioButton`, `Slider`, `TextField`, `ComboBox`, `SpinBox`.
  - **Rule**: an input with no `contentDescription`, whose sibling directly before it is not a `Text`, inside a `Row` or `FlowRow`, gets `Rwidget_set_accessible_labelled_by(input, text)` when the sibling directly after it is a `Text`. That is Compose's `Row { Checkbox(...); Text("Dark mode") }`. Raven itself already uses a `Text` before an input.
  - **Not in `Column`**, where text below an input is usually helper or error text.
  - **Updates**: the Applier re-checks a parent's children in `onEndChanges` after a child of it is inserted, moved or removed. `contentDescription` always wins.
  - The rule is spelled out in the spec.
- **Announcements**: `announceForAccessibility(text: String, assertive: Boolean = false)` uses `Raccessibility_announce`.
- **System settings**: the host calls `Raccessibility_follow_system()` at start, so text scale, contrast and reduced motion follow the OS. `application(followSystemAccessibility = false)` opts out. A Kotlin view of the settings (`LocalAccessibilitySettings`) waits until a sample needs it.
- **Tests**: the harness gains `onNodeWithContentDescription(...)`, which reads the semantics stored on each node. [Public test module for apps](0029-app-testing-module.md) inherits it.
- **Right-to-left**: a new Right-to-left layout fog patch on the map. This ticket only records that Raven supports it.
