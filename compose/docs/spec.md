# compose-raven specification

**Status: v1 and wave 2 complete.** Every decision below is settled. The first wayfinder map,
[compose-raven spec](issues/0001-compose-raven-spec.md), is closed. The open work is on
[compose-raven wave 3 spec](issues/0023-compose-raven-wave-3-spec.md), and each of its tickets
updates this file when it closes ([Roadmap](#13-roadmap)). The vocabulary is in
[`compose/GLOSSARY.md`](../GLOSSARY.md).

## 1. Overview

compose-raven is a family of Kotlin/Native modules that drives Raven's retained widgets from the
Compose runtime. It works like a native-widgets binding. Each composable node owns exactly one
Raven widget. Raven lays the widgets out and paints them, and Compose only manages the tree. Mosaic
is the model for wiring the Compose runtime to a tree of its own.

Two standing preferences shape every section:

- **Compose conventions and ergonomics.** Names, parameter shapes, defaults and behaviour follow
  Compose. Where Raven differs, compose-raven adapts to Compose, and the
  [Differences from Compose](#12-differences-from-compose) table lists every place where the two
  still differ.
- **Keep it slim.** v1 is the smallest spec that makes a real small tool. Everything else is on
  the [Roadmap](#13-roadmap).

An app looks like this:

```kotlin
fun main() = ravenApplication(title = "Hello", width = 800, height = 600) {
    var count by remember { mutableStateOf(0) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Clicked $count times")
        Button(onClick = { count++ }) { Text("Add") }
    }
}
```

The app never calls Raven directly.

## 2. Modules and build

compose-raven is a standalone Gradle build rooted at `compose/`, with these modules:

| Module | Contents | Depends on |
|---|---|---|
| `raven-native` | The cinterop over `Raven.h` (`raven.cinterop`) and the small public helpers in `raven.native` ([section 3](#3-raven-native)). It contains no Compose code. | libraven |
| `compose-raven-foundation` | Everything Compose: packages `raven.compose.ui` (Applier, node, host, `Modifier`, `Dp`, window entry point) and `raven.compose.foundation` (composables) | `raven-native` with `implementation`, so apps don't see it; `androidx.compose.runtime:runtime` |
| `samples/hello`, `samples/xml-translator` | Executable samples ([section 10](#10-samples)) | `compose-raven-foundation` only |

The two packages already allow a later split into `compose-raven-ui` and
`compose-raven-foundation` without breaking any import.

- **Toolchain**: Kotlin 2.4.21 with `kotlin("multiplatform")` and
  `org.jetbrains.kotlin.plugin.compose`. The dependency is `androidx.compose.runtime:runtime:1.12.1`
  from Google Maven; the JetBrains coordinates only redirect to it. Neither Compose UI nor the
  `org.jetbrains.compose` plugin is used.
- **Targets**: `macosArm64` and `linuxX64`. Each target builds and is tested on its own host, with
  no cross-compilation.
- **Building libraven**: a Gradle task runs CMake on the repo root into
  `compose/build/raven/<target>`. The build is always `Release`, with `RAVEN_BUILD_EXAMPLES`,
  `RAVEN_BUILD_TESTS`, `RAVEN_BUILD_TOOLS` and `RAVEN_BUILD_WEB` off. The cinterop task depends on
  it.
- **Linking**: static. `raven.def` bundles `libraven.a` (`staticLibraries` + `libraryPaths`).
  `linkerOpts` adds `-framework AppKit -framework QuartzCore -framework Metal -framework
  CoreFoundation` on macOS and `-lX11 -ldl -lpthread -lm` on Linux.
- **MoltenVK** is needed at run time only. Raven loads Vulkan dynamically, so nothing is linked.
  On macOS, users install it with `brew install molten-vk` or the Vulkan SDK.

Decided in: [Compose runtime on Kotlin/Native](issues/0002-compose-runtime-on-kotlin-native.md),
[Kotlin/Native targets and build](issues/0005-kotlin-native-targets-and-build.md),
[Layering over cinterop](issues/0006-cinterop-layering.md),
[Port the Raven samples to Compose](issues/0014-port-raven-samples.md).

## 3. raven-native

- **`raven.def`**: `headers = Raven.h`, `compilerOpts = -I<repo>/include`,
  `package = raven.cinterop`. The `headerFilter` keeps only Raven's own headers, without
  `raven_backend.h` and `raven_vulkan.h`.
- **`raven.native`** is a small public layer that holds only what node code needs again and again:
  - **Callback registry**: one `StableRef` per widget, passed as the `user_data` of every callback
    and disposed from `Rwidget_set_destroy_callback`. The `Rid` the destroy callback receives is
    already dead. The registry can report how many references are live, for the tests in
    [section 9](#9-testing).
  - **String conversion** at the C boundary.
  - **UI-thread check**.
- There are no handle value classes and no wrappers per widget. Nodes call `raven.cinterop`
  directly for everything else.
- No Raven type appears in `compose-raven-foundation`'s public API. Raven enums get Kotlin
  counterparts (such as `TextAlign`) only where a composable needs them, and they are mapped
  internally.

Decided in: [Layering over cinterop](issues/0006-cinterop-layering.md).

## 4. Application host

`fun ravenApplication(title: String, width: Int, height: Int, customHeader: Boolean = false, transparent: Boolean = false, undecorated: Boolean = false, content: @Composable RavenApplicationScope.() -> Unit)`
is in `raven.compose.ui`. It blocks, and it must be called from `main()` on the main thread, which
becomes the **UI thread** (AppKit requires this). It returns when the window closes. v1 has one
window. `customHeader = true` creates it with `R_WINDOW_CUSTOM_HEADER`, for a `HeaderBar`
([section 8](#8-v1-component-vocabulary)).
`transparent` uses `R_WINDOW_TRANSPARENT` and `undecorated` uses `R_WINDOW_BORDERLESS`. Inside
the content, `WindowBackdrop(backdrop: Backdrop)` declaratively calls `Rwindow_set_backdrop`.
`Backdrop` is `None`, `Transparent`, `Blur`, `Acrylic` or `Mica`, and a warning is logged when
the window isn't transparent or the system refuses. Growing it into `application { Window { } }` waits for the multi-window fog.

The setup lives in an internal `RavenHost` with the interface `start(content)`, `frame()` and
`close()`. `ravenApplication` is `RavenHost` plus `while (Rwait_frame(-1.0))`. The test harness is
`RavenHost` plus frames it drives itself ([section 9](#9-testing)).

- **Start**: `Rinit`, then create the window and the fixed root ([section 5](#5-node-model-and-applier)),
  then start the composition.
- **Dispatcher**: an internal `RavenDispatcher : CoroutineDispatcher`. `dispatch` calls
  `Rtask_post` with a `StableRef` to the block, and `isDispatchNeeded` is always `true`, so
  Applier work never runs re-entrantly inside a Raven callback. It isn't public and isn't
  `Dispatchers.Main`. After shutdown it drops dispatches instead of calling Raven.
- **Frame clock**: a `BroadcastFrameClock`. Its `onNewAwaiters` calls `Rwake_up()`.
  compose-raven owns the single `Rset_frame_callback` slot outright and calls
  `sendFrame((Rget_time() * 1e9).toLong())` from it. The frame callback runs after tasks and before
  layout, so a recomposition is laid out and painted in the same frame.
- **Recomposer**: `runRecomposeAndApplyChanges()` in a scope of `RavenDispatcher` plus the clock.
- **Snapshot apply pump**: `Snapshot.registerGlobalWriteObserver` sets a flag. If the flag wasn't
  already set, it dispatches one `Snapshot.sendApplyNotifications()`. `Rtask_post` is thread-safe
  and wakes `Rwait_frame`, so a state write from any thread wakes the loop.
- **Shutdown**, when `Rwait_frame` returns false:
  1. `composition.dispose()`. Each node's `onRelease` destroys its widget, and the destroy
     callbacks dispose the `StableRef`s.
  2. Cancel the Recomposer, then its scope.
  3. Unregister the write observer.
  4. `Rshutdown()`. Its last run of the task queue disposes any `StableRef` still in a pending
     dispatch.
- **Errors**: a `CoroutineExceptionHandler` on the scope, together with the C-boundary catch in
  [section 7](#7-events-and-state), records the first throwable and calls `Rquit()`. After the
  normal shutdown, `ravenApplication` rethrows it.
- **Exit**: `RavenApplicationScope.exitApplication()` calls `Rquit()`, which is thread-safe.
  Closing the window also ends the loop.

Decided in: [Frame loop and threading](issues/0008-frame-loop-and-threading.md),
[Testing strategy](issues/0012-testing-strategy.md).

## 5. Node model and Applier

- **Node**: a single internal class `RavenNode` in `raven.compose.ui`. It holds:
  - the widget's `Rid`
  - one `StableRef` to itself
  - the Kotlin event lambdas ([section 7](#7-events-and-state))
  - its folded layout values ([section 6](#6-layout-and-modifiers))
  - a mirror list of its child nodes
  - the kind of children its widget takes: **none**, **single** or **many**. Raven keeps this in
    private flags, so each composable states it.

  Composables emit `ComposeNode<RavenNode, RavenApplier>` with a factory that creates the widget
  (`RavenNode(Rwidget_create(...), ...)`) and `Updater.set` setters.
- **Lifetime**: the factory creates the widget. `Applier.remove` only detaches it
  (`Rwidget_remove_child`), so `movableContentOf` can insert the same node again somewhere else.
  `RavenNode` implements `ComposeNodeLifecycleCallback`: `onRelease` calls `Rwidget_destroy`, and
  the destroy callback disposes the `StableRef`. v1 has no node reuse (no `ReusableComposeNode`),
  so `onReuse` and `onDeactivate` do nothing.
- **Root**: the host creates an `R_WIDGET_OVERLAY`, sets it once with `Rwindow_set_content`, and
  wraps it as the Applier's root node. It stacks its children like `Box`, and the Applier never
  replaces it.
- **Inserts** are bottom-up, through `Rwidget_insert_child(parent, child, index)`. The Applier
  rejects an insert into a node that takes no children, in Kotlin, with a clear error.
- **Properties**: each `Updater.set` calls the cinterop setter directly. There is no batching in
  `onEndChanges`, because Raven lays out lazily later in the same frame and setters don't fire
  change callbacks.
- **Moves** use `Rwidget_move_child(parent, from, to)` ([section 11](#11-required-raven-changes)).
  The Applier calls it once for each of Compose's `count` items, with `to - count` when
  `from < to`.
- **Content of single-child widgets**: a composable over a single-child widget (Button, Border,
  Expander, ...) emits two nodes: the widget, plus an internal content node holding the
  composable's content. That node is a Row for buttons and a Box for the others. So content may
  emit any number of children.
- **Single-child parents** (Border, SizeBox, Button, Expander, BackgroundBlur) destroy their
  existing child when a second one is inserted. Before inserting into one, the Applier detaches
  the Raven child currently attached. That child stays alive in the mirror list. Removing a node
  that is no longer attached skips the Raven call.

Decided in: [How Mosaic is built](issues/0003-how-mosaic-is-built.md),
[Raven widget tree API for an Applier](issues/0004-raven-widget-tree-api.md),
[Node model and Applier](issues/0007-node-model-and-applier.md).

## 6. Layout and Modifiers

- **`Modifier`**: compose-raven has its own immutable chain of elements in `raven.compose.ui`.
  Compose UI's `Modifier` would bring in Compose UI. Every composable takes
  `modifier: Modifier = Modifier`. The node folds the chain into flat layout values, compares them
  with the previous set, and calls only the `Rslot_*` and `Rwidget_set_*size` setters that changed.
  Settings that belong to the widget itself, such as a panel's spacing or a label's text, are
  composable parameters.
- **`Dp`**: a value class in `raven.compose.ui.unit` (`16.dp`). 1.dp is one Raven logical unit
  and is passed through unchanged.
- **Containers**: `Column` uses VBox, `Row` uses HBox and `Box` uses Overlay.
- **Defaults are Compose's**: each node sets its slot to start/top alignment when it is created,
  overriding Raven's `FILL`. Stretching is opt-in.
- **v1 elements**:

| Element | Scope | Raven |
|---|---|---|
| `padding(all)`, `padding(horizontal, vertical)`, `padding(start, top, end, bottom)` | any | `Rslot_set_padding`. Several paddings add up. |
| `size`, `width`, `height` | any | `Rwidget_set_size` |
| `sizeIn`, `widthIn`, `heightIn` | any | `Rwidget_set_min_size` / `Rwidget_set_max_size` |
| `fillMaxWidth()`, `fillMaxHeight()`, `fillMaxSize()` (no fraction) | any | Cross axis, and both axes in `Box`: `FILL` alignment. Main axis of Row or Column: like `weight(1f)` |
| `weight(Float)` | `RowScope`, `ColumnScope` | `Rslot_set_fill` |
| `align(Alignment)` / `align(Alignment.Horizontal)` / `align(Alignment.Vertical)` | `BoxScope` / `ColumnScope` / `RowScope` | `Rslot_set_halign` / `_valign` |
| `testTag(String)` | any | stored on the node only, with no Raven call ([section 9](#9-testing)) |
| `material(Material)` | any | `Rwidget_set_material` on the composable's own (outer) widget; `Material.Default` clears it ([section 8](#8-v1-component-vocabulary)) |

  For size and alignment, the last element in the chain wins. Padding is always outside the widget,
  because v1 has no drawing modifiers.
- **Arrangement and container alignment** keep Compose's parameter shape, for example
  `Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start)`.
  v1's `Arrangement` offers only `Top`/`Start` and `spacedBy(dp)`, which uses `Rpanel_set_spacing`.
  The container's alignment sets each child's default cross-axis alignment, and `Modifier.align`
  overrides it.
- `Alignment.Start` and `Alignment.End` map to `LEFT` and `RIGHT`. v1 has no right-to-left
  support.

Decided in: [Layout and Modifiers](issues/0009-layout-and-modifiers.md).

## 7. Events and state

- **Wiring**: the node factory registers one shared `staticCFunction` per event kind the
  composable supports, once, with the node's `StableRef` as `user_data`. The lambdas live in
  mutable fields on `RavenNode`. `Updater.set` only replaces the field, and a null lambda makes
  the callback do nothing. Lambdas run synchronously on the UI thread. Any state they write is
  recomposed later through `RavenDispatcher`.
- **Controlled inputs**, as in Compose: a composable shows the value the app composed. Raven
  changes a widget's own value before it calls back, so each input node remembers the value it
  last composed, and a user change marks the node as needing a check. In the frame callback, after
  the clock ticks, compose-raven checks the marked nodes, but only when `recomposer.hasPendingWork`
  is false. Each marked node sets its widget back to the composed value if the two differ:
  - An accepted change already matches, so nothing happens and nothing flickers.
  - A rejected change reverts once the composition has settled.

  Raven's setters don't fire change callbacks, so nothing loops.
- **Text**: `TextField(value: String, onValueChange)`. Text is written back only when it differs
  from what the widget shows, so typing never moves the caret.
- **Errors**: a Kotlin exception must not cross a `staticCFunction`. Each shared callback catches
  it and passes it to the host's error path ([section 4](#4-application-host)).
- **v1 events** are only the callbacks that belong to each widget, listed in
  [section 8](#8-v1-component-vocabulary).

Decided in: [Events and state](issues/0010-events-and-state.md).

## 8. v1 component vocabulary

v1 covers what the translator sample and a basic settings form need. All composables take
`modifier: Modifier = Modifier`.

| Composable | Raven widget | Notes |
|---|---|---|
| `Column`, `Row`, `Box` | VBox, HBox, Overlay | [section 6](#6-layout-and-modifiers) |
| `Text(text, modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified, textAlign, softWrap)` | Label | `color` uses `Rlabel_set_color`. `fontSize` (`14.sp`, 1.sp = one Raven text-size unit) uses `Rlabel_set_text_size` |
| `Button(onClick, modifier, enabled) { content }` | Button, `R_BUTTON_PRIMARY` | the content goes in an internal Row (HBox, vertically centred), as Compose's `RowScope` does; never `Rbutton_set_text` |
| `OutlinedButton(...) { }`, `TextButton(...) { }` | Button, `R_BUTTON_DEFAULT` / `R_BUTTON_FLAT` | as `Button` |
| `Checkbox(checked, onCheckedChange)` | Checkbox, `R_CHECKBOX_CHECK` | controlled. Raven's own checkbox text isn't used; the label is a `Text` in a `Row` |
| `Switch(checked, onCheckedChange)` | Checkbox, `R_CHECKBOX_SWITCH` | as `Checkbox` |
| `RadioButton(selected, onClick)` | Checkbox, `R_CHECKBOX_RADIO` | the controlled check makes the app's state override Raven's sibling unchecking |
| `TextField(value, onValueChange, modifier, placeholder: String, singleLine = true)` | TextInput | single line only |
| `Slider(value, onValueChange, valueRange, steps)` | Slider | controlled |
| `LinearProgressIndicator(progress)` | ProgressBar | |
| `HorizontalDivider()`, `VerticalDivider()` | Separator | |
| `Spacer(modifier)` | Spacer | no children |
| `VerticalScroll { }`, `HorizontalScroll { }` | ScrollBox | a scrolling Column or Row |
| `CodeEditor(value, onValueChange, modifier, language: String?, readOnly, lineNumbers)` | TextEditor | controlled. Its change callback carries no text, so the node reads `Rtext_editor_get_text`. `language` is a name for `Rtext_editor_set_language` |
| `HorizontalSplitter { }`, `VerticalSplitter { }` | Splitter | a child's `Modifier.weight` is its relative pane size |

### Wave 2: menus and the header bar

Added for the `notepad` port:

| Composable | Raven widget | Notes |
|---|---|---|
| `MenuBar { }` | MenuBar | a layout composable; `MenuBarScope` allows only `Menu` |
| `Menu(text) { }` | Menu | `MenuScope` allows `Item`, `CheckboxItem`, `Separator` and sub-`Menu` |
| `Item(text, onClick, shortcut: KeyShortcut? = null, enabled = true)` | MenuItem | no children |
| `CheckboxItem(text, checked, onCheckedChange, shortcut, enabled)` | MenuItem, checkable | controlled |
| `Separator()` (in `MenuScope`) | Separator | to verify first: Raven treats an inserted separator as a menu separator |
| `HeaderBar(modifier, title: String? = null, buttons: Set<WindowButton> = WindowButton.all()) { }` | HeaderBar | needs `ravenApplication(customHeader = true)`, otherwise a warning is logged; in `HeaderBarScope`, `Modifier.align(Alignment.End)` places a child on the right |

`KeyShortcut(key: Key, ctrl, shift, alt, meta)` uses `Rmenu_item_set_shortcut`, where `meta` is
Super (Cmd on macOS). `Key` mirrors `RKey`: letters, digits, F1 to F12, and the named keys.

### Wave 2: ComboBox and SpinBox

Added for the `notepad`, `themes`, `glass` and `overlay` ports. All three are controlled.

| Composable | Raven widget | Notes |
|---|---|---|
| `ComboBox(items: List<String>, selectedIndex: Int, onSelectedIndexChange: (Int) -> Unit, modifier, enabled = true)` | ComboBox | -1 means nothing selected. When `items` changes (by value), the node calls `clear_items` and `add_item` again |
| `SpinBox(value: Double, onValueChange: (Double) -> Unit, modifier, valueRange = 0.0..100.0, step = 1.0, decimals = 2, enabled = true)` | SpinBox | Raven's `Double` and defaults |
| `SpinBox(value: Int, onValueChange: (Int) -> Unit, modifier, valueRange: IntRange = 0..100, step = 1, enabled = true)` | SpinBox | `decimals = 0`, values rounded with `roundToInt()` |

`codeEditorLanguages(): List<String>` lists Raven's editor languages (`Rlanguage_get_count`/`_name`).
It is valid only inside the composition.

### Wave 2: Surface, FlowRow and Expander

Added for the `components` port:

| Composable | Raven widget | Notes |
|---|---|---|
| `Surface(modifier, shape: Shape = RectangleShape, color: Color = Color.Unspecified, border: BorderStroke? = null, shadowElevation: Dp = 0.dp) { }` | Border | `Unspecified` means the theme's panel colour. Inner padding is the content's own `Modifier.padding`. `shadowElevation` maps to blur = elevation and y offset = elevation / 2. The content goes in an internal Box |
| `FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(x)) { }` | WrapBox | `x` is both the gap between items and the gap between lines |
| `Expander(title: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier) { }` | Expander | controlled; the content goes in an internal Box |

Graphics types live in `raven.compose.ui.graphics`:

- **`Color`** is a value class: `Color(0xFF3B82F6)`, `Color(red, green, blue, alpha)`, and the
  constants `Black`, `White`, `Gray`, `Red`, `Green`, `Blue`, `Yellow`, `Transparent` and
  `Unspecified`.
- **`Shape`** is sealed: `RectangleShape`, `RoundedCornerShape(all)`,
  `RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)`, and `CircleShape`, which is a
  large radius that Raven clamps.
- **`BorderStroke(width: Dp, color: Color)`** uses `Rborder_set_outline`.

### Wave 2: theming

Added for the `themes` port:

- **`RavenTheme(look: Look? = null, dark: Boolean? = null, theme: String? = null) { }`**:
  - `look` and `dark` become one `Rlook_set`. The look is app-wide, so only the outermost
    `RavenTheme` may set it. A nested one that does logs an error.
  - `theme` names a loaded theme. The outermost `RavenTheme` uses `Rtheme_use`. A nested one gives
    its subtree its own theme through an internal Box and `Rwidget_set_theme`.
  - `Look` is one of `Raven`, `Glass`, `HighContrast`, `Windows`, `Macos`, `Gnome`, `Native` or
    `MacosGlass`.
- **`loadTheme(path): String`** and **`loadThemeFromString(xml): String`** load theme files and
  return the name for `theme =`. They are valid only while Raven runs.
- **`RavenTheme.colors`** is a CompositionLocal with one `Color` per Raven colour role, read from
  the app theme with `Rtheme_get_color`. It doesn't see subtree themes.
- **Colour parameters** follow Material: `XxxDefaults.colors(...)` returns `XxxColors`, passed as
  `colors =`. Every default is `Color.Unspecified`, meaning "keep the Raven theme's value", so
  only the colours given call `Rwidget_set_color`.

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
  | `Surface` | `contentColor` | `TEXT`, passed down to the children |

  **Hover and pressed shades:** the implementation first checks whether Raven derives them from
  the base colour. If it doesn't, compose-raven sets slightly lighter and darker shades.
- **Shape and padding:** buttons take `shape` and `contentPadding: PaddingValues`, and `TextField`
  takes `shape`.
  - `shape` uses `R_METRIC_CORNER_RADIUS`, which is a single radius. A non-uniform shape uses
    `topStart` and logs a warning.
  - `contentPadding` uses `R_METRIC_PADDING_X`/`_Y` (start, top). Non-symmetric values log a
    warning.

### Wave 2: glass and BackgroundBlur

Added for the `overlay` and `glass` ports:

- **`Modifier.material(Material)`** gives any widget a material of its own: `Default`, `Solid`,
  `Glass`, `FrostedGlass` or `TintedGlass`. On `Surface`, `color` then tints the glass:
  `Surface(Modifier.material(Material.FrostedGlass), color = tint)`.
- **`RavenTheme(..., material: Material? = null)`** sets the material app-wide with
  `Rtheme_set_material`. Only the outermost `RavenTheme` may set it. Controls get glass from the
  Glass and macOS Glass looks.
- **`BackgroundBlur(modifier, style: BlurStyle = BlurStyle.Frosted, shape: Shape = RectangleShape, tint: Color = Color.Unspecified, radius: Dp = Dp.Unspecified, saturation: Float? = null, grain: Float? = null, shadowElevation: Dp = 0.dp) { }`**:
  - It is a container over BackgroundBlur, and its content goes in an internal Box.
  - `BlurStyle` is `Plain`, `Frosted`, `Glass` or `Native`; with `Native`, the OS blurs the
    desktop.
  - `radius = Unspecified` uses the theme's blur radius.
  - Use `Modifier.material(FrostedGlass)` for an ordinary glass panel, and `BackgroundBlur` for
    `Native`, `Plain`, or control over the blur.
- The theme's glass metrics (frost, refraction, rim, ...) are set in theme files only.

Decided in: [v1 component vocabulary](issues/0011-v1-component-vocabulary.md),
[Menus and the header bar](issues/0018-menus-and-header-bar.md),
[ComboBox and SpinBox](issues/0019-combobox-and-spinbox.md),
[Border, WrapBox and Expander](issues/0020-border-wrapbox-expander.md),
[Theming](issues/0021-theming.md),
[Glass and BackgroundBlur](issues/0022-glass-and-background-blur.md).

## 9. Testing

- **The harness** is internal, in `compose-raven-foundation`'s test source set, and tests
  compose-raven itself. It is built on `RavenHost` ([section 4](#4-application-host)) and runs
  headless: `Rinit_ex` with `R_BACKEND_NULL`, `headless = R_TRUE`, `worker_threads = -1`, and a
  fixed `Rset_animation_time_step`. `waitForIdle()` keeps calling `Rdraw_frame()` until no
  recomposition, snapshot apply or controlled check is pending.
- **API**, a small part of `ComposeUiTest`:
  ```kotlin
  runRavenTest {
      setContent { Counter() }
      onNodeWithText("Add").performClick()
      onNodeWithText("Count: 1").assertExists()
  }
  ```
  - `onNodeWithText` and `onNodeWithTag` search compose-raven's node tree.
  - `performClick()` injects a mouse press and release at the centre of `Rwidget_get_rect`.
  - `performTextInput` clicks the field to focus it, then calls `Rwindow_inject_text`.
  - Assertions include `assertExists` and `assertIsChecked`, plus widget values read through
    `raven-native` getters.
- **Leak checks after every test**: after `close()`/`Rshutdown`, `Rmemory_get_stats` must report
  zero blocks held, and the `raven.native` registry must report zero live `StableRef`s.
- **Isolation**: Raven starts fresh for every test. The implementation first verifies that
  `Rinit`/`Rshutdown` can be repeated in one process ([section 11](#11-required-raven-changes)).
- **Pure logic** gets plain Kotlin unit tests, with no Raven: Modifier folding, move-index
  adjustment and mirror-list bookkeeping. There are no screenshot tests, because the null renderer
  draws nothing.
- **CI**: a compose job in `.github/workflows/build.yml` on a macOS and a Linux runner, running
  `./gradlew -p compose allTests`.

Decided in: [Testing strategy](issues/0012-testing-strategy.md).

## 10. Samples

Each sample is its own executable module under `compose/samples/`. A port is idiomatic,
hand-written Compose with the same look and behaviour as the C example it ports.

### `samples/hello`

A port of Raven's `examples/hello`: a label, buttons, and a click counter. Its timer becomes a
`LaunchedEffect`.

### `samples/xml-translator`

This sample translates Raven XML into compose-raven source.

- **UI**: a `HorizontalSplitter`.
  - The left pane is `CodeEditor(xml, language = "XML")`, starting with the example from
    `docs/XML.md`.
  - The right pane is `CodeEditor(code, readOnly = true, language = "Kotlin")`, using Raven's
    built-in Kotlin language ([section 11](#11-required-raven-changes)).
  - A `Text` below shows the warnings.
  - Translation reruns on every change.
- **Parsing**: Raven's own parser, through a cinterop private to the sample. `ravenxml.def` uses
  `package = raven.sample.xml.cinterop` and a `headerFilter` of `raven_xml.h`, plus
  `Rinit_ex`/`Rshutdown`/`RInitDesc` for tests. It has no `staticLibraries`, because `libraven.a`
  already arrives through `raven-native`. A single adapter, `parseRavenXml(text)`, calls
  `Rxml_document_parse` (UI thread, Raven running) and copies the result into a Kotlin
  `XmlElement(tag, attributes, text, line, children)` tree, keeping comments. It then destroys the
  document.
- **Translator**: `translate(xml: String): Translation(code, warnings)`, working on the Kotlin
  tree. Names are matched case-insensitively, through an alias table taken from `docs/XML.md`.
- **Mapping** (exactly the [v1 vocabulary](#8-v1-component-vocabulary)):

  | XML | Compose |
  |---|---|
  | `Window` | `fun main() = ravenApplication(...)` |
  | `VBox`, `HBox`, `Overlay` | `Column`, `Row`, `Box` |
  | `ScrollBox` | `VerticalScroll` / `HorizontalScroll` |
  | `Splitter` | `HorizontalSplitter` / `VerticalSplitter` |
  | `Label` | `Text` |
  | `Button` by `Style` | `Button` / `OutlinedButton` / `TextButton`, with `{ Text(...) }` |
  | `Checkbox` by `Style` | `Checkbox` / `Switch` / `RadioButton`, each in a `Row` with a `Text` |
  | `TextInput` | `TextField` |
  | `TextEditor` | `CodeEditor` |
  | `Slider` | `Slider` |
  | `ProgressBar` | `LinearProgressIndicator` |
  | `Separator` | `HorizontalDivider` / `VerticalDivider` |
  | `Spacer` | `Spacer` |
  | `Slot.*`, `Width`/`Height`, `Min*`/`Max*` | Modifiers |
  | `Spacing` | `Arrangement.spacedBy` |
- **Faithful output**: wherever the XML relies on Raven's `Fill` default, the output adds
  explicit `fillMax*` modifiers.
- **State and actions**: each input gets a hoisted
  `var <name> by remember { mutableStateOf(<initial>) }` and is wired as a controlled input. The
  name comes from `Name`, or is generated (`checkbox1`). Actions become stub lambdas, for example
  `onClick = { /* file.save */ }`.
- **Templates**:
  - Each `<Widget Name="Card" Title="Untitled" Wide="false">` becomes a top-level composable,
    `@Composable fun Card(modifier: Modifier = Modifier, title: String = "Untitled", wide: Boolean = false, content: @Composable () -> Unit = {})`,
    emitted before `TranslatedUi()`.
    - Parameters are renamed to lowerCamelCase.
    - Types come from the default value: `Boolean`, `Float` (a number with a dot), `Int`, or
      `String`.
    - Inputs get `remember` state inside the function.
    - A `<Widgets>` root produces only the functions.
  - `<Children/>` becomes the trailing `content` slot, and `<Children Name="x"/>` becomes a slot
    `x: @Composable () -> Unit = {}`. At the use site, children with `Into="x"` go to `x = { }`.
  - Layout attributes at the use site become the call's `modifier`, which the function passes to
    its root. Any other attribute that would pass through becomes a warning.
  - The whole `{...}` expression language is translated into Kotlin, with the type taken from the
    target attribute: `Width="{Wide ? 452 : 220}"` becomes
    `Modifier.width((if (wide) 452 else 220).dp)`. Interpolated text becomes string templates.
    `{Instance}` becomes a comment.
  - `If`/`<If>` becomes `if`, `<Repeat>` becomes `repeat(n) { i -> }`, and `<ForEach>` becomes
    `listOf(...).forEachIndexed { n, x -> }`.
  - `<Include>` stays a TODO plus a warning.
  - The implementation first verifies that `Rxml_document_parse` registers no declarations
    globally. If it does, `parseRavenXml` unregisters each one with `Rxml_unregister_widget`.
- **Unsupported input** is never refused:
  - An element with no composable, `<Include>`, or an expression that fails to parse becomes
    `// TODO(raven-xml): ...` in its place, plus a warning with its line and column.
  - An attribute with no equivalent becomes a warning plus a trailing comment.
- **Output**: one compilable file, with 4-space indents. It holds the imports, then
  `@Composable fun TranslatedUi()` with the hoisted state at the top, then `main()` when the root
  is `<Window>`.
- **Tests**: most tests build `XmlElement` trees and check the generated code, as plain unit
  tests. A few headless smoke tests cover `parseRavenXml`.

Decided in: [XML to Compose translator sample](issues/0013-xml-to-compose-translator.md),
[Templates in the XML translator](issues/0016-xml-translator-templates.md),
[Port the Raven samples to Compose](issues/0014-port-raven-samples.md).

## 11. Required Raven changes

These changes go into the `psh/Raven` fork and are offered upstream later.

1. **`Rwidget_move_child(Rid parent, int from, int to)`** (required): it moves one child, and
   `to` is counted after the child has been taken out, as in `Rwidget_insert_child`. It keeps
   focus, hover and capture, doesn't replay the appear transition, invalidates layout, and treats
   an index out of range as an error.
2. **Repeated `Rinit`/`Rshutdown` in one process** (only if needed): Raven resets its global
   context, but none of its own tests starts it twice. The implementation verifies this first. If
   it fails, the fork fixes it.
3. **A built-in Kotlin language** (required), next to Java in `src/widgets/syntax.c`:
   - `name = "Kotlin"`, `extensions = "kt;kts"`.
   - The keywords are Prism.js's Kotlin list: `abstract actual annotation as break by catch class
     companion const constructor continue crossinline data do dynamic else enum expect external
     final finally for fun get if import in infix init inline inner interface internal is lateinit
     noinline object open operator out override package private protected public reified return
     sealed set super suspend tailrec this throw to try typealias val var vararg when where
     while`.
   - `constants = "true false null"`.
   - The types are `Int Long Short Byte Float Double Boolean Char String Unit Any Nothing Array
     List MutableList Map MutableMap Set MutableSet Sequence Pair Triple`.
   - Comments are `//` and `/* */`, and strings use `"` and `'`, `"""` for multiline, and `@`
     for annotations.
   - Flags: `R_LANGUAGE_NESTED_COMMENTS | R_LANGUAGE_CHAR_LITERALS`.
   - Known limits: string templates aren't highlighted, labels look like annotations,
     trailing-lambda calls aren't marked as functions, and a keyword right after a `.` may be
     highlighted.

Decided in: [Node model and Applier](issues/0007-node-model-and-applier.md),
[Testing strategy](issues/0012-testing-strategy.md),
[Kotlin highlighting in CodeEditor](issues/0017-kotlin-highlighting.md).

## 12. Differences from Compose

| compose-raven | Compose | Section |
|---|---|---|
| `fillMaxWidth`/`fillMaxHeight` on a Row's or Column's main axis behave like `weight(1f)` | takes all the remaining space | [6](#6-layout-and-modifiers) |
| `fillMax*` has no fraction argument | `fillMaxWidth(0.5f)` | [6](#6-layout-and-modifiers) |
| `Arrangement` offers only `Top`/`Start` and `spacedBy` | Center, End, SpaceBetween, ... | [6](#6-layout-and-modifiers) |
| `padding` is always outside the widget (no drawing modifiers) | position in the chain decides | [6](#6-layout-and-modifiers) |
| No `Layout {}`, no right-to-left (`Start`/`End` are left/right) | custom layouts, `LayoutDirection` | [6](#6-layout-and-modifiers) |
| `VerticalScroll {}` / `HorizontalScroll {}` containers | `Modifier.verticalScroll(rememberScrollState())` | [8](#8-v1-component-vocabulary) |
| `TextField` takes a `String` placeholder, single line only | a placeholder slot, multi-line | [8](#8-v1-component-vocabulary) |
| No `TextFieldValue`; if the app rewrites the text, the caret jumps to the end | selection kept in `TextFieldValue` | [7](#7-events-and-state) |
| A rejected input change can show for a frame before it reverts | never shown | [7](#7-events-and-state) |
| Plain `Button` is Raven's primary style; `OutlinedButton` is the default style | Material's filled and outlined buttons | [8](#8-v1-component-vocabulary) |
| `CodeEditor`, `HorizontalSplitter`, `VerticalSplitter`, `ComboBox`, `SpinBox` | no counterpart in Compose Foundation | [8](#8-v1-component-vocabulary) |
| `SpinBox` shows 2 decimals unless told otherwise (Raven's default) | none | [8](#8-v1-component-vocabulary) |
| `Surface` container for background, border, corners and shadow | `Modifier.background` / `.border` / `.clip` / `.shadow` | [8](#8-v1-component-vocabulary) |
| `shadowElevation` maps to blur and offset as an approximation | Material elevation | [8](#8-v1-component-vocabulary) |
| `Shape` is rounded rectangles only, and Start/End mean left/right | any `Shape`, RTL-aware | [8](#8-v1-component-vocabulary) |
| `FlowRow` has one spacing for both items and lines, and no `verticalArrangement` | separate arrangements | [8](#8-v1-component-vocabulary) |
| `Expander` | no counterpart | [8](#8-v1-component-vocabulary) |
| Colour defaults are `Unspecified` (the Raven theme), and there is no `disabledContainerColor` | Material defaults read its theme | [8](#8-v1-component-vocabulary) |
| `shape` is one corner radius, and `contentPadding` is symmetric | any shape and padding | [8](#8-v1-component-vocabulary) |
| The look can only be set by the outermost `RavenTheme`, and `RavenTheme.colors` shows only the app theme | nested themes are fully independent | [8](#8-v1-component-vocabulary) |
| No style classes; theme rules by class don't apply | no such concept | [8](#8-v1-component-vocabulary) |
| No generic input (`Modifier.clickable`, key and pointer) and no focus control | available | [7](#7-events-and-state) |
| A single window through `ravenApplication` | `application { Window { } }` | [4](#4-application-host) |
| `MenuBar` is a layout composable drawn in the window | a window-level call, the system menu bar on macOS | [8](#8-v1-component-vocabulary) |
| Shortcuts have no automatic Ctrl-to-Cmd; apps write `meta = true` for macOS | the same in Compose Desktop, but its menus are native | [8](#8-v1-component-vocabulary) |
| `customHeader = true` keeps the OS's resizing and window behaviour; `undecorated = true` removes all decoration, as in Compose Desktop | only `undecorated` | [4](#4-application-host) |
| `Modifier.material(...)` gives any widget a glass or solid surface | no counterpart | [6](#6-layout-and-modifiers) |
| `BackgroundBlur` blurs what is behind it (backdrop blur) | `Modifier.blur` blurs the element itself | [8](#8-v1-component-vocabulary) |
| The backdrop is set with the `WindowBackdrop(...)` composable | no counterpart | [4](#4-application-host) |

## 13. Roadmap

**Sample ports, in waves**:

- **Wave 1 (v1)**: `hello`.
- **Wave 2**: `notepad`, `components`, `themes`, `overlay`, `glass`.
- **Wave 3**: `editor`, `workspace`, `music`, `animations`.
- **Waiting on fog**: `effects`, `viewport_opengl` and `viewport_vulkan` (custom drawing), plus
  `studio` and `browser` (specialist widgets).
- **Not ported**: `memory` (C-level allocator), `languages` and `bevy_editor` (other bindings).

**Open tickets**: on [compose-raven wave 3 spec](issues/0023-compose-raven-wave-3-spec.md).

- [Multiple windows, dialogs and popups](issues/0024-multiple-windows.md)
- [Generic input and focus](issues/0025-generic-input-and-focus.md)
- [Accessibility semantics](issues/0026-accessibility-semantics.md)
- [Fonts and text styling](issues/0027-fonts-and-text-styling.md)
- [Custom drawing and custom layout](issues/0028-custom-drawing-and-layout.md)
- [Public test module for apps](issues/0029-app-testing-module.md)
- [XML translator as a tool](issues/0030-translator-as-a-tool.md)
- [Packaging and publishing](issues/0031-packaging-and-publishing.md)
- [Windows (mingwX64) target](issues/0032-windows-target.md)
- [Lists and trees](issues/0033-lists-and-trees.md)
- [Tabs, Switcher and grids](issues/0034-tabs-switcher-and-grids.md)
- [Image and resources](issues/0035-image-and-resources.md)
- [Island and appear transitions](issues/0036-island-and-appear-transitions.md)
- [DockArea and FileBrowser](issues/0037-docking-and-file-browser.md), after multiple windows
- [Toolbar](issues/0038-toolbar.md), after accessibility

**Not yet specified**: the specialist widgets (Timeline, NodeGraph, ColorPicker, WebView,
BlockEditor) for `studio` and `browser`.

## 14. Out of scope

- **JVM** and the `raven-gui` Java binding: compose-raven is Kotlin/Native only.
- **Source compatibility with Compose Foundation or Material**: names and shapes follow Compose,
  but code written against androidx foundation or material isn't guaranteed to compile against
  compose-raven.
- **Mixing Raven XML documents with composables at run time**: composables build the whole tree.
  Translating XML into Compose source is in scope ([section 10](#10-samples)).
- **A Material-like module**: Raven's looks already imitate each platform.
- **Cross-compilation**: each target builds on its own host.
