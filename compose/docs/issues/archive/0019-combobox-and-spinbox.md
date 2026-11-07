---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0014]
assignee: Paul Hawke
---

# ComboBox and SpinBox

## Question

Which composables cover Raven's ComboBox and SpinBox, as the `notepad`, `themes`, `glass` and `overlay` ports need? Compose has no exact equivalents (`ExposedDropdownMenuBox` is Material and heavy), so decide names, parameter shapes (items as a `List<String>` with a selected index? a numeric value with range, step and decimals?), and how both work as controlled inputs.

## Resolution

Facts: Raven's ComboBox holds strings (`Rcombo_box_add_item`/`clear_items`), a selected index (-1 for none), and a change callback that passes the index. SpinBox holds a `double` with range, step and decimals (Raven's default is 2), and a change callback that passes the value. `notepad` lists Raven's languages with `Rlanguage_get_count`/`_get_name`, which apps can't reach.

- **`ComboBox(items: List<String>, selectedIndex: Int, onSelectedIndexChange: (Int) -> Unit, modifier, enabled = true)`** uses ComboBox and takes no children. It is a compose-raven name, because Compose has no plain dropdown and Material's `ExposedDropdownMenuBox` is heavy. It is controlled ([Events and state](0010-events-and-state.md)), and -1 means nothing selected. When `items` changes (compared by value), the node calls `Rcombo_box_clear_items`, then `add_item` for each entry, then sets the selection again. There is no generic `<T>`/`itemText` for now.
- **`SpinBox(value: Double, onValueChange: (Double) -> Unit, modifier, valueRange: ClosedFloatingPointRange<Double> = 0.0..100.0, step: Double = 1.0, decimals: Int = 2, enabled = true)`** uses SpinBox, controlled. It takes Raven's `Double` and defaults. `Slider` stays `Float`, as in Compose. Scrubbing sends each change through `onValueChange`.
- **`SpinBox(value: Int, onValueChange: (Int) -> Unit, modifier, valueRange: IntRange = 0..100, step: Int = 1, enabled = true)`** is an `Int` overload over the same widget. It fixes `decimals = 0`, passes range and step as `Double`, rounds the callback value with `roundToInt()`, and the controlled check compares `Int`s.
- **`codeEditorLanguages(): List<String>`** in `raven.compose.foundation` reads `Rlanguage_get_count`/`Rlanguage_get_name`. It is valid only while Raven runs on the UI thread, which means inside the composition, and throws a clear error otherwise. `notepad` writes `ComboBox(listOf("Plain text") + codeEditorLanguages(), ...)`.
