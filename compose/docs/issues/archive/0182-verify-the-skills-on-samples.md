---
status: closed
---

# Verify the skills on Raven's samples

The proof the skills map asked for: run every skill in mattpocock-skills' `engineering/` and
`productivity/` folders that needs the stack against a Raven sample, in C, Python and Rust, with the
`raven` skill installed, and record what happens. Design:
[The skills themselves](../../agent-skills.md#the-skills-themselves); the skills audited in
[Audit the skills against Raven](0158-audit-the-skills-against-raven.md).

**Depends on:** [The raven agent skill](0181-the-raven-agent-skill.md),
[Raven's actions as tools](0177-ravens-actions-as-tools.md),
[Python and Rust MCP wrappers](../0180-python-and-rust-mcp-wrappers.md).

**Settled when started (2026-11-24):**

- The sample is `examples/notes`, the worked example in C, Python and Rust (`notes_app.c`, `.py`,
  `.rs`). `examples/notepad`, the text editor, is C only.
- It goes ahead without 0180: the six skills need only the `ui.*` tools, which the Raven MCP library
  serves to every binding through the raw `Rmcp_*` calls and `RAVEN_MCP_UI=1`. Running them again
  with the wrappers is [0190](../0190-verify-the-skills-with-the-mcp-wrappers.md).
- The `raven` skill is read by hand (`skills/raven/SKILL.md` and the language file), not installed.
  Whether an agent finds it through its description with the plugin installed is
  [0189](../0189-verify-the-skills-with-the-raven-plugin-installed.md).

## Deliverables

- [x] `diagnosing-bugs`, `pr`, `prototype`, `tdd`, `triage` and `implement` run on `examples/notes` in each verified language, each with a note of what it did.
- [x] A skill that goes wrong despite the guidance: the guidance fixed where it can be, else an issue on `mattpocock/skills` with the session's evidence, judged against its `SCOPE.md`.
- [x] A table in `agent-skills.md`: each skill and language, compatible or not.

## The runs (2026-11-24)

Run in a worktree on `verify/0182-skills-runs`, from `compose-raven` at `90fc3ff`. The variants are
on `prototype/0182-notes-variants`. The libraries were the shared libraven and the Kotlin-built
`libraven_mcp`. `triage` and `implement` can't be invoked by the model, so their `SKILL.md`s were
followed by hand. The skills are mattpocock-skills 1.3.1.

- **`tdd`**: the seam was agreed with Paul: the window, through each language's harness. Feature:
  a blank note isn't added, and a note is trimmed. Two slices in each language, each red then
  green. Slice 2 went red with spaces left over from slice 1, because typing appends at the caret.
- **`diagnosing-bugs`**: a planted bug in its own commit (the count shown before the delete),
  given as the user's symptom only. The loop: the user's steps on each running app through
  `ui.*`, with a small script as the MCP client, red in all three, and the headless tests, red
  3/3 per language in under a second. It was minimised with a scratch `raven_testing` script.
  Four hypotheses, one tagged probe, then a regression check at the window's seam, red then green
  in all three, and the running apps green again.
- **`triage`**: a scratch report, "the Delete key doesn't delete the selected note". The
  redundancy check found Raven's `<Shortcut>` unused in notes. The claim was confirmed on all
  three running apps with `ui.click` and `ui.key`. Paul agreed enhancement and `ready-for-agent`,
  and the brief followed `AGENT-BRIEF.md`.
- **`implement`**: built from the brief with `tdd`. The fix is one `<Shortcut>` in the shared XML,
  so Python and Rust were shown red with it taken out. Then `code-review`, with its two
  sub-agents; its four test findings were fixed. The commit is `7427557`. The full suite at the end
  caught a hand edit to the generated `Cargo.toml`, which was fixed in `gen_rust.py`.
- **`prototype`**: the UI branch on the existing window. Three structurally different XML variants
  were rendered by `raven_preview` in one command, and Paul chose A (compose below). It was folded
  into `notes.xml`, and every language's tests passed unchanged. The variants are on the
  throwaway branch.
- **`pr`**: a body with before and after previews. The populated window needs app code, so each
  shot came from a test with capture plus `Rwindow_save_preview`, and C and Rust were
  byte-identical.

**Guidance fixed** (no skill went wrong on its own side, so nothing goes upstream):

- `c.md`, `python.md` and `rust.md` show how to serve the `ui.*` tools. None of the notes apps
  served them, and the skill only said "an app wired with `Rmcp_config_from_environment`".
- `SKILL.md` covers connecting in the middle of a session (`claude mcp add` arrives with the next
  one), typing that appends, fixes in the XML that turn every language green at once, and
  previews of XML that run no app code.

**Found besides**, now [Small fixes the skill runs found](../0191-small-fixes-the-skill-runs-found.md):

- `raven_preview` exits 0 when the XML had an unknown attribute but the window still built.
- `raven::testing` panics at its own line, not the test's.
- C examples link the static libraven, which can't host Raven MCP.

The runs branch's improvements to notes landed on `compose-raven` as one commit (Paul,
2026-11-24): trimming, the Delete key, the new layout, Raven MCP in the three apps, and the Rust
`notes` example. The planted bug and its fix cancel out, so they don't appear.
