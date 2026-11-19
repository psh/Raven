---
status: open
---

# Verify the skills on Raven's samples

The proof the skills map asked for: run every skill in mattpocock-skills' `engineering/` and
`productivity/` folders that needs the stack against a Raven sample, in C, Python and Rust, with the
`raven` skill installed, and record what happens. Design:
[The skills themselves](../agent-skills.md#the-skills-themselves); the skills audited in
[Audit the skills against Raven](archive/0158-audit-the-skills-against-raven.md).

**Depends on:** [The raven agent skill](0181-the-raven-agent-skill.md),
[Raven's actions as tools](archive/0177-ravens-actions-as-tools.md),
[Python and Rust MCP wrappers](0180-python-and-rust-mcp-wrappers.md).

## Deliverables

- [ ] `diagnosing-bugs`, `pr`, `prototype`, `tdd`, `triage` and `implement` run on the notepad in each verified language, each with a note of what it did.
- [ ] A skill that goes wrong despite the guidance: the guidance fixed where it can be, else an issue on `mattpocock/skills` with the session's evidence, judged against its `SCOPE.md`.
- [ ] A table in `agent-skills.md`: each skill and language, compatible or not.
