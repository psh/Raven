# Raven widget tree API for a Compose Applier

Research for ticket 0004 (parent 0001, compose-raven spec). Question: what does
Raven's public C API give a Compose `Applier` and its nodes, and where are the gaps?

Sources: Raven at commit `e9a6016` (v0.2.1). Every claim cites a file in this repo
(`include/`, `src/`, `docs/`). Paths are relative to the repo root.

## TL;DR

- The tree API already fits an Applier closely: `Rwidget_create`, `Rwidget_insert_child(parent, child, index)`,
  `Rwidget_remove_child` (detach, keep alive), `Rwidget_destroy` (whole subtree), plus
  `get_child` / `get_child_count` / `get_parent`. Reparenting is implicit: inserting a widget
  that already has a parent moves it.
- **There is no in-place move.** A move is detach + insert. A detach drops focus, hover, capture
  and tooltip, and an insert replays the widget's appear transition. Compose's `move()` will
  cause these side effects unless Raven adds a `Rwidget_move_child`.
- Layout follows Slate. Layout values a parent reads (padding, h/v align, auto/fill weight,
  max size, grid cell, canvas anchors) live in the **child's slot** and stay with the child
  across reparenting. Size constraints are set on the widget. Spacing is set on the panel.
- Every callback is a plain C function pointer with a trailing `void* user_data`, and the Rid
  comes first. That fits `staticCFunction` + `StableRef`. Each event has one slot, and a
  set call replaces the previous handler. Setting a value from code does not fire the
  control's changed callback, so recomposition cannot loop back into itself.
- Threading: everything is UI-thread-only (the thread that called `Rinit`) and is enforced
  at runtime with `R_ERROR_WRONG_THREAD`. Other threads use `Rtask_post` (thread-safe, wakes
  the loop) or `Rwake_up`.
- Frame clock: `Rset_frame_callback(cb, user_data)` runs once per update, after tasks and
  timers and before layout. Use `Rwake_up()` to request a frame and `Rget_time()` for the
  timestamp. There is only one global slot.
- Headless: `RInitDesc{ backend = R_BACKEND_NULL, headless = R_TRUE }` gives the null platform
  and the null renderer. Layout still runs, and input can be injected. All but one of Raven's
  own tests run this way.

## 1. Creating and destroying widgets

| Call | Behaviour | Source |
|---|---|---|
| `Rid Rwidget_create(RWidgetType)` | Creates a detached widget. It is not shown until it is added to a parent that belongs to a window. | `include/raven/raven_widget.h:182-186`, `src/widget/widget.c:305-310` |
| `void Rwidget_destroy(Rid)` | Detaches the widget, then deletes it and all its descendants. | `raven_widget.h:188-189`, `widget.c:108-120` |
| `int Rwidget_is_valid(Rid)` / `Rwidget_get_type` | Handles carry a generation counter, so a stale handle is detected and reported instead of crashing. | `raven_widget.h:190-191`, `include/raven/raven_core.h:54-60` |
| `Rwidget_set_destroy_callback(w, cb, user_data)` | Called once when the widget is gone (destroyed directly, with its parent or window, or by `Rshutdown`), on the UI thread. **The Rid it receives no longer resolves**, so `Rwidget_get_user_handle` cannot be used there. The `StableRef` to dispose must come in via `user_data`. | `raven_widget.h:336-344`, `widget.c:88-93` |
| `Rwidget_set_user_handle` / `get_user_handle` | One `void*` per widget for the application. | `raven_widget.h:333-334` |
| Convenience creators | `Rlabel_create(text)`, `Rbutton_create(text)`, `Rcheckbox_create(text)`, `Rbackground_blur_create()`, and others. Everything else goes through `Rwidget_create(type)`. | `include/raven/raven_controls.h:62,132,147`, `include/raven/raven_layout.h:152` |

- **Deferred free.** If a destroy happens while a callback is being dispatched (`dispatch_depth > 0`),
  the handles die at once but the memory, and with it the destroy callback, waits until the dispatch
  ends. So a widget can be destroyed from inside its own callback. Sources: `widget.c:113-119`,
  `src/core/context.c:36-46`, `docs/ARCHITECTURE.md` ("Deferred destruction").
  Frame callbacks, tasks and timers are all dispatched this way (`context.c:309-311, 413-433, 610-614`).
- **Ownership.** A detached widget belongs to the caller. `Rshutdown` destroys windows first,
  then every widget that has no parent (`context.c:199-213`), so a detached widget leaks
  only until shutdown.
- **Window root.** `Rwindow_set_content(window, widget)`: the window takes ownership and **deletes
  the previous content**. `Rwindow_destroy` deletes the content widgets too. Source:
  `include/raven/raven_window.h:307-316`. A Compose root should be one fixed panel
  (for example a VBox) set once, so the Applier never replaces it.

## 2. Insert at index, move, reparent, remove

| Call | Behaviour | Source |
|---|---|---|
| `Rid Rwidget_insert_child(parent, child, index)` | Inserts at `index`. An index `< 0` or `> count` appends. If the child already has a parent, **it is detached first** and the index then applies to the list without it. | `raven_widget.h:193-200`, `widget.c:326-342`, `widget.c:193-214` (clamp at line 200) |
| `Rid Rwidget_add_child(parent, child)` | `insert_child(..., -1)`. | `widget.c:343-345` |
| `void Rwidget_remove_child(parent, child)` | Detaches without deleting, and the caller owns the child again. It is an error if `child` is not a child of `parent`. | `raven_widget.h:202-203`, `widget.c:347-358` |
| `void Rwidget_clear_children(parent)` | **Destroys** every child. | `raven_widget.h:205-206`, `widget.c:359-363` |
| `Rwidget_get_parent` / `get_child_count` / `get_child(i)` | Read the tree. | `raven_widget.h:208-212` |

When an insert is refused:

- The parent has `RVN_CF_NO_CHILDREN` (`widget.c:331-334`). This covers Label, Image, Slider,
  Checkbox, TextInput, SpinBox, ComboBox, ProgressBar, Separator, Spacer, ListView, TreeView,
  TileView, MenuItem, TextEditor, Viewport, ColorPicker, Timeline, NodeGraph, BlockEditor and
  FileBrowser (the `.flags` lines in `src/widgets/*.c`, for example `label.c:611`,
  `slider.c:336`, `overlay.c:25` for Spacer).
- The parent is the child itself or one of its descendants (`widget.c:335-338`).

**Single-child parents replace silently.** In Border, SizeBox, Button, Expander and BackgroundBlur
(`RVN_CF_SINGLE_CHILD`: `border.c:220`, `size_box.c:62`, `button.c:242`, `expander.c:232`,
`background_blur.c:177`), inserting a second child **destroys the existing one**
(`widget.c:197-199`). Two more cases follow from this:

- `Rbutton_set_text` creates a Label child, or reuses one that is already there (`button.c:201-214`).
- A Compose `Button { content }` must not also call `Rbutton_set_text`.

The Applier must treat these parents as "at most one child". If Compose briefly inserts
a new child before removing the old one, the old one is destroyed and its later removal
fails.

**Side effects of each structural change.** `rvn_widget_detach` (`widget.c:162-191`):

- calls `rvn_window_forget_widget`, which clears focus, capture, hover, the tooltip, the drop
  target and the touch state for the subtree (`src/widget/window.c:636-654`);
- releases native resources;
- marks the window for layout and paint;
- calls the parent's `children_changed`.

`rvn_widget_add_child` then:

- inherits the theme and font scope;
- calls `rvn_widget_appear`, which **replays the appear transition** if one is set
  (`src/widget/anim.c:179-183`);
- invalidates the layout.

Both functions call `rvn_a11y_structure_changed()`.

### Mapping to `Applier`

| Compose | Raven |
|---|---|
| `insertTopDown` / `insertBottomUp(index, node)` | `Rwidget_insert_child(current, node, index)`. The slot is stored on the child, so slot properties can be set before or after the insert. Bottom-up avoids walking a window subtree for each insert (`rvn_widget_set_window` recurses, `widget.c:133-137`). |
| `remove(index, count)` | Use `Rwidget_get_child(current, index)` followed by `Rwidget_destroy` for nodes Compose discards. Use `Rwidget_remove_child` only when the node will be reused. Iterate from high to low index. |
| `move(from, to, count)` | **No native call.** Emulate it with `get_child` plus `insert_child` per item. Compose passes `to` as an index before removal, so it has to be adjusted (`to - count` when `from < to`). Each step loses focus and hover and replays the appear transition. |
| `clear()` | `Rwidget_clear_children(root)` (destroys). |
| `onEndChanges` | Nothing needed. Layout is lazy and runs in the same frame (section 5). |

## 3. Layout model and properties

The model is Slate's. Measure runs bottom-up to compute a desired size, then arrange runs
top-down so each panel places its children according to their **slots**. A widget has at most
one parent, so its slot is stored on the child (`raven_widget.h:1-15`,
`docs/ARCHITECTURE.md` "A frame" step 2). The slot struct is `src/widget/widget.h:63-85`.

| Concept (Compose modifier) | Raven call | Applies to | Source |
|---|---|---|---|
| padding | `Rslot_set_padding(child, l, t, r, b)` | all panels | `raven_widget.h:229-230` |
| horizontal / vertical alignment | `Rslot_set_halign(child, R_HALIGN_FILL\|LEFT\|CENTER\|RIGHT)`, `Rslot_set_valign(...)`; the default is FILL | all panels | `raven_widget.h:120-132, 232-234`, `widget.c:61-62` |
| weight | `Rslot_set_fill(child, coefficient)` shares the space the auto slots leave; `Rslot_set_auto(child)` takes the desired size. In a Splitter the fill coefficient is the relative pane size. | VBox, HBox, Splitter | `raven_widget.h:236-248`, `widget.c:459-471` |
| max along main axis | `Rslot_set_max_size(child, v)` | VBox, HBox | `raven_widget.h:250-251` |
| fixed / min / max size | `Rwidget_set_size`, `Rwidget_set_min_size`, `Rwidget_set_max_size`; a negative value removes the constraint | constraints on the desired size | `raven_widget.h:313-320` |
| grid cell / span | `Rslot_set_grid`, `Rslot_set_grid_span`; `Rgrid_set_column_fill/row_fill` | Grid, UniformGrid | `raven_widget.h:253-255`, `raven_layout.h:72-77` |
| absolute / anchored | `Rslot_set_anchors`, `Rslot_set_position`, `Rslot_set_size`, `Rslot_set_alignment` | Canvas | `raven_widget.h:257-268` |
| spacing (Arrangement.spacedBy) | `Rpanel_set_spacing(panel, gap)` | VBox, HBox, WrapBox, Grid, UniformGrid, ScrollBox | `raven_layout.h:64-68` |
| inner padding / background | `Rborder_set_padding`, `Rborder_set_background`, `Rborder_set_corner_radius`, ... | Border | `raven_layout.h:88-104` |
| aspect ratio | `Rsize_box_set_aspect_ratio` | SizeBox | `raven_layout.h:106-113` |
| visibility | `Rwidget_set_visibility(R_VISIBLE\|R_HIDDEN\|R_COLLAPSED)` | all | `raven_widget.h:134-141, 278` |
| enabled, opacity, clip, tooltip, cursor, focusable | `Rwidget_set_enabled/opacity/clipping/tooltip/cursor/focusable` | all | `raven_widget.h:278-331` |
| style | `Rwidget_set_color`, `set_metric`, `set_class`, `set_theme`, `set_font`, `set_material` | all | `raven_widget.h:392-462` |
| RTL | `Rwidget_set_direction` | all | `raven_layout.h:233-260` |

The table of which slot values each panel reads is at `raven_layout.h:9-23`. Values a panel
does not use are ignored (`raven_widget.h:13-14`). New widgets start with fill alignment,
`fill = 1.0`, spans of 1, `is_fill = false` (auto), and no size constraints
(`widget.c:55-65`, `widget.h:67`).

Reading layout back: `Rwidget_get_rect` gives the bounds after the last layout and
`Rwidget_get_desired_size` gives the measured size (`raven_widget.h:322-324`).

**Custom measure only, no custom arrange.** `R_WIDGET_CUSTOM` takes `Rcustom_set_measure_callback`
and `Rcustom_set_paint_callback` (`include/raven/raven_draw.h:47-52`). Its class has a
`measure` and a `paint` but no arrange hook (`src/widgets/viewport.c:342-348`). Compose
`Layout { }` with custom child placement is therefore not possible today. The closest
substitute is a Canvas panel with positions set from Kotlin.

## 4. Callbacks and user data (Kotlin interop)

- **Shape.** Every callback is a non-capturing C function pointer. The handle comes first and
  `void* user_data` comes last. Examples:
  - `RButtonClickedCallback(Rid, void*)`, `RCheckboxChangedCallback(Rid, int, void*)`,
    `RSliderChangedCallback(Rid, float, void*)`, `RTextChangedCallback(Rid, const char*, void*)`
    (`raven_controls.h:139, 153, 169, 211`);
  - the generic `RWidgetEventCallback(Rid, const REvent*, void*)`, which returns int: R_TRUE
    consumes the event (`raven_widget.h:346-352`);
  - `RWidgetDestroyCallback(Rid, void*)` (`raven_widget.h:343`);
  - `RTaskCallback(void*)`, `RFrameCallback(double, void*)`, `RTimerCallback(Rid, void*)`
    (`raven_core.h:313, 373, 402`).

  The full list is the `typedef ... (*R...Callback)` lines in `include/raven/*.h`. Every one
  follows this pattern. `RErrorCallback` is the only one without `user_data`
  (`raven_core.h:510`).
  This maps directly onto `staticCFunction { id, ..., ud -> ud!!.asStableRef<Node>().get()... }`.
- **One slot per event and widget.** Each `R..._set_..._callback` stores a single
  `(callback, user_data)` pair and overwrites the previous one (for example `widget.c:693-697`).
  A node can register one shared static function per event kind once, and keep the Kotlin
  lambda in a mutable field on the node behind a single `StableRef`. Updating a Compose
  `onClick` then never needs another C call.
- **StableRef lifetime.** Create one `StableRef` per node. Pass it as `user_data` to every
  callback, including `Rwidget_set_destroy_callback`. Dispose it in the destroy callback,
  which is guaranteed to fire for every widget, `Rshutdown` included (`raven_widget.h:336-342`,
  `context.c:205-213`). The `Rid` passed to the destroy callback is already invalid
  (`widget.c:91-92`).
- **No feedback loops.** Setters from code do not fire the changed callbacks:
  - `Rcheckbox_set_checked` calls `rvn_checkbox_set(w, v, notify=R_FALSE)` (`checkbox.c:376, 499-502`);
  - `Rslider_set_value` does the same (`slider.c:40, 354-357`);
  - `Rtext_input_set_text` calls no callback and is a no-op when the text is equal
    (`text_input.c:197-200`, `textedit.c:67-81`). A different text moves the caret to the end
    (`textedit.c:78`), so a controlled text field must only write back text that differs.
- **REvent.** Type (mouse move/down/up/wheel/enter/leave, key down/up, text, focus gained/lost),
  window and local coordinates, button, clicks, key, modifiers, codepoint and wheel data
  (`raven_window.h:199-249`). Mouse and key events bubble from the target to its parents
  (`raven_window.h:215-217`). The widget's event callback sees an event before the widget
  itself does (`src/widget/events.c:57`).
- **Actions.** As an alternative to per-widget callbacks, `Rwidget_set_action(w, "name")` plus
  `Raction_bind(name, cb, ud)` dispatches by string (`raven_widget.h:354-359`,
  `raven_core.h:417-437`). Raven also has built-in actions such as `app.quit` and `show:NAME`.
  Per-widget callbacks fit Compose better.

## 5. Threading, the event loop and a frame clock

- **UI thread only.** "Unless a function is documented as thread-safe, it must be called from the
  UI thread (the thread that called Rinit)" (`raven_core.h:16-17`). The thread is recorded at
  `context.c:118` and checked on every API entry by `rvn_api_check`, which raises
  `R_ERROR_WRONG_THREAD` with the hint "use Rtask_post from other threads"
  (`context.c:23-34`, `src/core/context.h:296-297`). `Ris_ui_thread()` reports which thread
  the caller is on (`raven_core.h:395`).
- **Thread-safe entry points.** `Rtask_post`, `Rtask_run_async`, `Rwake_up`, `Rquit`,
  `Rget_time`, the `Rmemory_*` functions and `Rparallel_for` (`raven_core.h:229-246, 284, 319, 376-392`).
- **`Rtask_post(cb, ud)`** queues work under a mutex and wakes the platform (`context.c:278-291`).
  Each update takes the whole batch, and tasks posted while that batch runs wait for the
  next update (`context.c:293-313`, comment at 297). `Rshutdown` drains the queue once more
  (`context.c:217`), so a `StableRef` passed through a task is never lost. This is the
  Dispatcher for a Compose `Recomposer` coroutine context: `dispatch()` becomes
  `Rtask_post(staticCFunction(runRunnable), StableRef(block))`.
- **Loop.** The application owns the loop. `Rdraw_frame()` never sleeps.
  `Rwait_frame(timeout)` sleeps until an OS event, a task, a timer, an animation or a redraw,
  then produces one frame. Both return R_FALSE after `Rquit` or when the last window closes
  (`raven_core.h:253-282`, `context.c:625-641`).
- **Order of one update** (`rvn_update`, `context.c:596-623`):
  1. posted tasks;
  2. timers;
  3. device check;
  4. **frame callback** `(delta_seconds, user_data)`;
  5. layout, paint and submit for every window that changed.

  `docs/ARCHITECTURE.md` "A frame" says the same. Because step 4 comes before layout, a
  recomposition run inside the frame callback is laid out and painted in the same frame.
- **Frame clock recipe.** Back the Compose `MonotonicFrameClock` with a `BroadcastFrameClock`:
  1. Register it once with `Rset_frame_callback(staticCFunction(onFrame), ref)`
     (`raven_core.h:307-314`, `context.c:563-567`, UI-thread only).
  2. In `onFrame`, call `clock.sendFrame((Rget_time() * 1e9).toLong())`. `Rget_time` is
     monotonic seconds since `Rinit` (`raven_core.h:319-320`).
  3. When a frame is awaited, call `Rwake_up()`. It is thread-safe and wakes `Rwait_frame`
     (`context.c:341-343, 48-51`). `Rwait_frame` always runs `rvn_update` after it wakes
     (`context.c:633-641`).
  4. Recomposition triggered from a task runs in that same update before the frame callback,
     so no extra frame is needed.

  `Rset_animation_time_step(seconds)` gives deterministic frame deltas for tests
  (`raven_core.h:361-366`).
- **Paint threads.** The UI thread owns the tree. Paint jobs on worker threads only read it,
  and only while the UI thread waits. Custom widgets, which call application code, are always
  painted on the UI thread (`docs/ARCHITECTURE.md` "Threads" and "Parallel painting"). Kotlin
  paint and measure callbacks therefore run on the UI thread.

## 6. Null backends (headless)

- `RBackend.R_BACKEND_NULL` renders nothing and is meant for headless runs and unit tests
  (`raven_core.h:69-76`, `context.c:80`, `src/render/null/null_backend.c`).
- `RInitDesc.headless = R_TRUE` opens no OS window and selects the null platform
  (`raven_core.h:118-119`, `context.c:136`, `src/platform/platform.c:26-28`). On an OS without
  a window system the null platform is also the fallback (`platform.c:39-40`).
- The null platform's `pump` blocks on a condition variable until `wake` is called or the
  timeout expires (`src/platform/null/null_platform.c:179-197`). So `Rwait_frame`, `Rtask_post`
  and `Rwake_up` behave headless as they do on a real platform.
- Tests run this way, for example `desc.backend = R_BACKEND_NULL; desc.headless = R_TRUE;`
  (`tests/test_dock_tearing.c:71-72`, `tests/benchmark.c:52-53`). Every test except
  `test_render` runs headless. Layout runs, `Rwidget_get_rect` is meaningful, and input is
  injected with `Rwindow_inject_mouse_move/button/key/text` (`docs/ARCHITECTURE.md` "Tests",
  `raven_window.h:472-484`). Each test also fails when a block is still allocated after
  `Rshutdown`, which helps catch leaked StableRef or widget pairs.
- Initialization example for Kotlin tests:
  `Rinit_ex(RInitDesc{backend=R_BACKEND_NULL, headless=R_TRUE, worker_threads=-1})`.
  Setting `worker_threads` to a negative value means no worker threads (`raven_core.h:110`).

## 7. Widget types (`RWidgetType`, `raven_widget.h:25-118`)

| Group | Types |
|---|---|
| Panels (`raven_layout.h`) | `VBOX`, `HBOX`, `GRID`, `UNIFORM_GRID`, `OVERLAY`, `BORDER`\*, `SIZE_BOX`\*, `SPACER`†, `SCROLL_BOX`, `SPLITTER`, `WRAP_BOX`, `CANVAS`, `SWITCHER`, `TAB_VIEW`, `EXPANDER`\*, `BACKGROUND_BLUR`\* |
| Controls (`raven_controls.h`) | `LABEL`†, `BUTTON`\*, `CHECKBOX`†, `SLIDER`†, `PROGRESSBAR`†, `IMAGE`†, `TEXT_INPUT`†, `SPIN_BOX`†, `COMBO_BOX`†, `SEPARATOR`†, `LIST_VIEW`†, `TREE_VIEW`†, `TILE_VIEW`†, `MENU_BAR`, `MENU`, `MENU_ITEM`† |
| Drawing | `VIEWPORT`† (app-rendered, `raven_viewport.h`), `CUSTOM` (paint and measure callbacks, `raven_draw.h`) |
| Editors and tools | `TEXT_EDITOR`†, `COLOR_PICKER`†, `TIMELINE`†, `NODE_GRAPH`†, `BLOCK_EDITOR`† (`raven_text_editor.h`, `raven_tools.h`) |
| Shell and dialogs | `HEADER_BAR`, `DOCK_AREA`, `ISLAND` (`raven_shell.h`), `FILE_BROWSER`† (`raven_dialogs.h`) |

\* single child: a second insert destroys the first. † no children: an insert is an error.
There are 43 types in all. Some have their own item model rather than child widgets, and the
Applier should model those as leaf nodes with a list-valued property:

- ListView: `Rlist_view_add_item` / `set_virtual_items`;
- TreeView: `Rtree_view_add_item`, with tree items as their own Rids;
- ComboBox: `Rcombo_box_add_item`;
- TileView: `Rtile_view_set_items`;
- Menu: `Rmenu_add_item`.

Sources: `raven_controls.h:239-472`.

## 8. Gaps for an Applier

1. **No in-place reorder** (main gap). `move()` has to detach and re-insert, which clears
   focus, hover, capture and tooltip for the subtree (`window.c:636-654`), releases native
   resources, and replays appear transitions (`anim.c:179-183`). Proposal: add
   `Rwidget_move_child(parent, from, to)` that only reorders `children[]` and invalidates
   the layout. Alternatively, skip `forget_widget` and `appear` in `rvn_widget_add_child`
   when the old parent equals the new one.
2. **Single-child parents destroy on a second insert** (`widget.c:197-199`). The Applier must
   remove before it inserts, or Raven could return an error instead of destroying.
3. **One global frame callback** (`context.c:563-567`). compose-raven takes the only slot. An
   app that also wants `Rset_frame_callback` would overwrite it. Either compose-raven exposes
   its own hook, or Raven adds a list of frame callbacks.
4. **No arrange hook for custom layouts.** `R_WIDGET_CUSTOM` can measure and paint but cannot
   place children (`viewport.c:342-348`), so Compose `Layout {}` is limited to Canvas
   emulation.
5. **No public child-index lookup.** `rvn_widget_child_index` is internal (`widget.c:139-145`).
   An Applier tracks indices itself, so this is minor.
6. **No "request frame" call distinct from wake.** `Rwake_up` works because every wake runs a
   full update (`context.c:633-641`). Note that `Rwindow_request_redraw` and
   `Rwindow_set_continuous_redraw` exist per window (`raven_window.h:418-421`).
7. **Destroy callbacks during shutdown** (a caution). The destroy callback can run during `Rshutdown`
   after windows are gone. Kotlin code in it must only dispose refs, not call Raven.
   This follows from `context.c:205-213`.

What is *not* a gap:

- reparenting (implicit on insert);
- slot persistence across moves (the slot is stored on the child);
- stale-handle safety (generation counters);
- deleting a widget from inside its own callback (deferred free);
- posting work from other threads (`Rtask_post`);
- headless testing.
