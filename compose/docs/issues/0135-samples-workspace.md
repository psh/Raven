---
status: open
---

# samples/workspace

Port Raven's `examples/workspace`: docked panels, a file browser, materials, an island. Spec: [10. Samples](../spec.md#10-samples).

**Depends on:** [Windows that come and go, and their owners](archive/0112-windows-that-come-and-go-and-their-owners.md), [Images and resources](archive/0115-images-and-resources.md), [Fonts and text styling](archive/0116-fonts-and-text-styling.md), [Theming extras](archive/0119-theming-extras.md), [Lists and trees](archive/0120-lists-and-trees.md), [Scroll position](archive/0121-scroll-position.md), [Island](archive/0128-island.md), [DockArea and FileBrowser](archive/0129-dockarea-and-filebrowser.md).

## Deliverables

- [ ] `samples/workspace` with the C example's look: a header bar without background, the island, a `DockArea` of editor, files, appearance, log and about panels.
- [ ] Save and restore layout, the layout text shown live from `DockAreaState.layout`, new notes brought forward with `showPanel`.
- [ ] The file browser opening files into new panels; the materials chosen app-wide and for one panel; the log keeping its last line in view.
- [ ] Runs from Gradle, documented in `compose/README.md`.
- [ ] A headless test: a new note panel, a saved and restored layout, a file accepted.

## Gaps

None: its gaps were decided into phase 3 on [compose-raven phase 3 sample gaps](0095-compose-raven-phase-3-sample-gaps.md).
