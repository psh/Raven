---
status: open
---

# compose-raven in the raven skill: compose.md, and renderPreview

The `raven` skill ([The raven agent skill](archive/0181-the-raven-agent-skill.md)) covers C and main Raven's
12 bindings. Kotlin/Native reaches Raven only through compose-raven, which the design left for later
("a `compose.md` can join later", [Guidance for agents](../agent-skills.md#guidance-for-agents)).
This ticket adds it. Asked for on 2026-11-24, and kept apart from 0181.

**Depends on:** [The raven agent skill](archive/0181-the-raven-agent-skill.md).

## Deliverables

- [ ] `skills/raven/languages/compose.md`, hand-written, since bindgen doesn't generate compose-raven.
  It covers: `runRavenTest` and `FoundNode` (compose-raven-ui-test); `McpTool` and compose-raven's
  `ui.*` tools; XML variants through `raven_preview`, then folded in by `raven-xml2compose`; and
  `tag=` in selectors.
- [ ] `renderPreview` in `compose-raven-test`, as [Previews](../agent-skills.md#compose-raven)
  designs it. It needs `RavenHost` to offer headless Vulkan, and skips with a message without it.
- [ ] `SKILL.md`'s list of language files names `compose.md`.

Verifying the skills on compose-raven's samples stays deferred, as
[0157](0157-raven-works-with-the-matt-pocock-skills.md) decided.
