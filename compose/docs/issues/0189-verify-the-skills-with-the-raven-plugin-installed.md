---
status: open
---

# Verify the skills with the raven plugin installed

[Verify the skills on Raven's samples](archive/0182-verify-the-skills-on-samples.md) read the `raven` skill
by hand. This runs the same six skills (`diagnosing-bugs`, `pr`, `prototype`, `tdd`, `triage`,
`implement`) on `examples/notes` in a fresh session with the plugin installed from the repository's
root, to show that an agent finds the skill through its description, with nothing pointing at it.
Asked for on 2026-11-24.

**Depends on:** [Verify the skills on Raven's samples](archive/0182-verify-the-skills-on-samples.md).

## Deliverables

- [ ] The plugin installed from the local marketplace (`.claude-plugin/`), noted as the steps a user takes.
- [ ] Each skill run in C, Python and Rust in a new session, noting whether the `raven` skill loaded unprompted and at which step.
- [ ] Where it didn't load, the description or the one-line `CLAUDE.md` pointer fixed, and run again.
- [ ] The table in `agent-skills.md` updated with the result.
