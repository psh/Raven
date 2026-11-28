# Issue/Ticket Format

Tickets for compose-raven live here, in `compose/docs/issues/`, numbered sequentially:
`0001-slug.md`, `0002-slug.md`, and so on. To pick the next number, scan this directory **and
`archive/`, `on-hold/` and `maps/`** for the highest existing one and add one; never guess or reuse
a remembered number.

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
- `on-hold`: parked until something outside the work changes (for now, releasing beyond the local
  machine); the ticket says why in an `> **On hold (date).**` line under its title

## Where tickets live

This directory holds the tickets still to do, and the maps still open. Three folders beside it keep
it easy to scan, each ticket keeping its filename when it moves:

- **`archive/`**: finished tickets other than maps, with `status: closed` or `status: wont-fix`.
- **`on-hold/`**: tickets with `status: on-hold`. A ticket put on hold moves there; one taken off
  hold gets `status: open` again, loses its on-hold line, and moves back here.
- **`maps/`**: wayfinder maps (`wayfinder: map`) once they are closed. They are never archived and
  stay there permanently as tables of contents. A map still open stays here.

A ticket only partly on hold (one deliverable waiting, the rest workable) stays here, `open`, and
says which part waits.

## Archive

A ticket that binds Raven API, or finds a gap in what is bound, also updates
[`../api-coverage.md`](../api-coverage.md) when it closes: its row's status and link, and the numbers
from `compose/tools/api-coverage.sh`. Its archive link there is fixed with the others below.

When a ticket other than a map reaches `closed` or `wont-fix`, move it into `archive/` with
`git mv`; when a map closes, into `maps/`; when a ticket goes on hold, into `on-hold/` (and back
here when it comes off hold). Keep the same filename, and fix the links in both directions:

- **Links to the moved ticket.** Every file that links to it needs its relative link pointed at
  the new folder. That is most often the parent map's `## Decisions so far`, but also check
  `compose/docs/spec.md`, `compose/docs/roadmap.md`, `compose/docs/api-coverage.md` and other
  tickets, in every folder.
- **The moved ticket's own links.** From one of the folders, a link to something in this directory
  gains a `../` prefix, a link to `compose/docs/` gains one more (`../spec.md` becomes
  `../../spec.md`), and a link into another folder goes through `../` (`../maps/0204-...md`). A link
  within the same folder needs no change.

Don't infer a ticket's location from its number. Look up the actual path, here or in `archive/`,
`on-hold/` or `maps/`.
`blocked_by` lists numbers, not paths, so a blocker that has been archived is closed by
definition.

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
one line to the map's `## Decisions so far` linking back to the ticket. A map that closes moves to
`maps/`.
