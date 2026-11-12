---
status: open
---

# ComboBox, SpinBox and codeEditorLanguages

Three controlled inputs and the list of editor languages, for the `notepad`, `themes` and
`overlay` ports (and `glass` in phase 3). Spec:
[8: ComboBox and SpinBox](../spec.md#phase-2-combobox-and-spinbox).

**Depends on:** nothing open.

## Deliverables

- [ ] `ComboBox(items, selectedIndex, onSelectedIndexChange, modifier, enabled)`, controlled, -1
      meaning nothing selected. When `items` changes by value, the node clears the items and adds
      them again, then restores the selection.
- [ ] `SpinBox(value: Double, onValueChange, modifier, valueRange = 0.0..100.0, step = 1.0,
      decimals = 2, enabled)`, controlled, with Raven's defaults.
- [ ] `SpinBox(value: Int, onValueChange, modifier, valueRange: IntRange = 0..100, step = 1,
      enabled)`, with `decimals = 0` and values rounded with `roundToInt()`.
- [ ] `codeEditorLanguages(): List<String>` from `Rlanguage_get_count`/`_name`, failing outside the
      composition.
- [ ] Harness: selecting a ComboBox entry and setting a SpinBox value, with assertions reading
      them back.
- [ ] Tests: an ignored selection or value reverts; an accepted one doesn't flicker; new `items`
      keep the selected index; the Int SpinBox rounds; `codeEditorLanguages()` includes Kotlin
      (fork change 3).
