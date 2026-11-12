---
status: accepted
---

# Phase split after the phase 2 sample gaps

[compose-raven phase 2 sample gaps](../issues/0073-compose-raven-phase-2-sample-gaps.md) pulled
window, host and harness work into phase 2, and left phase 3 holding app vocabulary, graphics and
distribution together. On 2026-11-11 we re-cut the phases so that each finishes what it starts and
the release isn't held back by vocabulary it doesn't need:

- **Phase 2 finishes the single `Window`**: `WindowState` (`size: DpSize`) replaces phase 1's
  `width`/`height` in phase 2, with every flag and parameter the phase 2 samples use, in one ticket
  ([Window: WindowState and its parameters](../issues/archive/0086-window-windowstate-and-its-parameters.md)),
  so `Window`'s signature changes once, before the samples are written against it. Phase 3 keeps
  windows that come and go, `Dialog`, the lifecycle owners, `icon`, `island`, `onKeyEvent` and
  `backKeys`.
- **Phase 2 brings two thin wrappers early**: `MarkdownText` (without `style`, which waits for
  `TextStyle`) and `Modifier.appearTransition`, the only things keeping `components` and `themes`
  from their C examples. `ListView` stays in phase 3 with the rest of the lists, which share its
  selection and item handling.
- **The dialogs stay native-only in phase 2**: the final API, failing without `native = true`,
  since its default needs phase 3's `Dialog` and `FileBrowser`.
- **Phase 3 splits in two**: phase 3 is the app vocabulary and its five samples; a new **phase 4,
  the release**, holds phase 1's unfinished CI and Linux build, the public test module, the
  translator tool, packaging and publishing, and the Windows target. It needs none of phase 3's
  vocabulary and can run beside it once phase 2 is done.
- **The fog moves to phase 5.** Tickets closed before this decision still say "phase 4" for it;
  read that as phase 5.

## Considered options

- **Keep phase 3 whole and order it into milestones**: no renumbering, but publishing would still
  wait on docking, navigation and the graphics modules.
- **Move `samples/notepad` to phase 3**, dropping phase 2's native-only dialogs, or **pull `Dialog`
  and `FileBrowser` into phase 2**: the first strands phase 2's CodeEditor extras without a sample,
  the second pulls a large widget family forward to remove one failing default.
