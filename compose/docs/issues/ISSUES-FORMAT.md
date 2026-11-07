# Issue/Ticket Format

Tickets for compose-raven live here, in `compose/docs/issues/`, numbered sequentially:
`0001-slug.md`, `0002-slug.md`, and so on. To pick the next number, scan this directory **and
`archive/`** for the highest existing one and add one; never guess or reuse a remembered number.

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

## Archive

`archive/` holds finished tickets, meaning those with `status: closed` or `status: wont-fix`, so
that this directory stays easy to scan. **Wayfinder maps (`wayfinder: map`) are never
archived**, closed or not. They stay here permanently as tables of contents, even after every
child ticket has moved.

When a ticket other than a map reaches `closed` or `wont-fix`, move it into `archive/` with
`git mv`, keeping the same filename, and fix the links in both directions:

- **Links to the moved ticket.** Every file that links to it needs its relative link pointed into
  `archive/`. That is most often the parent map's `## Decisions so far`, but also check
  `compose/docs/spec.md`, `compose/docs/roadmap.md` and other tickets. A file that is itself in
  `archive/` needs no change for this.
- **The moved ticket's own links.** A link to something still in this directory gains a `../`
  prefix, and a link to `compose/docs/` gains one more (`../spec.md` becomes `../../spec.md`). A
  link to another archived ticket needs no change.

Don't infer a ticket's location from its number. Look up the actual path, here or in `archive/`.
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
one line to the map's `## Decisions so far` linking back to the ticket.
