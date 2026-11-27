# Raven API coverage

How much of Raven's C API compose-raven binds, what it leaves out on purpose, and the gaps an app
would notice, each with its ticket or its place on the [roadmap](roadmap.md). It began as a review
on 2026-11-14 at `fdfca11`; this file replaces it and is kept in step with the work.

**Keeping it in step**: when a ticket that binds Raven API closes, mark its row here in the same
change, and rerun [`tools/api-coverage.sh`](../tools/api-coverage.sh) to refresh the numbers and
the table. A gap found later gets a row and a ticket, or a line on the roadmap.

*Last updated 2026-11-26, after merging upstream's `main` into compose-raven (`a0eaab62`).*

## Summary

| | 2026-11-14 (`fdfca11`) | Now |
|---|---:|---:|
| Functions used | 370 of 1,157 (31%) | 480 of 1,701 (28%) |
| Widget types bound | 36 of 51 | 37 of 58 |

compose-raven covers everything the ported samples need, but not all of Raven. About 350 of the
functions it doesn't call are deliberately unnecessary in a Compose binding. Of the everyday
features the review found missing, all seven are now bound. Raven grew 65 functions on the fork,
most of them in the UI driver (`raven_driver.h`) and the MCP library (`raven_mcp.h`), which
compose-raven binds almost whole; the map widget was added and bound too.

Merging upstream's `main` (2026-11-26) added 478 functions and 6 widget types that compose-raven
doesn't bind yet, so the percentage fell while what is bound stayed the same. Most of them are new
widgets (MapView, Chart, PdfView, Carousel, SystemWebView, DiffView), the C testing API
(`raven_testing.h`), Raven Script (`raven_script.h`), and the services of mobile devices
(`raven_device.h`, `raven_host.h`).

## Coverage by header

The raw percentages understate coverage: Compose holds state itself, so it rarely needs Raven's
getters, and whole headers (XML, host embedding, Emscripten) have no place in a Compose binding.

| Header | Used | Declared | Coverage | Main reason for the rest |
|---|---:|---:|---:|---|
| `raven_controls.h` | 100 | 557 | 18% | Table, TileView, Plot, Chart, MapView, PdfView, Carousel, SystemWebView, Date/TimePicker, Rating, Pagination, Breadcrumb, Toast unbound; getters |
| `raven_tools.h` | 8 | 231 | 3% | NodeGraph, Timeline, BlockEditor, DiffView unbound (phase 5 fog) |
| `raven_window.h` | 46 | 108 | 43% | drag and drop inside the app, window themes, shortcuts, the inspector, mobile insets and keyboard, getters |
| `raven_text_editor.h` | 24 | 91 | 26% | undo/redo availability, selection, zoom, token colours, find bar, folding, spell checking |
| `raven_core.h` | 40 | 85 | 47% | allocator and memory, async tasks, vsync, actions with parameters |
| `raven_widget.h` | 54 | 81 | 67% | getters, style classes, free-form slots, drop targets on widgets |
| `raven_xml.h` | 4 | 64 | 6% | the XML loader, replaced by the composition |
| `raven_layout.h` | 34 | 61 | 56% | Switcher, SizeBox, Spacer (Compose idioms), layout direction |
| `raven_shell.h` | 23 | 60 | 38% | tray, taskbar, parts of the header bar and dock area |
| `raven_dialogs.h` | 26 | 47 | 55% | popups, system notifications |
| `raven_resources.h` | 18 | 36 | 50% | SVG, async and URL textures, animated images, text measuring |
| `raven_locale.h` | 0 | 33 | 0% | translation, locales, dates and time zones |
| `raven_testing.h` | 0 | 30 | 0% | C UI tests (the Kotlin harness replaces them) |
| `raven_style.h` | 17 | 29 | 59% | per-style theme overrides, wallpaper |
| `raven_accessibility.h` | 18 | 24 | 75% | colour filters, getters |
| `raven_touch.h` | 0 | 23 | 0% | touch, pen and gestures |
| `raven_draw.h` | 17 | 22 | 77% | springs |
| `raven_host.h` | 0 | 16 | 0% | host embedding and device services for embedders (not needed) |
| `raven_device.h` | 0 | 15 | 0% | permissions, pickers, camera, haptics, sharing (mobile) |
| `raven_driver.h` | 13 | 14 | 93% | |
| `raven_shader.h` | 6 | 14 | 43% | getters, effect lookup |
| `raven_vulkan.h` | 11 | 14 | 79% | getters |
| `raven_mcp.h` | 13 | 13 | 100% | |
| `raven_script.h` | 0 | 12 | 0% | Raven Script, for XML documents (not needed) |
| `raven_viewport.h` | 8 | 9 | 89% | |
| `raven_emscripten.h` | 0 | 8 | 0% | web build (not needed) |
| `raven_backend.h` | 0 | 3 | 0% | backend registration (not needed) |
| `raven_android.h` | 0 | 1 | 0% | Android app glue (not needed) |
| **Total** | **480** | **1,701** | **28%** | |

## Not needed by design

About 350 functions have no use in a Compose binding, and leaving them unbound is correct:

- **The XML loader** (64): the composition replaces the XML. Named actions are bound only as far as
  MCP needs them ([Raven's actions as tools](issues/archive/0177-ravens-actions-as-tools.md));
  lambdas replace `Raction_bind` in an app.
- **Most getters**: Compose holds the state, so the binding writes to Raven and rarely reads back.
- **Per-type `R*_create` functions**: the binding creates every widget through the generic
  `Rwidget_create`.
- **Containers with a Compose idiom**: Switcher is an `if`, SizeBox and Spacer are modifiers.
- **Platform internals**: host embedding, Emscripten, Android glue, backend registration,
  allocator hooks, `Rpoll_event`. Coroutines cover `Rparallel_for` and `Rtask_run_async`.
- **Raven Script** (`raven_script.h`, 12): scripts of XML documents; Kotlin is the app's language.
- **The C testing API** (`raven_testing.h`, 30): compose-raven's tests drive the UI through its
  Kotlin harness and the UI driver instead.
- **`raven_test.h`**: header-only C test helpers, which the Kotlin harness replaces; it declares no
  library API, so it isn't counted.

## Gaps

The gaps fall into four kinds, from small features an app expects to whole widgets nobody has
needed yet.

### Everyday features

Each is small, and an app would notice it missing. The review proposed doing them ahead of the
phase 5 design pass, since none needs new design; they were all done on 2026-11-25.

| Feature | Raven API | compose-raven | Status |
|---|---|---|---|
| Clipboard | `Rclipboard_get_text`, `_set_text` | `LocalClipboardManager` | **Done** ([0193](issues/archive/0193-clipboard.md)) |
| Password fields, max length, Enter to submit | `Rtext_input_set_password`, `_set_max_length`, `_set_committed_callback` | `PasswordVisualTransformation`, `TextField(maxLength)`, `KeyboardActions(onDone)` | **Done** ([0194](issues/archive/0194-password-max-length-and-submit-on-textfield.md)) |
| Selectable label text | `Rlabel_set_selectable` | `SelectionContainer`, `DisableSelection` | **Done** ([0195](issues/archive/0195-selectable-text.md)) |
| Scroll a widget into view | `Rwidget_scroll_into_view` | `BringIntoViewRequester` | **Done** ([0196](issues/archive/0196-bring-into-view.md)) |
| Window operations | `Rwindow_focus`, `_center`, `_hide`, `_request_attention`, `_set_picture_in_picture`, `Rwidget_set_window_drag` | `Window(visible)`, `WindowPosition.Aligned`, `WindowPlacement.PictureInPicture`, `LocalWindowController`, `WindowDraggableArea` | **Done** ([0197](issues/archive/0197-window-operations.md)) |
| System accessibility settings | `Raccessibility_get_reduce_motion`, `_high_contrast`, `_text_scale`, `_is_screen_reader_active`, `_get_system_settings` | `LocalAccessibilitySettings`; the host follows the system while the app runs | **Done** ([0198](issues/archive/0198-system-accessibility-settings.md)) |
| UI scale | `Rget_ui_scale`, `Rset_ui_scale` | `RavenUiScale.value` | **Done** ([0199](issues/archive/0199-ui-scale.md)) |

Found while doing them:

| Feature | Needs | Status |
|---|---|---|
| Leading and trailing icons on `TextField` (a password toggle, a search glass, a clear button) | two Raven changes: `Rtext_input_set_insets`, `Rwidget_set_focus_on_click` | **Done** ([0200](issues/archive/0200-leading-and-trailing-icons-on-textfield.md)) |
| The XML translator emitting `Password`, `MaxLength` and a TextInput's action | translator only | **Done** ([0201](issues/archive/0201-textinputs-password-maxlength-and-action-in-the-translator.md)) |
| `Icon`, tinted exactly, for any icon from fonts.google.com/icons | a Raven change: `Rtexture_create_mask_from_file_async` | **Done** ([0221](issues/archive/0221-raven-mask-textures.md), [0222](issues/archive/0222-icon-and-googles-icons.md)) |
| Scroll-linked effects (collapsing toolbars) | two Raven changes: a pre-scroll callback for `Modifier.nestedScroll`, and the drawn scroll offset | Roadmap, phase 5 gestures |
| Raven's HTML copy | `Rclipboard_set_html`; Compose's `ClipboardManager` has nothing for it | Not planned |

### Whole areas with no binding

- **Localisation and right-to-left**: `Rtr`, locales, `Rset_layout_direction` (none of
  `raven_locale.h`'s 33). Needs a design decision first.
- **Touch, pen and gestures**: none of `raven_touch.h`'s 23. Needs a design decision first; on the
  roadmap under phase 5 gestures.
- **Drag and drop inside the app**: no drag source (`Rdrag_begin`) and no drop target on a widget
  (`Rwidget_set_drop_callback`); only files dropped on the window from outside. Phase 5 gestures.
- **Toasts, system notifications, tray, taskbar badges and jump lists**: about 25, none bound.
- **Raven popups** (`Rpopup_open`): on the phase 5 list.

### Widgets with no composable

| Widget | Functions | Note |
|---|---:|---|
| NodeGraph | 87 | specialist, phase 5 fog (`studio`) |
| Timeline | 59 | specialist, phase 5 fog (`studio`) |
| Table | 56 | sortable data grid; the one apps reach for first. With lazy virtualisation |
| Plot | 55 | charts; `examples/plot`. Needs a design decision first |
| Chart | 48 | dashboard charts: pie, donut, gauge, heatmap, sparkline. With Plot's design decision |
| PdfView | 44 | PDF pages shown (PDFium at run time) and PDF documents written |
| BlockEditor | 36 | specialist, phase 5 fog |
| MapView | 34 | raster slippy-map tiles with markers; compose-raven's `MapView` binds the vector `Map` widget instead |
| DiffView | 32 | two texts or a patch compared |
| TileView | 29 | virtualised grid of items. With lazy virtualisation |
| SystemWebView | 19 | the system's web engine in a view |
| Carousel | 17 | pages swiped one at a time |
| TimePicker | 15 | |
| DatePicker | 13 | |
| Toast | 13 | transient messages |
| Rating | 8 | |
| Pagination | 6 | |
| Breadcrumb | 6 | |

NodeGraph, Timeline and BlockEditor alone are 182 functions, about 11% of Raven's API. Switcher,
SizeBox and Canvas are the other unbound widget types, which Compose idioms cover.

### Partial bindings

- **CodeEditor**: undo/redo availability (`Rtext_editor_can_undo`), the selection, scrolling to a
  line, zoom, tab size and auto pairs, and token colours (`Rtheme_set_token_color`) are unbound.
  Its markers are bound.
- **TreeView, ListView**: item-level operations. `ListViewState.revealItem` covers scrolling a
  ListView row into view; `TreeViewState.revealItem`, controlled expansion and its Raven change are
  filed ([Lists at scale: TreeView expansion and item operations](issues/archive/0217-lists-at-scale-treeview-expansion-and-item-operations.md)).
  The selected item is controlled state already; the item at a point waits for drags inside the
  app.
- **Slider**: no vertical orientation (`Rslider_set_orientation`).
- **Textures**: no SVG data in memory or async loading from bytes (`Rtexture_create_from_svg`,
  `_create_async`); `painterResource` reads SVG files, and `Icon` draws them as masks.

## What this means for phase 5

The roadmap's phase 5 list is mostly structural: custom `Layout {}`, lazy virtualisation, popups
and gestures. This review adds:

1. **The everyday-features batch**: done, all seven (above).
2. **Table and TileView**, designed together with lazy virtualisation, since both depend on it.
3. **Decide first**: localisation with right-to-left, touch and pen, and Plot each need a design
   decision (scope, API shape) before they become tickets.
4. **Keep in the fog**: NodeGraph, Timeline, BlockEditor and the `studio` and `browser` samples
   that need them.

## Corrections to the review

- **SpinBox decimals and CodeEditor markers** were listed as unbound, but both were bound at
  `fdfca11` already: `SpinBox(decimals)` uses `Rspin_box_set_decimals`, and `CodeEditor`'s markers
  `Rtext_editor_clear_markers`.
- **The counts**: the review gave 347 of 1,160 and didn't record its script; it can't be
  reproduced. Counting every `R…(` in a header also counted functions a header only calls or
  mentions (`raven_test.h` took several of `raven_window.h`'s). [`tools/api-coverage.sh`](../tools/api-coverage.sh)
  counts `RAVEN_API` declarations instead, and gives 370 of 1,157 at `fdfca11`. The widget types,
  36 of 51, reproduce exactly.

## How this is measured

[`tools/api-coverage.sh`](../tools/api-coverage.sh) prints the totals, the table above without its
last column, and the widget types; give it a commit to count that one instead of the working tree.
`-u raven_window.h` lists one header's unbound functions, for the table's last column.

- **Declared**: every function declared `RAVEN_API` (or `RAVEN_MCP_API`) in `include/raven/*.h`
  and `include/Raven.h`, under its header.
- **Used**: a declared name that appears as an identifier in compose-raven's main sources
  (`*/src/*Main/**/*.kt`), leaving out samples and spikes. A function counts as used when its name
  appears, so a binding that calls it in only one case still counts.
- **Widget types**: the `R_WIDGET_*` constants of `raven_widget.h` against those the main sources
  name, leaving out the `R_WIDGET_LAST_` sentinel.
- **Gaps checked by hand**: each row's functions are checked against the main sources when it
  changes.
