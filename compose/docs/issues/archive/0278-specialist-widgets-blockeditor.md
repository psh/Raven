---
status: closed
wayfinder: grilling
parent: 0241
blocked_by: []
assignee: claude
---

# Specialist widgets: BlockEditor

## Question

How is Raven's BlockEditor (`Rblock_editor_*`, 36 functions: a document of blocks the user edits,
reorders and converts) bound? Decide whether the document is a controlled value (a list of blocks
the app holds, each edit a proposal) or a state object Raven edits and the app reads, how blocks
are keyed and typed, and what an app reads back (the text, a serialised form). No example uses it:
decide the test app that proves it. The answer is the spec section and its implementation tickets.

## Resolution

Resolved on 2026-11-27, with the user, in two rounds: designed into
[spec section 8: BlockEditor](../../spec.md#blockeditor-phase-5), section 10's
[samples/blocks](../../spec.md#samplesblocks-phase-5) and section 12.

The question took BlockEditor for a document of text blocks; it is Raven's Scratch-style editor of
programs made of blocks.

- **The program controlled as text**, as `CodeEditor`'s is: Raven's saved form, read on each change
  and loaded when the app's differs, since Raven reports only that something changed.
- **`BlockEditorState`**: a read-only snapshot of the scripts for running them, `glow`, `select`,
  the view; `onScriptClick` and `onContextMenu`.
- **Kinds as a list** in Raven's label syntax, with category colours and the palette.
- **`samples/blocks`**, a turtle of compose-raven's own, proves it with the headless tests;
  undeletable blocks left out.

Filed as [Specialist widgets: BlockEditor, the composable](../0285-specialist-widgets-blockeditor-the-composable.md)
and [samples/blocks](../0286-samples-blocks.md).
