---
status: closed
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: Paul Hawke
---

# Multiple windows, dialogs and popups

## Question

Does a composition own one window or many, and how does `ravenApplication` grow into Compose Desktop's `application { Window { } }`? Decide how windows open and close as composables, how dialogs (`Rdialog_*` / file dialogs in `raven_dialogs.h`) and transient popups map onto composables, how per-window settings (background, wallpaper, clear colour via `Rwindow_set_background` / `Rwindow_set_clear_color`) are written, and how [Frame loop and threading](0008-frame-loop-and-threading.md)'s single root and shutdown generalise.

## Resolution

Facts (`raven_window.h`, `raven_dialogs.h`): windows are created, destroyed and given content, and `Rwindow_set_close_callback` can **refuse** a close. Dialogs are sheets over a window. `Rdialog_open` **takes ownership of the content and deletes it** when the dialog closes, Escape included. `Rmessage_box`, `Rfile_dialog_*` and `Rdialog_run` block while running the app's frames themselves. Raven has no generic popup API, only menus and tooltips. Navigation 3: `navigation3-runtime` 1.2.1 is published for `macos_arm64` and `linux_x64` with no Compose UI dependency. The Scene layer and `NavDisplay` are in `navigation3-ui`, which needs Compose UI and Animation and has no macOS variant.

- **Entry points**, with Compose Desktop's names: `application { }`, whose receiver is `ApplicationScope` with `exitApplication()`. It runs the loop and ends when its content has no `Window` left, or on `exitApplication()`. Phase 1's `ravenApplication(...)` is **renamed** `singleWindowApplication(...)`, shorthand for `application { Window(onCloseRequest = ::exitApplication, ...) }`, and `RavenApplicationScope` becomes `ApplicationScope`.
- **`Window(onCloseRequest: () -> Unit, state: WindowState = rememberWindowState(), title: String, customHeader = false, transparent = false, undecorated = false, resizable = true, background: Color = Color.Unspecified) { content }`**:
  - **Closing** is controlled: the close callback always refuses and calls `onCloseRequest`, and the window is destroyed only when the app removes `Window` from the composition.
  - **`WindowState`** (Compose Desktop's) holds `size`, `position`, `placement` (Floating or Maximized) and `isMinimized`. It is mutable state: writes call the Raven setters, and Raven's resize and move callbacks write back.
  - **Composition**: a `Window` emits no node into its parent. It creates its own composition (own Overlay root and Applier) under the one Recomposer, linked with `rememberCompositionContext()`, as Compose Desktop does.
  - **Owners**: each window provides a `LifecycleOwner` and a `SavedStateRegistryOwner`, which persist nothing for now, so that Navigation 3's `rememberNavBackStack` and other lifecycle-aware libraries work.
  - **Per-window settings**: `WindowBackdrop` acts on its own window, and `background` uses `Rwindow_set_clear_color`. A wallpaper texture waits for [Image and resources](0035-image-and-resources.md).
- **`Dialog(onDismissRequest: () -> Unit, properties: DialogProperties = DialogProperties()) { content }`** is a Raven sheet over the window it sits in. Its content has its own composition rooted in a panel given to `Rdialog_open`. When `Dialog` leaves the composition, that composition is disposed first, then `Rdialog_close` deletes the panel. Raven would otherwise close and delete the dialog by itself on Escape, so **fork change 4** adds `Rdialog_set_dismiss_callback`, which can refuse a dismissal: Escape then calls `onDismissRequest` and the app decides, as with windows. `DialogProperties` is compose-raven's own, with `dismissOnBackPress` (Escape) and `dismissOnClickOutside`. This `Dialog` is the building block of Navigation 3's `DialogScene` ([Navigation 3 and scenes](0039-navigation3-and-scenes.md)).
- **Blocking dialogs** become suspend functions run as a task on `RavenDispatcher`: `messageBox(title, text, buttons): MessageResult`, `openFileDialog(filters, multiple): List<String>`, `saveFileDialog(...)`, `selectFolderDialog(...)`. They are called from `LaunchedEffect` or `rememberCoroutineScope().launch`. **Verify first**: Compose recomposing inside Raven's nested frames is safe. If not, these are rebuilt on `Dialog` and Raven's FileBrowser.
- **Tooltips**: `Modifier.tooltip(text: String)` uses `Rwidget_set_tooltip`. It is text only, unlike Compose Desktop's `TooltipArea` (documented). Like material, it is decoration any widget can have.
- **`Popup { }`**: Raven exposes no generic popup, so it goes back to the fog.
- **Navigation**: the Scene layer becomes its own ticket, [Navigation 3 and scenes](0039-navigation3-and-scenes.md).
