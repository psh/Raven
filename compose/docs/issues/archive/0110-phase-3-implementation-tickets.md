---
status: closed
wayfinder: task
parent: 0095
blocked_by: [0096, 0097, 0098, 0099, 0100, 0101, 0102, 0103, 0104, 0105, 0106, 0107, 0108, 0109]
assignee: Paul Hawke
---

# Phase 3 implementation tickets

## Question

Nothing to decide: once the other tickets close, break phase 3 into implementation tickets, as
phase 2 was. `roadmap.md`'s phase 3 section becomes a table of families with their spec sections,
the samples that need them and their tickets, each ticket with a **Depends on** line, in
dependency order, including what phase 3 completes for phase 2 (the default dialogs, `themes`'
`ListView`, `Text(softWrap)`/`fontFamily`, `MarkdownText`'s `style`, the rest of transitions).
Write one sample ticket for each of the five samples, listing only its port gaps, each naming the
phase that completes it. A sample ticket also lists the deliberate differences from its
C example that this map decided (for `music`, in
[Music's pages and back navigation](0098-musics-pages-and-back-navigation.md)). Then close this map.

## Resolution

Done 2026-11-12. Phase 3 is broken into 27 open tickets, numbered in dependency order (each
depends only on earlier ones), and `roadmap.md`'s phase 3 section is now their table, as phase 2's
is.

- **Families** (21): [Windows that come and go, and their owners](../0112-windows-that-come-and-go-and-their-owners.md),
  [Generic input, focus and menus](../0113-generic-input-focus-and-menus.md),
  [Accessibility semantics](../0114-accessibility-semantics.md),
  [Images and resources](../0115-images-and-resources.md),
  [Fonts and text styling](../0116-fonts-and-text-styling.md),
  [Typography in RavenTheme](../0117-typography-in-raventheme.md),
  [Modifier.offset and LocalDensity](0118-layout-offset-and-density.md),
  [Theming extras](../0119-theming-extras.md),
  [Lists and trees](../0120-lists-and-trees.md),
  [Scroll position](../0121-scroll-position.md),
  [Tabs and grids](../0122-tabs-and-grids.md),
  [Toolbar](0123-toolbar.md),
  [Custom drawing, viewports and effects](../0124-custom-drawing-viewports-and-effects.md),
  [compose-raven-vulkan](../0125-compose-raven-vulkan.md),
  [compose-raven-opengl](../0126-compose-raven-opengl.md),
  [Animation](0127-animation.md),
  [Island](../0128-island.md),
  [DockArea and FileBrowser](../0129-dockarea-and-filebrowser.md),
  [Dialog and the default dialogs](../0130-dialog-and-the-default-dialogs.md),
  [compose-raven-navigation3](../0131-compose-raven-navigation3.md),
  [ColorPicker](../0132-colorpicker.md). Every decision of this map is a deliverable of one of them,
  linked to the ticket that made it.
- **The phase 2 samples completed**: [The phase 2 samples completed](../0133-phase-2-samples-completed.md)
  (`themes`' `ListView`, `ColorPicker` and text styling, `overlay`'s `softWrap`, `notepad`'s default
  dialogs and their tests).
- **The five samples**: [samples/editor](../0134-samples-editor.md),
  [samples/workspace](../0135-samples-workspace.md), [samples/music](../0136-samples-music.md),
  [samples/animations](../0137-samples-animations.md), [samples/glass](../0138-samples-glass.md).
  Each lists only its port gaps (none) and, for `music`, the deliberate differences decided in
  [Music's pages and back navigation](0098-musics-pages-and-back-navigation.md).
- Spec section 10's port rule needed no change.
