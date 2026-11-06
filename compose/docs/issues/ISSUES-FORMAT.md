# Issue/Ticket Format

Tickets for compose-raven live here, in `compose/docs/issues/`, numbered sequentially:
`0001-slug.md`, `0002-slug.md`, and so on. To pick the next number, scan this directory for the
highest existing one and add one; never guess or reuse a remembered number.

## Template

```md
---
status: open
---

# {Short title of the work item}

{1-3 sentences: what needs to happen and why.}
```

## Status values

- `open`: not started
- `in-progress`: actively being worked
- `closed`: done
- `wont-fix`: decided against, keep the record

## Wayfinding operations

`/wayfinder` maps and their child tickets live here too, with extra frontmatter for what plain
markdown lacks (labels, parent/child, blocking, assignee).

```md
---
status: open
wayfinder: map | research | prototype | grilling | task
parent: NNNN             # child tickets only: the map's ticket number
blocked_by: [NNNN, NNNN] # child tickets only: [] if none
assignee: null           # null = unclaimed; set to the claiming dev when picked up
---

# {Title}

## Question           <!-- child tickets -->
## Destination        <!-- map only -->
## Notes              <!-- map only -->
## Decisions so far   <!-- map only -->
## Not yet specified  <!-- map only -->
## Out of scope       <!-- map only -->
```

**Frontier query**: files with `wayfinder:` set to a ticket type (not `map`), `status: open`,
`assignee: null`, and every id in `blocked_by` pointing at a ticket with `status: closed`.

**Claim**: set `assignee` (and `status: in-progress`) before starting work.

**Resolve**: append a `## Resolution` section with the answer, set `status: closed`, then add
one line to the map's `## Decisions so far` linking back to the ticket.
