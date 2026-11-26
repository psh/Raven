---
status: open
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

- [Icons and resources: SVG facts](archive/0206-icons-and-resources-svg-facts.md): Raven reads every Google icon and redraws SVGs per scale already; only tinting is missing, since Raven's tint multiplies and the icons are black.

## Not yet specified

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
- **Lottie**: an opt-in `compose-raven-lottie` module over ThorVG; the spike passes, and the
  design sketch and open questions are in [A compose-raven-lottie module](0111-compose-raven-lottie-module.md).
  ThorVG's SVG loader could also serve vector icons.
- **Graphics extras**: a `MetalViewport` convenience, zero-copy GL textures
  (`GL_EXT_memory_object`), a Vulkan-headers cinterop for `compose-raven-vulkan`.

## Out of scope

- **App bundling** (a macOS `.app` with MoltenVK, Linux packages): it goes with the release, phase
  4, which is on hold while nothing is released beyond the local machine.
- **Specialist widgets** (NodeGraph, Timeline, BlockEditor, WebView) and the `studio` and
  `browser` samples that need them: large and callback-driven, left for a later map once the
  common patterns these batches set have settled.
