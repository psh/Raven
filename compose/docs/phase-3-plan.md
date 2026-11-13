# Phase 3 implementation plan

How the 27 phase 3 tickets ([roadmap, section 4](roadmap.md#4-phase-3)) are built by several agents
at once without tripping over each other. The tickets say *what*; this file says *who, in what
order, and where they meet*. Written 2026-11-12, after
[compose-raven phase 3 sample gaps](issues/0095-compose-raven-phase-3-sample-gaps.md) closed.

## The dependency graph, in waves

A ticket is in the first wave where everything on its **Depends on** line is done. Tickets in the same
wave don't depend on each other.

| Wave | Tickets |
|---|---|
| 1 | [0112 Windows that come and go, and their owners](issues/archive/0112-windows-that-come-and-go-and-their-owners.md), [0113 Generic input, focus and menus](issues/archive/0113-generic-input-focus-and-menus.md), [0114 Accessibility semantics](issues/archive/0114-accessibility-semantics.md), [0115 Images and resources](issues/archive/0115-images-and-resources.md), [0118 Modifier.offset and LocalDensity](issues/archive/0118-layout-offset-and-density.md), [0123 Toolbar](issues/archive/0123-toolbar.md), [0127 Animation](issues/archive/0127-animation.md), [0132 ColorPicker](issues/archive/0132-colorpicker.md) |
| 2 | [0116 Fonts and text styling](issues/archive/0116-fonts-and-text-styling.md), [0120 Lists and trees](issues/0120-lists-and-trees.md), [0124 Custom drawing, viewports and effects](issues/archive/0124-custom-drawing-viewports-and-effects.md), [0128 Island](issues/archive/0128-island.md), [0129 DockArea and FileBrowser](issues/0129-dockarea-and-filebrowser.md) |
| 3 | [0117 Typography in RavenTheme](issues/archive/0117-typography-in-raventheme.md), [0121 Scroll position](issues/0121-scroll-position.md), [0122 Tabs and grids](issues/0122-tabs-and-grids.md), [0125 compose-raven-vulkan](issues/archive/0125-compose-raven-vulkan.md), [0126 compose-raven-opengl](issues/0126-compose-raven-opengl.md), [0130 Dialog and the default dialogs](issues/0130-dialog-and-the-default-dialogs.md), [0137 samples/animations](issues/0137-samples-animations.md) |
| 4 | [0119 Theming extras](issues/archive/0119-theming-extras.md), [0131 compose-raven-navigation3](issues/0131-compose-raven-navigation3.md), [0133 The phase 2 samples completed](issues/0133-phase-2-samples-completed.md), [0138 samples/glass](issues/0138-samples-glass.md) |
| 5 | [0134 samples/editor](issues/0134-samples-editor.md), [0135 samples/workspace](issues/0135-samples-workspace.md), [0136 samples/music](issues/0136-samples-music.md) |

Two critical paths, five tickets each: **0115 → 0116 → 0117 → 0119 → the last samples**, and
**0115 → 0129 → 0130 → 0131 → samples/music**. 0115 Images and resources is the root of both.

## Lanes

Parallel agents collide on shared files even where the graph says they're independent, so work is
split into **lanes** that each own a set of files. A lane is one agent in its own git worktree, on its
own branch from `compose-raven`, doing its tickets in order. A lane stops at a ticket whose
dependencies belong to another lane and aren't merged yet, and reports back.

| Lane | Branch | Tickets, in order | Owns |
|---|---|---|---|
| **A: window and host** | `phase3/lane-a` | 0112 → 0128 → (after 0129 is merged) 0130 → 0131 | `Window`, `RavenHost`, application and window composition, owners, dialogs, `compose-raven-navigation3` |
| **B: input and harness** | `phase3/lane-b` | 0113 → 0114 → (after 0120 is merged) 0121 | the event callback and input modifiers, focus, menus, semantics, most test-harness additions |
| **C: text and theme** | `phase3/lane-c` | 0115 → 0116 → 0117 → 0119 | images and resources, fonts, `TextStyle`, typography, `XxxColors`, `RavenTheme` |
| **D: leaves and drawing** | `phase3/lane-d` | 0118, 0123, 0127, 0132, then (after 0115 is merged) 0124 → 0125, 0126 | layout modifiers and density, Toolbar, animation, ColorPicker, Canvas and viewports, the Vulkan and OpenGL modules |
| **E: containers** | `phase3/lane-e` | (after 0113 and 0115 are merged) 0120 → 0122, 0129 | lists, trees, tabs, grids, DockArea, FileBrowser |
| **Samples** | `phase3/sample-<name>` | 0137 animations, 0138 glass, 0133 phase 2 samples, then 0134 editor, 0135 workspace, 0136 music | one sample module each |

Small additions that a ticket needs in a file another lane owns (for example `Window(icon)` from 0115,
`Window(onKeyEvent)` from 0113, `Window(island)` from 0128, `Window(backKeys)` from 0131, harness
actions from 0121 and 0132) are fine: keep them small and self-contained, so merges stay trivial.

### Launch order

1. **Round 1**, now: lanes A (0112, 0128), B (0113, 0114), C (0115 → 0116 → 0117 → 0119) and D
   (0118, 0123, 0127, 0132), four agents at once.
2. **Round 2**, after 0113 and 0115 are merged: lane E (0120 → 0122, 0129) and lane D's drawing
   (0124 → 0125, 0126).
3. **Round 3**, after 0120 and 0129 are merged: lane A's 0130 → 0131 and lane B's 0121; then the
   samples as each unblocks.

## Where the lanes meet

Hot files, where merges need care:

- **Window and host**: `compose-raven-ui`'s `Window` and `RavenHost` sources. Lane A owns them; 0113,
  0115, 0128 and 0131 add one parameter each.
- **The test harness**: `compose-raven-foundation/src/testHarness/kotlin/.../RavenTest.kt`. 0113,
  0114 (`onNodeWithContentDescription`), 0121 (`performScrollTo`, `performScrollToIndex`) and 0132
  (`performColorChange`) add to it. Add new finders and actions as separate functions at the end of
  their section, not by reshaping existing ones.
- **`settings.gradle.kts`**: the new modules (0125, 0126, 0131) and the five samples each add a line.
- **`compose/docs/roadmap.md`**: each ticket's archive commit changes its own row's link. Rows are one
  line each, so these merge cleanly.
- **`compose/docs/spec.md`**: change it only when the implementation finds the spec wrong or
  incomplete, and say so in the ticket's Resolution.

## How each ticket is done

The phase 2 workflow, unchanged:

1. **Implement** with tests, in one or more commits titled `compose: <what> (NNNN)`, each ending with
   the attribution line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
2. **Verify**: `./gradlew build allTests` from `compose/` passes (macOS only until phase 4). Run a
   sample's launch script, or its `runReleaseExecutable...` task, when a ticket changes what a
   sample shows.
3. **Record**: tick the ticket's deliverables and add a `## Resolution` section saying what was built,
   where, any deviation from the spec and why, and the test count.
4. **Archive**: set `status: closed`, `git mv` the ticket to `issues/archive/`, and fix links both
   ways (`roadmap.md`'s row, the spec, other tickets; see `issues/ISSUES-FORMAT.md`), in a commit
   titled `compose: archive <title> (NNNN)`.

A lane never pushes, and never merges other lanes' branches. The coordinating session merges each
lane's branch into `compose-raven` in the order above, runs `./gradlew build allTests` after every
merge, and starts the next round.

## Known limits

- Everything is checked on macOS only, until phase 4's CI and Linux build.
- Each worktree builds libraven and the modules separately, so the first build in a lane is slow.
- No ticket waits on a Raven fork change: changes 1 and 3 to 8 are done. If a lane finds Raven
  lacking, it makes the change as its own commit with a C test in Raven's `tests/`, as the roadmap's
  section 1 says, and notes it in the ticket and spec section 11.
