---
status: closed
wayfinder: task
parent: 0073
blocked_by: [0074, 0075, 0076, 0077, 0078, 0083, 0084]
assignee: Paul Hawke
---

# Sample tickets reflect every gap decision

## Question

Nothing to decide: once the gap tickets close, make the sample tickets, the spec and the roadmap
agree with them. Each sample ticket's Gaps section lists only port gaps, each naming the phase
(and ticket or spec section) that completes it; the "brought forward or left out" deliverable is
removed; work brought into phase 2 is a deliverable of the sample ticket or of a phase 2 ticket it
depends on, with **Depends on** lines updated. Spec section 10 states the port rule settled when
charting the map, and `roadmap.md`'s later phases list every gap they complete.

## Resolution

Done on 2026-11-11:

- The four sample tickets list only port gaps, each naming its phase: `notepad`'s dialog-flow
  tests (phase 3), `components`' card transitions (phase 3), `themes`' `ListView` and Markdown
  labels (phase 3) and `ColorPicker` (phase 4), and none for `overlay`. Parts with no Compose
  counterpart are marked as no gap. The "brought forward or left out" deliverable is gone from all
  four, and their **Depends on** lines name the phase 2 tickets the map added: Native message and
  file dialogs, CodeEditorState, completions, markers and languages, Files and text dropped on a
  window, and Window minimum size and DpSize.
- Spec section 10 states the port rule: the C example's look, the behaviour the phase covers, and
  port gaps listed with their phase; parts with no counterpart are not gaps.
- `roadmap.md`'s phase 2 section says the map settled every gap, and its phase 3 and later
  sections list every gap they complete.
- The map's fog patch cleared with no new ticket: the samples' tests need only finders and actions
  phase 2 tickets already plan.
