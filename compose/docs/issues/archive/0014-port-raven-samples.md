---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0011]
assignee: Paul Hawke
---

# Port the Raven samples to Compose

## Question

Which of Raven's own examples (`examples/`: hello, notepad, components, themes, editor, studio, workspace, ...) get compose-raven ports, and in what order? For each, what does it need beyond the v1 component vocabulary? That answer is how the Wider vocabulary fog becomes tickets. Which examples can't be ported yet (for example the viewport ones, which wait on Custom drawing), and where do the ports live in the Gradle build?

## Resolution

- **Waves**, ordered by how far each example goes beyond the [v1 component vocabulary](0011-v1-component-vocabulary.md):
  - **Wave 1 (v1)**: `hello`. Its timer becomes a `LaunchedEffect`.
  - **Wave 2**:
    - `notepad`: menus, HeaderBar, ComboBox.
    - `components`: Border cards, WrapBox, Expander. Its `<Widget>`/`Repeat`/`ForEach` templates become plain composables and loops.
    - `themes`: Theming, ComboBox, SpinBox.
    - `overlay` and `glass`: BackgroundBlur, glass theme, ComboBox, SpinBox, UniformGrid, TreeView.
  - **Wave 3**:
    - `editor`: menus, TreeView, TabView, Viewport, SpinBox.
    - `workspace`: DockArea, FileBrowser, ListView, Island.
    - `music`: lists, Island, many screens.
    - `animations`: Appear transitions, Island, Custom.
  - **Waits on fog**: `effects`, `viewport_opengl` and `viewport_vulkan` wait on Custom drawing. `studio` needs Timeline and NodeGraph, and `browser` needs WebView and ColorPicker.
  - **Not ported**: `memory` demonstrates `Rset_allocator`, which is C-level and out of Compose's reach. `languages` and `bevy_editor` show the other bindings and Bevy, not Raven UIs.
- **Spec scope**: the spec records the waves as a roadmap section. Wave 2's needs become tickets on this map, one per family, and they do **not** block [Assemble the spec](0015-assemble-the-spec.md), so v1 can be handed off while they are worked through. Wave 3 and later stay in the Wider vocabulary fog.
- **Wave-2 tickets**: [Menus and the header bar](0018-menus-and-header-bar.md), [ComboBox and SpinBox](0019-combobox-and-spinbox.md), [Border, WrapBox and Expander](0020-border-wrapbox-expander.md), [Theming](0021-theming.md) (graduated from the fog, including the question of a Material-like layer), and [Glass and BackgroundBlur](0022-glass-and-background-blur.md) (blocked on Theming). UniformGrid and TreeView, which only `glass` needs, stay in the fog.
- **Where ports live**: `compose/samples/<name>`, one executable Gradle module per example (`samples/hello`, `samples/notepad`, ...). The existing `sample` module becomes `samples/xml-translator`. This slightly amends [Kotlin/Native targets and build](0005-kotlin-native-targets-and-build.md) and [XML to Compose translator sample](0013-xml-to-compose-translator.md).
- **Fidelity**: each port is idiomatic, hand-written Compose with the same look and behaviour as the C example (state, composable functions, loops), not pasted translator output. The translator may draft it from the example's XML. The C and Compose versions side by side are part of the point.
