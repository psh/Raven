---
status: closed
---

# Verify the skills with the Python and Rust MCP wrappers

[Verify the skills on Raven's samples](0182-verify-the-skills-on-samples.md) drove the running
Python and Rust apps through the raw `Rmcp_*` calls, since
[Python and Rust MCP wrappers](0180-python-and-rust-mcp-wrappers.md) was still open. Once it closes,
the steps that use Raven MCP (`diagnosing-bugs` and `triage`, reproducing in the running app) are
run again in Python and Rust through `raven_mcp` (Python's module, and Rust's crate `raven-gui-mcp`), and the `raven` skill's language
files are checked against them. Asked for on 2026-11-24.

**Depends on:** [Python and Rust MCP wrappers](0180-python-and-rust-mcp-wrappers.md),
[Verify the skills on Raven's samples](0182-verify-the-skills-on-samples.md).

## Deliverables

- [x] `diagnosing-bugs` and `triage` run again on `examples/notes` in Python and Rust, with the wrappers.
- [x] `python.md` and `rust.md` (through `tools/bindgen/skill/`) show the wrappers where they now show raw calls.
- [x] The table in `agent-skills.md` updated if anything changed.

## The runs (2026-11-24 to 25)

Run in a worktree on `verify/0190-skills-runs`, with the guidance below in place first, so the runs
followed the new language files. The apps were served by the wrappers: `notes_app.py` with
`raven_mcp`, and the Rust example in `bindings/rust/mcp` with `raven-gui-mcp`.

- **`diagnosing-bugs`** (compatible in both): a planted bug in its own commit. The window's
  `notes.add` deleted the selected note, and the user's symptom was all that was given.
  - The loop: the user's steps through `ui.*`, then the app's own `notes.list`, on one connection
    to each running app, red in both. Headless scratch tests in each language, red 3/3, made the
    tight loop.
  - Minimising compared paths. The agent's `notes.add` tool kept the selected note, which put the
    cause in the window's action and not in the add they share. Without a selection it was green.
  - Four hypotheses. One tagged probe and an Add-button variant settled them.
  - Regression check in `test_notes.py` and `tests/notes.rs`, red then green, and both running
    apps green again. The check ("adding a note keeps the one selected") was kept on
    `compose-raven`; the planted bug and its fix were not.
- **`triage`** (compatible in both): a real report, not planted: "my agent added blank notes". It
  was confirmed on both running apps through the `notes.add` tool, which is the reporter's own
  path. A tab, a newline or a no-break space each became a note, because notes are trimmed of `' '`
  only, in C too. Paul agreed bug and `ready-for-agent`; the brief is
  [Blank notes from an agent](../0192-blank-notes-from-an-agent.md).

**Guidance changed:**

- `python.md` and `rust.md` serve the `ui.*` tools through the wrappers
  (`start_from_environment`, `stop`) and say in a line how the app's own tools are written. That
  replaces about ten raw lines each.
- Writing the Python snippet turned up a flaw in `raven_mcp`: `start_from_environment()` and
  `stop()` loaded the Raven MCP library even when `RAVEN_MCP` wasn't set, so a plain app raised
  `OSError` where the library is missing. Both now leave it alone until `RAVEN_MCP=1` or a tool
  asks, with a test that holds without the library.

**Noticed, not changed:**

- Python's and Rust's JSON replies differ in spacing (`["a", "b"]` and `["a","b"]`), so a script
  compares parsed JSON.
- Typing appends, already a gotcha in the skill, tripped the new regression check once. The
  driver's message ("no accessible with role=list-item text=\"x\"") made it a one-minute fix.

Closed on 2026-11-25: both skills stay compatible with the wrappers.
