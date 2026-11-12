---
status: open
wayfinder: map
---

# compose-raven phase 2 sample gaps

## Destination

Every [port gap](../../GLOSSARY.md) listed on the phase 2 sample tickets
([samples/notepad](0069-samples-notepad.md), [samples/components](0070-samples-components.md),
[samples/themes](0071-samples-themes.md), [samples/overlay](0072-samples-overlay.md)) is settled:
either decided into phase 2 (written into `compose/docs/spec.md` and owned by a phase 2 ticket), or
left as a port gap that names the phase completing it, with that phase's part of the spec or
roadmap listing it. The sample tickets then lose their "brought forward or left out" deliverable,
so implementation starts with nothing open. This map plans; it builds nothing.

## Notes

- **Phase 2 is a foundation a later phase completes.** Nothing is left out without a home: every
  port gap names the phase (and, where one exists, the ticket or spec section) that completes it.
  A gap that is "not in the spec" leaves this map either designed into a named phase, or written as
  a named patch of that phase's fog on `compose/docs/roadmap.md`. Never "later" with no owner.
- Settled when charting (2026-11-11):
  - Gaps already designed for phase 3 or later stay port gaps of that phase by default, except
    `notepad`'s file dialogs and discard prompt, which get their own ticket.
  - Spec section 10's rule becomes: a port has the C example's look and the behaviour its phase's
    vocabulary covers, and lists its port gaps with the phase that completes each.
- Domain: the Compose runtime over Raven's retained widget tree. Vocabulary is in
  `compose/GLOSSARY.md`; keep it current as terms resolve.
- Required reading for Raven questions: `docs/ARCHITECTURE.md`, `include/raven/` and the C example
  being ported in `examples/`.
- Skills: grilling tickets call `grilling` + `domain-modeling`.
- Standing preference: **keep it slim.** Lean towards the smallest design that works.
- Standing preference: **favour Compose conventions and ergonomics**, and document every difference
  from Compose in spec section 12.
- The decisions of [compose-raven spec](0001-compose-raven-spec.md) and
  [compose-raven phase 3 spec](0023-compose-raven-phase-3-spec.md) are settled and live in
  `compose/docs/spec.md`. Build on them, don't reopen them.
- Closing a ticket also updates `compose/docs/spec.md` and `compose/docs/roadmap.md`, and the
  sample tickets whose gaps it settled.
- Ticket conventions: `ISSUES-FORMAT.md` in this directory.

## Decisions so far

<!-- one line per closed ticket: [title](file): gist -->

## Not yet specified

- **The samples' headless tests and harness**: once the gaps settle, a sample's test may need
  finders or actions phase 2 doesn't plan yet (a menu item inside a header bar, a dropped file).
  Revisit when the gap tickets close.

## Out of scope

- Implementation of phase 2: the vocabulary and sample tickets do that.
- Port gaps of the phase 3 samples (`editor`, `workspace`, `music`, `animations`, `glass`): their
  own phase's planning.
- Mapping the phase 2 vocabulary in the XML translator: spec section 10 fixes its mapping to
  phase 1's.
