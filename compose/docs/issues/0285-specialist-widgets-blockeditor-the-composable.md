---
status: open
---

# Specialist widgets: BlockEditor, the composable

Bind Raven's BlockEditor as `BlockEditor(program, onProgramChange, kinds, ...)` with a
`BlockEditorState`. Spec: [8: BlockEditor](../spec.md#blockeditor-phase-5) and section 12. Planned in [Specialist widgets: BlockEditor](archive/0278-specialist-widgets-blockeditor.md).

**Depends on:** [samples/studio](0284-samples-studio.md), the ticket before it in phase 5's implementation sequence.

## Deliverables

- [ ] `BlockEditor(program, onProgramChange, kinds, modifier, state, categoryColors, palette, onScriptClick, onContextMenu)` over `Rblock_editor_*`: the program saved on each change and loaded when the app's differs.
- [ ] `BlockKind`, `BlockShape`, slot options and visibility, diffed by name.
- [ ] `BlockEditorState`: `scripts()` and `Block`, `glow`, `select`, `setView`, `view()`.
- [ ] Headless tests: load and read back, a user's change reported, a refused change loaded back, the snapshot, glow, kinds added and relabelled.
- [ ] The XML translator's `<BlockEditor>` with `<Kind>` and `<Category>`.
- [ ] The spec's sections checked against what was built; `api-coverage.md` updated (the BlockEditor row and the numbers).
