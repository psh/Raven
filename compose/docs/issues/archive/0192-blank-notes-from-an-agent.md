---
status: closed
labels: [bug, ready-for-agent]
---

# Blank notes from an agent

An agent connected to the notes example (`RAVEN_MCP=1`) can add notes that look empty: `notes.add`
trims the space character only, so a tab, a newline or a no-break space each become a note. Found
by the `triage` run of
[Verify the skills with the Python and Rust MCP wrappers](0190-verify-the-skills-with-the-mcp-wrappers.md)
on 2026-11-25, from the report "my agent added blank notes".

## Agent Brief

**Category:** bug
**Summary:** A note that is only whitespace is refused, whatever the whitespace, in every language

**Current behavior:**
A note is trimmed of the space character (U+0020) only. Through the agent's `notes.add` tool, a
tab, a newline or a no-break space each become a note that looks empty, and the count goes up.
Verified on the running Python and Rust apps over Raven MCP. The C app has the same rule.

**Desired behavior:**
Adding a note trims all whitespace around it (spaces, tabs, newlines, Unicode spaces such as
U+00A0), from the window's Add and Enter and from the `notes.add` tool alike. A note left empty is
not added: the window adds nothing, and the tool fails with "a note can't be blank". Whitespace
inside a note stays as typed.

**Key interfaces:**
- The notes example's one add operation per language, which both the window's `notes.add` action and the `notes.add` tool use, so the rule lives in one place per language.
- The `notes.add` tool's reply and error texts stay as they are.

**Acceptance criteria:**
- [x] The headless UI tests in C, Python and Rust add a note of a tab plus spaces through the window and find it not added.
- [x] The MCP notes tests in Python and Rust call `notes.add` with "\t", " \n " and " ", get the blank error each time, and `notes.list` is unchanged.
- [x] "  water plants\t" is added as "water plants" in all three.
- [x] The existing notes tests still pass.

**Out of scope:**
- Collapsing whitespace inside a note, length limits, and duplicate notes.
- The C app's MCP tools (it has none).

## Resolution (2026-11-25)

Each language's one add operation trims Unicode's White_Space (the set Rust's `str::trim` uses),
so C, Python and Rust agree: `is_white_space` over UTF-8 in `notes_app.c`, `strip(WHITESPACE)` in
`notes_app.py` (not a bare `strip()`, which also drops U+001C to U+001F), `trim()` in
`notes_app.rs`. The tool's description says "the whitespace around it is dropped".

- **Changed from the brief:** typing drops a tab or a newline from a one-line field, so through
  the window the hole was the no-break space. The UI tests in C, Python and Rust type `" \u00a0 "`
  (not added) and `"  water plants\u00a0"` (added trimmed). The tab, the newline and the
  no-break space are the MCP tests' blanks, and the tools add `"  buy milk\t"` as `"buy milk"`.
- Red first in all five tests (the UI tests: 2 matches, expected 1; the tools: `added 'buy
  milk\t'`), green after. `pythonWrapperTest`, `rustTest`, `cTest` and the ctests pass.
