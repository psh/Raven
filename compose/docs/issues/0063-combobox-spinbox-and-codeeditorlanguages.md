---
status: closed
---

# ComboBox, SpinBox and codeEditorLanguages

Three controlled inputs and the list of editor languages, for the `notepad`, `themes` and
`overlay` ports (and `glass` in phase 3). Spec:
[8: ComboBox and SpinBox](../spec.md#phase-2-combobox-and-spinbox).

**Depends on:** nothing open.

## Deliverables

- [x] `ComboBox(items, selectedIndex, onSelectedIndexChange, modifier, enabled)`, controlled, -1
      meaning nothing selected. When `items` changes by value, the node clears the items and adds
      them again, then restores the selection.
- [x] `SpinBox(value: Double, onValueChange, modifier, valueRange = 0.0..100.0, step = 1.0,
      decimals = 2, enabled)`, controlled, with Raven's defaults.
- [x] `SpinBox(value: Int, onValueChange, modifier, valueRange: IntRange = 0..100, step = 1,
      enabled)`, with `decimals = 0` and values rounded with `roundToInt()`.
- [x] `codeEditorLanguages(): List<String>` from `Rlanguage_get_count`/`_name`, failing outside the
      composition.
- [x] Harness: selecting a ComboBox entry and setting a SpinBox value, with assertions reading
      them back.
- [x] Tests: an ignored selection or value reverts; an accepted one doesn't flicker; new `items`
      keep the selected index; the Int SpinBox rounds; `codeEditorLanguages()` includes Kotlin
      (fork change 3).

## Resolution

Done on macOS. `./gradlew build allTests` passes: 164 tests, 55 of them in foundation.

- **Composables**: `ComboBox`, and the `Double` and `Int` `SpinBox` overloads, are in
  `raven.compose.foundation` (`Inputs.kt`), declarations over the binding layer as `Slider` is.
  `enabled` uses the shared `Enabled` property. The binding layer gains `ComboBoxSelected` and
  `SpinBoxValue` (controlled values) and `ComboBoxChanged`, `SpinBoxChanged` and
  `IntSpinBoxChanged` (events that mark them).
- **ComboBox items**: the items property clears and adds the items again only when `items`
  changes by value (`Updater.set` compares lists with `equals`). Raven clears the selection with
  the items, so the property puts back the selection the widget showed; it is bound before
  `selectedIndex`, so a new index composed with new items is set after them. A `selectedIndex`
  that is neither -1 nor an index of `items` fails with a clear error, since Raven would show
  nothing for it.
- **SpinBox**: range, step and decimals are bound before the value, which is coerced into
  `valueRange` as `Slider`'s is. Raven's own default range is unbounded (±1e30); the composables
  default to `0.0..100.0` and `0..100`, as the spec says.
- **Int SpinBox, a deviation from [ComboBox and SpinBox](archive/0019-combobox-and-spinbox.md)**:
  that decision has the controlled check compare `Int`s. The `Int` overload instead composes
  `value.toDouble()` into the same `SpinBoxValue` and rounds only the callback's value, so the
  check compares the widget's raw value with the whole number composed. Comparing rounded `Int`s
  would leave a typed 2.5 in the widget, which `decimals = 0` shows as "2" (C's `%.0f` rounds
  half to even) while the app holds 3. Now the fraction is written back as the number the app took.
  Spec section 8's text (values rounded with `roundToInt()`) still holds.
- **`codeEditorLanguages()`** is in `CodeEditor.kt`. It is a plain function, not a composable:
  "inside the composition" is checked as Raven running with this the UI thread (`Ris_ui_thread`),
  as decision 0019 words it, and fails with an `IllegalStateException` naming the function
  otherwise.
- **Harness**: `performSelection(index)` picks a combo box item as a keyboard user would (a click
  opens the list, then Home, Down and Enter, which the open popup sees first);
  `performValueInput(value)` clicks a spin box, types the value and presses Enter;
  `assertSelectedIndex(index)` and `assertValue(value)` read `Rcombo_box_get_selected` and
  `Rspin_box_get_value`. Each fails on a node of another widget type.
- **Tests**:
  - a ComboBox follows the app, shows an accepted selection at once and keeps it after a frame,
    and shows a selection the app sets;
  - a ComboBox whose app ignores the selection reverts;
  - new `items` keep the selected index, with the new texts;
  - a SpinBox follows the app, an accepted value shows at once and stays, and an ignored one
    reverts;
  - a SpinBox keeps to its range;
  - the `Int` SpinBox rounds a typed 2.6 to 3 and writes 3 back, and an ignored value reverts;
  - `codeEditorLanguages()` includes Kotlin (fork change 3), and fails outside the composition.

  With the new events not marking their values, the three revert tests fail; without the
  selection restore, the new-items test fails.
