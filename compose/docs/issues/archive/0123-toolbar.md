---
status: closed
---

# Toolbar

A row with the toolbar role. Spec: [8: Toolbar](../../spec.md#toolbar-phase-3).

**Depends on:** nothing in phase 3.

## Deliverables

- [x] `Toolbar(modifier, contentPadding, horizontalArrangement) { }`, an HBox with `R_ROLE_TOOLBAR`, children centred vertically, and `ToolbarSeparator()` in `ToolbarScope`.
- [x] Tests: the role, the separator, and a toolbar inside a `HeaderBar`.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 331 tests, 3 of them `ToolbarTest`'s.

- **`Toolbar(modifier, contentPadding = PaddingValues(6.dp, 4.dp), horizontalArrangement =
  Arrangement.spacedBy(4.dp)) { }`**, in `raven.compose.foundation` (`Toolbar.kt`), places its
  children left to right, `horizontalArrangement` apart, each centred vertically, inside
  `contentPadding`. It draws nothing itself, so it goes under a `MenuBar`, in a `HeaderBar`, or on
  a `Surface` for a floating one.
- **Deviation**: the spec called it "an HBox with `R_ROLE_TOOLBAR`", but Raven's HBox has no
  padding of its own (only its children's slots do), so `contentPadding` had nowhere to go. The
  toolbar is an Overlay with `R_ROLE_TOOLBAR`, which takes the Modifier, holding one HBox
  (`R_ROLE_NONE`) that its children are in, with `Modifier.padding(contentPadding).fillMaxSize()`.
  Laid out, it is the same as a padded HBox, and assistive technology sees one toolbar. Spec
  section 8's Toolbar says so.
- **`ToolbarScope`** is a `RowScope`, so `Spacer(Modifier.weight(1f))` pushes what follows to the
  end, as the spec's editor toolbar does, and adds **`ToolbarSeparator(modifier)`**, a
  `VerticalDivider` as high as the row.
- **Tests** (`ToolbarTest`): the role, the default padding and spacing, and children centred
  vertically; the separator is a vertical Separator spanning the row, and a weighted Spacer pushes
  the last child to the end; a toolbar in a `HeaderBar` keeps its role and one HBox, and is centred
  in the bar.
