---
status: closed
---

# samples/workspace

Port Raven's `examples/workspace`: docked panels, a file browser, materials, an island. Spec: [10. Samples](../../spec.md#10-samples).

**Depends on:** [Windows that come and go, and their owners](0112-windows-that-come-and-go-and-their-owners.md), [Images and resources](0115-images-and-resources.md), [Fonts and text styling](0116-fonts-and-text-styling.md), [Theming extras](0119-theming-extras.md), [Lists and trees](0120-lists-and-trees.md), [Scroll position](0121-scroll-position.md), [Island](0128-island.md), [DockArea and FileBrowser](0129-dockarea-and-filebrowser.md).

## Deliverables

- [x] `samples/workspace` with the C example's look: a header bar without background, the island, a `DockArea` of editor, files, appearance, log and about panels.
- [x] Save and restore layout, the layout text shown live from `DockAreaState.layout`, new notes brought forward with `showPanel`.
- [x] The file browser opening files into new panels; the materials chosen app-wide and for one panel; the log keeping its last line in view.
- [x] Runs from Gradle, documented in `compose/README.md`.
- [x] A headless test: a new note panel, a saved and restored layout, a file accepted.

## Gaps

None: its gaps were decided into phase 3 on [compose-raven phase 3 sample gaps](../0095-compose-raven-phase-3-sample-gaps.md).

## Resolution

Done on macOS. `./gradlew build allTests` passes: 530 tests, 4 of them `WorkspaceTest`'s and 1 new in
`DockingTest`. The sample was launched and looks as the C example does.

- **`samples/workspace`** (`settings.gradle.kts`, `launch-workspace`, documented in
  `compose/README.md`; the file browser starts in Raven's `examples` folder, whose path the build
  writes in, as the C example's `RAVEN_EXAMPLE_DIR "/.."`): `WorkspaceApp` holds the app-wide
  theme and material around a custom-header window; a `Workspace` holds the panels added, the log,
  the island's text and the layout saved.
- **The header and the island**: `HeaderBar(title = "", background = false)` with Save layout,
  Restore and New note; the window's `Island` (`expandOnClick = false`) shows the last thing said,
  and unfolds for two seconds each time (the C example's `Risland_expand_for`).
- **The panels**: a `DockArea` of the welcome `CodeEditor`, the files (`FileBrowser` without
  controls or places, `Dock.Left(0.24f)`, not closable), the appearance (`Dock.Right(0.24f)`), the
  log (`Dock.Bottom(0.26f, of = "welcome")`, brought to the front at first) and the about panel
  behind it (`Dock.Tab("log")`), which shows `DockAreaState.layout` live. Closing the welcome panel
  takes it out; a note or a file is then added in the middle of the area.
- **Layouts**: Save layout keeps `saveLayout()`, Restore `loadLayout`s it, and says so when nothing
  was saved.
- **Files and notes**: a file accepted in the browser (a double click or Enter) is read (up to
  2 MB, else "Too large to show") into a new `CodeEditor` panel with `codeEditorLanguageForFile`'s
  language; New note adds an empty one. Each is a tab beside the welcome editor, brought forward
  with `showPanel`, and closing it removes it.
- **Materials**: the theme list (Dark, Light, Dark glass, Light glass, starting at Dark glass) and the
  material of everything are `RavenTheme(look, dark, material)`, a theme starting the material
  over; the panel's own material is `Modifier.material` on its content.
- **Found while porting, filled with tests**: [DockArea and FileBrowser](0129-dockarea-and-filebrowser.md)
  docked a side panel only along the whole area: `Dock.Left/Right/Top/Bottom` gain `of`, a panel to
  dock beside, as `Dock.Tab` has (the spec says so); and a panel added after the area was shown left
  `DockAreaState.layout` stale until the next change: a panel refreshes it once it is in.
- **Tests** (`WorkspaceTest`): a new note's panel in front, in the layout and told by the island;
  Restore with nothing saved, a layout saved, changed and restored; a file accepted opening in a
  new panel with its language; a theme starting the material over.
