---
status: open
wayfinder: map
---

# compose-raven phase 3 sample gaps

## Destination

Every [port gap](../../GLOSSARY.md) of the phase 3 samples (`editor`, `workspace`, `music`,
`animations`, `glass`) is settled: either decided into phase 3 (written into `compose/docs/spec.md`),
or left as a port gap that names the phase completing it, with that phase's part of the spec or
roadmap listing it. The open facts and fog notes that touch phase 3 are settled too. Then phase 3 is
broken into implementation tickets, in dependency order, as phase 2 was, so implementation starts
with nothing open. This map plans; it builds nothing.

## Notes

- **Why this map**: [compose-raven phase 3 spec](0023-compose-raven-phase-3-spec.md) designed
  phase 3's vocabulary, but nobody checked it against the five C examples, and phase 2's own check
  ([compose-raven phase 2 sample gaps](0073-compose-raven-phase-2-sample-gaps.md)) turned up enough
  to need a map. The check behind this map was done on 2026-11-12, by reading each example's XML
  and C sources against the spec.
- **Checked, no gap** (so nobody checks them again): timers (`Rtimer_create`) are
  `LaunchedEffect` with `delay`; `Risland_expand_for` is the controlled `Island` with a delay;
  window shortcuts with no menu item (`<Shortcut Keys="F5">`, music's Space) are
  `Window(onKeyEvent)`; `<Widget>` templates and `Rwidget_create_from_xml` are the app's own
  composables; `Visibility="Collapsed"` is composition; `Rfile_read` is the app's own; Border
  effects are `Surface(effect)`; glass's own glass, ComboBox and SpinBox are phase 2's; music's
  `Heading` is `FontWeight.Bold`, its `Cover` and `Icon` atlas are `ImageBitmap` with
  `BitmapPainter` regions and `ColorFilter.tint`, and its `Visualizer` and animations' springs
  track are `Canvas`; `editor`'s Grid, Expander, splitters and TreeView, and `workspace`'s
  FileBrowser and dynamic panels (`Dock.Tab(of)`), are already designed.
- **Settled elsewhere, not reopened**: style classes stay unexposed
  ([Theming](archive/0021-theming.md), [Fonts and text styling](archive/0027-fonts-and-text-styling.md),
  [Theme extras the themes sample uses](archive/0077-theme-extras-the-themes-sample-uses.md)), so
  `music`'s class-styled themes are a port question, not a reason to bring classes back.
  `LazyVerticalGrid` has `GridCells.Fixed` only (spec section 12).
- **Upstream changes** pulled into the fork on 2026-11-12 matter here: `Rpopup_open` /
  `Rpopup_close` / `Rpopup_is_open` (an app's light-dismiss popup), `Rwindow_focus` with borderless
  windows that can become key (Cocoa), and a Vulkan loader that honours `RAVEN_VULKAN_LIBRARY` and
  finds MoltenVK in the app bundle.
- Spec section 10's port rule applies: a port has the C example's look and the behaviour its
  phase's vocabulary covers, and lists its port gaps with the phase that completes each. Nothing
  is left out without a home.
- Domain: the Compose runtime over Raven's retained widget tree. Vocabulary is in
  `compose/GLOSSARY.md`; keep it current as terms resolve.
- Required reading for Raven questions: `docs/ARCHITECTURE.md`, `include/raven/` and the C example
  being ported in `examples/`.
- Skills: grilling tickets call `grilling` + `domain-modeling`.
- Standing preference: **keep it slim.** Lean towards the smallest design that works.
- Standing preference: **favour Compose conventions and ergonomics**, and document every difference
  from Compose in spec section 12.
- The decisions of [compose-raven spec](0001-compose-raven-spec.md),
  [compose-raven phase 3 spec](0023-compose-raven-phase-3-spec.md) and
  [compose-raven phase 2 sample gaps](0073-compose-raven-phase-2-sample-gaps.md) are settled and
  live in `compose/docs/spec.md`. Build on them, don't reopen them.
- Closing a ticket also updates `compose/docs/spec.md` and `compose/docs/roadmap.md`.
- Ticket conventions: `ISSUES-FORMAT.md` in this directory.

## Decisions so far

<!-- one line per closed ticket: [title](file): gist -->

- [Scroll position](archive/0096-scroll-position.md): `ScrollState` (a `state` of the scroll containers: polled `value`, `scrollTo`, a jumping `animateScrollTo`, `scrollToEnd()`), `LazyListState` (`firstVisibleItem*`, `scrollToItem` to the top) and `ListViewState` (`revealItem` only); scroll states are saveable and apply positions on attach; `performScrollTo`/`performScrollToIndex` in the harness.
- [Verify Navigation 3 under the host](archive/0097-verify-navigation-3-under-the-host.md): passes, no fallback; on Kotlin/Native `rememberNavBackStack` needs a `SavedStateConfiguration` declaring the `NavKey` subclasses, and entry state lives in the decorator's `SaveableStateHolder`, so a page kept as an entry keeps its state with nothing from the window.
- [Music's pages and back navigation](archive/0098-musics-pages-and-back-navigation.md): a `NavDisplay` over keys per page (the one phase 3 sample using `compose-raven-navigation3`), pages brought to the front when visited again, Search's two views a plain `if`; nothing new for compose-raven, and four deliberate differences from the C example go to `music`'s sample ticket.
- [Music's class-styled themes](archive/0099-musics-class-styled-themes.md): classes stay out; the port has its own palette per theme over the C example's theme files, and compose-raven gains explicit hovered and pressed colours (buttons, `Checkbox`, `RadioButton`, `Slider`), `ColorFilter.textColor()` and `TextFieldDefaults.colors(unfocusedIndicatorColor)`.

## Not yet specified

The map's tickets, by sample (a ticket can serve several):

- `editor`: [The editor's scene viewport](0101-the-editors-scene-viewport.md),
  [The editor's run-time extras](0102-the-editors-run-time-extras.md)
- `workspace`: [DockArea and HeaderBar extras](0105-dockarea-and-headerbar-extras.md)
- `music`: [Music's accent picker](0100-musics-accent-picker.md),
  [Animated effects and continuous redraw](0104-animated-effects-and-continuous-redraw.md),
  [DockArea and HeaderBar extras](0105-dockarea-and-headerbar-extras.md) (the header),
  [Small port gaps](0107-small-port-gaps.md)
- `animations`: [Animations beyond appear transitions](0103-animations-beyond-appear-transitions.md)
- `glass`: [Animated effects and continuous redraw](0104-animated-effects-and-continuous-redraw.md),
  [Menus opened from a button, and Raven's popups](0106-menus-opened-from-a-button-and-ravens-popups.md),
  [Small port gaps](0107-small-port-gaps.md)
- Open facts: [Verify GL and Metal on macOS child views](0108-verify-gl-and-metal-on-macos-child-views.md)
- Upstream: [Upstream changes and the fog notes](0109-upstream-changes-and-the-fog-notes.md)
- Last: [Phase 3 implementation tickets](0110-phase-3-implementation-tickets.md)

## Out of scope

- Implementation of phase 3: the tickets [Phase 3 implementation tickets](0110-phase-3-implementation-tickets.md)
  creates do that.
- Reopening phase 3's vocabulary decisions, beyond what a gap ticket needs to add to them.
- The phase 5 samples (`effects`, `viewport_opengl`, `viewport_vulkan`, `studio`, `browser`) and
  designing the phase 5 fog, except where a gap ticket here moves a patch of it.
- Phase 4 (release) work.
