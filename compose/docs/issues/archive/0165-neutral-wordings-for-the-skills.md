---
status: closed
wayfinder: grilling
parent: 0157
blocked_by: []
assignee: Paul Hawke
---

# Neutral wordings for the skills

## Question

Which of the 12 web-only wordings found by the
[audit](0158-audit-the-skills-against-raven.md) should be proposed to mattpocock-skills, and
how?

The audit lists them, each with a neutral version, in its findings
(`research/audit-the-skills-against-raven`, section "Web-only wording a neutral wording would fix").
Its numbers 1, 2, 6 and 7 change what an agent does on Raven. The rest are cosmetic.

- **Which**: only the four that change behaviour, or all twelve?
- **How**: issues or pull requests on the skills' repository, one change or several. Or Raven's own
  guidance ([Where agents find Raven's guidance](0163-where-agents-find-ravens-guidance.md)) covers
  them, so nothing needs proposing.
- **What doesn't fit a neutral wording**: the logic branch of `prototype` lifts a JavaScript module
  into the code, which only holds for JavaScript projects.

## Resolution

Decided with Paul on 2026-11-18, and written into
[`compose/docs/agent-skills.md`](../../agent-skills.md#the-skills-themselves).

- **Nothing proposed now.** mattpocock-skills' `SCOPE.md` keeps an issue open only for an observed
  failure in a real session, and sends anything else to a project's own `CLAUDE.md`/`AGENTS.md` or
  a fork. The audit's wordings come from reading, so none clear that bar.
- **The `raven` skill covers the four that change behaviour.** Its translation table gains "curl a
  running dev server" → driving the running app, and "a route, `?variant=`, a switcher" →
  previews. The logic branch's "lift the module" was already covered (port by hand).
- **Propose only from observed failures**: the fog's final pass, verifying each skill on a sample,
  records any session where a skill goes wrong on Raven despite the guidance, and files that as an
  issue on `mattpocock/skills` with its evidence.
- **The eight cosmetic wordings** aren't tracked; they stay in the audit's findings.
