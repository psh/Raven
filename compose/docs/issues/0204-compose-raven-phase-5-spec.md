---
status: closed
wayfinder: map
---

# compose-raven phase 5 spec

## Destination

The first four **batches** of phase 5 designed into `compose/docs/spec.md`, each with its
implementation tickets filed in the tracker, ready to build in this order: designed samples, icons
and resources, small widgets, lists at scale. The rest of phase 5 is charted here as fog. This map
files the implementation tickets; it doesn't build them. Charted on 2026-11-25 from the roadmap's
phase 5 list, [compose-raven phase 3 spec](0023-compose-raven-phase-3-spec.md#not-yet-specified)'s
fog and [Raven API coverage](../api-coverage.md).

## Notes

- Domain: the Compose runtime over Raven's retained widget tree, as before. Vocabulary is in
  `compose/GLOSSARY.md` (**Phase**, **Batch**, **Port gap**); keep it current as terms resolve.
- Skills: grilling tickets call `grilling` + `domain-modeling`; call `codebase-design` for lazy
  virtualisation and for anything that changes the binding layer.
- Standing preference: **keep it slim.** The smallest design that works; extras go to fog or out
  of scope.
- Standing preference: **favour Compose conventions and ergonomics.** Names, parameter shapes,
  defaults and behaviour follow Compose; where Raven differs, compose-raven adapts, and section 12
  records the difference.
- **Raven changes are allowed** when small: one commit each on `compose-raven` with a C test, the
  bindings regenerated, an entry in spec section 11, and an on-hold upstream ticket (as
  [Propose text input insets upstream](0202-propose-text-input-insets-upstream.md)).
- **Implementation tickets**: ordinary tracker tickets (no `wayfinder:` field), each linking its
  spec section, with Deliverables. They are built **in sequence**: each batch's first
  implementation ticket names the previous batch's last in its **Depends on:** line. Decision
  tickets have blocking edges only within their batch, so the design can run ahead of the build.
- **Vector icons must be compatible with <https://fonts.google.com/icons>**: any downloaded
  Material Symbol or Material Icon renders correctly, crisp at every density and tinted, proved by
  committed Google icon fixtures in a test.
- Keep [`api-coverage.md`](../api-coverage.md) in step as each batch's tickets are filed and built.
- Nothing is released beyond the local machine: research branches stay local and unpushed.

## Decisions so far

- [Designed samples: the three ports' plan](archive/0205-designed-samples-the-three-ports-plan.md): `effects` ports as is; `viewport_opengl` redrawn in core GL, macOS only; `viewport_vulkan` over a cinterop local to the sample. Filed as [samples/effects](archive/0218-samples-effects.md), [samples/viewport-opengl](archive/0219-samples-viewport-opengl.md), [samples/viewport-vulkan](archive/0220-samples-viewport-vulkan.md).
- [Icons and resources: SVG facts](archive/0206-icons-and-resources-svg-facts.md): Raven reads every Google icon and redraws SVGs per scale already; only tinting is missing, since Raven's tint multiplies and the icons are black.
- [Icons and resources: vector icons](archive/0207-icons-and-resources-vector-icons.md): `Icon` draws a resource painter as a mask (a Raven change to `R_TEXTURE_ALPHA8`), `Unspecified` tint is the text colour, no `ImageVector`; Google fixtures prove it. Filed as [Raven: mask textures](archive/0221-raven-mask-textures.md) and [Icon and Google's icons](archive/0222-icon-and-googles-icons.md).
- [Icons and resources: generated resources](archive/0208-icons-and-resources-generated-resources.md): Compose's `composeResources/` and `Res` (drawables, fonts, files; no strings or qualifiers) from an in-repo convention plugin; copied beside the executable, embedding opt-in. Filed as [Generated resources: Res and the plugin](archive/0223-generated-resources-res-and-the-plugin.md) and [Generated resources: embedding](archive/0224-generated-resources-embedding.md).
- [Icons and resources: build-time XML translation](archive/0209-icons-and-resources-build-time-xml-translation.md): the specified `compose-raven-xml` / `raven-xml2compose` split built now (unpublished), and a `raven.compose-xml` plugin translating an app's XML folder, subfolders as subpackages; the examples check moves onto it. Filed as [XML translation: compose-raven-xml and raven-xml2compose](archive/0225-xml-translation-compose-raven-xml-and-raven-xml2compose.md) and [XML translation: the raven.compose-xml plugin](archive/0226-xml-translation-the-raven-compose-xml-plugin.md).
- [Small widgets: date and time pickers](archive/0210-small-widgets-date-and-time-pickers.md): controlled `DatePicker(date: LocalDate)` and 24-hour `TimePicker(time: LocalTime)` over Raven's fields, with kotlinx-datetime; not Material 3's state objects. Filed as [Small widgets: DatePicker and TimePicker](archive/0227-small-widgets-datepicker-and-timepicker.md).
- [Small widgets: Rating, Pagination, Breadcrumb and a vertical Slider](archive/0211-small-widgets-rating-pagination-breadcrumb-and-a-vertical-slider.md): four controlled composables over Raven's own drawing, no icons; `VerticalSlider` by Material 3's name. Filed as [Small widgets: Rating, Pagination, Breadcrumb and VerticalSlider](archive/0228-small-widgets-rating-pagination-breadcrumb-and-verticalslider.md).
- [Small widgets: the CodeEditor extras](archive/0212-small-widgets-the-codeeditor-extras.md): undo availability, selection, scroll to line and zoom on the state; four editing parameters; a `SyntaxTheme` composition local, over a Raven change for colours per editor. Filed as [Raven: token colours per editor](archive/0229-raven-token-colours-per-editor.md) and [Small widgets: CodeEditor extras](0230-small-widgets-codeeditor-extras.md).
- [Small widgets: notifications and tray](archive/0213-small-widgets-notifications-and-tray.md): Compose Desktop's `Tray` and notifications (also without a tray, returning whether shown); taskbar progress as a `Window` parameter, `TaskbarBadge` and `DockMenu` composables; no jump list. Filed as [Small widgets: Tray and notifications](0231-small-widgets-tray-and-notifications.md) and [Small widgets: taskbar progress, badge and Dock menu](0232-small-widgets-taskbar-progress-badge-and-dock-menu.md).
- [Lists at scale: lazy virtualisation](archive/0214-lists-at-scale-lazy-virtualisation.md): only the items in view, between two Spacers in the ScrollBox, sizes cached by key with estimates, the place held by key, focus pinned; `layoutInfo` and `canScroll*`; no Raven change. Filed as [Lists at scale: the lazy list window](0233-lists-at-scale-the-lazy-list-window.md) and [Lists at scale: layoutInfo and contentType](0234-lists-at-scale-layoutinfo-and-contenttype.md).
- [Lists at scale: Table](archive/0215-lists-at-scale-table.md): Raven's Table in virtual mode, text cells from a `column(...) { text }` DSL; sorting and selection controlled, the app sorting its items; widths the user's. Filed as [Lists at scale: Table](0235-lists-at-scale-table.md).
- [Lists at scale: TileView](archive/0216-lists-at-scale-tileview.md): Raven's TileView wrapped, the app's items and a controlled selection of keys as the model, a `tile: (T) -> TileContent` for what each tile shows; the lazy grids left alone. Filed as [Lists at scale: TileView](0236-lists-at-scale-tileview.md).
- [Lists at scale: TreeView expansion and item operations](archive/0217-lists-at-scale-treeview-expansion-and-item-operations.md): controlled `expanded` over a Raven expand callback, children asked for lazily, `TreeViewState.revealItem(key)`; tab renaming already works, the item at a point waits for drags. Filed as [Raven: tree expand callback](0237-raven-tree-expand-callback.md) and [Lists at scale: TreeView expansion and revealItem](0238-lists-at-scale-treeview-expansion-and-revealitem.md).

## Not yet specified

Closed on 2026-11-26 with the destination reached: the four batches (designed samples, icons and
resources, small widgets, lists at scale) are designed into `compose/docs/spec.md`, every decision
ticket is archived, and their implementation tickets are filed in sequence, from
[samples/effects](archive/0218-samples-effects.md) to
[Lists at scale: TreeView expansion and revealItem](0238-lists-at-scale-treeview-expansion-and-revealitem.md).
The patches below are the fog that remains, moved on 2026-11-27 to
[compose-raven phase 5 remaining fog](0287-compose-raven-phase-5-remaining-fog.md), which holds them now.

- **Gestures and drag**: Compose's coroutine `pointerInput { awaitPointerEventScope { } }`,
  `draggable`/`detectDragGestures` and long press beyond `onPointerEvent`; drop targets on widgets
  and drags inside the app (`Modifier.dragAndDropTarget`, `Rdrag_begin`,
  `Rwidget_set_drop_callback`); scroll-linked effects such as collapsing toolbars, which need two
  Raven changes (a scroll offered to a callback before the scroll box applies it, reporting what
  is left, for `Modifier.nestedScroll`; and the scroll box's drawn offset, so an effect follows the
  smooth scroll rather than its target).
- **Overlays and motion**: Compose's `Popup(alignment, offset, onDismissRequest)` over Raven's
  `Rpopup_open` ([Menus opened from a button, and Raven's popups](archive/0106-menus-opened-from-a-button-and-ravens-popups.md));
  Toast; exit transitions for `AnimatedVisibility`, which need a Raven change.
- **Multi-window**: `onWindow(title)`, `onDialog()` and tests of `application { }` with several
  windows; DockArea's torn-off panels as real `Window`s; a `WindowSceneStrategy`, list-detail
  scenes, the ViewModel entry decorator and a docking scene for Navigation 3
  ([DockArea and HeaderBar extras](archive/0105-dockarea-and-headerbar-extras.md)).
- **Decide first**: right-to-left and localisation (`LayoutDirection`, `Rset_layout_direction`,
  `Rtr`, direction-aware `Alignment.Start`/`End`); touch and pen (`raven_touch.h`); Plot; custom
  `Layout { }`, which needs a Raven change (custom widgets that take children, measure and arrange
  callbacks). Each needs its scope and API shape decided before it can be ticketed.
- **Lazy extras**: sticky headers, `Modifier.animateItem` and prefetching ahead of the scroll for
  `LazyColumn`/`LazyRow`; a DSL that keeps intervals and asks for keys and content by index only
  near the view, so `items(count) { }` over paged data costs only what is on screen (today every
  key is computed on each composition).
- **Grids**: virtualising `LazyVerticalGrid`, a `LazyHorizontalGrid`, and a virtual grid scrolling
  both ways (spreadsheet style); left alone by [Lists at scale: TileView](archive/0216-lists-at-scale-tileview.md).
- **Table extras**: composable cells (a table over the lazy lists), several rows selected, a
  context menu, and widths the app controls (a Raven change to report a resize).
- **Lottie**: an opt-in `compose-raven-lottie` module over ThorVG; the spike passes, and the
  design sketch and open questions are in [A compose-raven-lottie module](0111-compose-raven-lottie-module.md).
  ThorVG's SVG loader could also serve vector icons.
- **Graphics extras**: a `MetalViewport` convenience, zero-copy GL textures
  (`GL_EXT_memory_object`), a Vulkan-headers cinterop for `compose-raven-vulkan`; a queue lock for
  Raven's own device and a public redraw for a bitmap whose Vulkan image changed, both found by
  [samples/viewport-vulkan](archive/0220-samples-viewport-vulkan.md).

## Out of scope

- **App bundling** (a macOS `.app` with MoltenVK, Linux packages): it goes with the release, phase
  4, which is on hold while nothing is released beyond the local machine.
- **Specialist widgets** (NodeGraph, Timeline, BlockEditor, WebView) and the `studio` and
  `browser` samples that need them: large and callback-driven, left for a later map once the
  common patterns these batches set have settled.
