---
status: open
labels: [bug, ready-for-agent]
---

# Blank notes from an agent

An agent connected to the notes example (`RAVEN_MCP=1`) can add notes that look empty: `notes.add`
trims the space character only, so a tab, a newline or a no-break space each become a note. Found
by the `triage` run of
[Verify the skills with the Python and Rust MCP wrappers](archive/0190-verify-the-skills-with-the-mcp-wrappers.md)
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
- [ ] The headless UI tests in C, Python and Rust add a note of a tab plus spaces through the window and find it not added.
- [ ] The MCP notes tests in Python and Rust call `notes.add` with "\t", " \n " and " ", get the blank error each time, and `notes.list` is unchanged.
- [ ] "  water plants\t" is added as "water plants" in all three.
- [ ] The existing notes tests still pass.

**Out of scope:**
- Collapsing whitespace inside a note, length limits, and duplicate notes.
- The C app's MCP tools (it has none).
