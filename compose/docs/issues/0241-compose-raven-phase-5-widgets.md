---
status: open
wayfinder: map
---

# compose-raven phase 5 widgets

## Destination

The widgets [Raven API coverage](../api-coverage.md#widgets-with-no-composable) lists with no
composable and no design yet, designed into `compose/docs/spec.md` as phase 5 **batches** five to
nine, each with its implementation tickets filed in the tracker, its sample port among them, ready
to build in this order: overlays and pagers, charts, documents and the web, maps, specialist
widgets. This map files the implementation tickets; it doesn't build them. Charted on 2026-11-26
from that table, after merging upstream's `main`, which added six of the widgets. The seven the
[compose-raven phase 5 spec](0204-compose-raven-phase-5-spec.md) map already designed (Table,
TileView, DatePicker, TimePicker, Rating, Pagination, Breadcrumb) aren't part of it.

| Batch | Widgets | Proved by |
|---|---|---|
| 5. Overlays and pagers | Toast, Carousel | the `carousel` port; a test app for Toast |
| 6. Charts | Plot, Chart | the `plot` and `charts` ports |
| 7. Documents and the web | PdfView, DiffView, SystemWebView | the `pdf`, `diff_view` and `web_view` ports |
| 8. Maps | upstream's raster MapView, beside the vector map | the `map` port |
| 9. Specialist widgets | NodeGraph, Timeline, BlockEditor | the `studio` port; a test app for BlockEditor |

## Notes

- Domain: the Compose runtime over Raven's retained widget tree, as before. Vocabulary is in
  `compose/GLOSSARY.md` (**Phase**, **Batch**, **Port gap**); keep it current as terms resolve.
- Skills: grilling tickets call `grilling` + `domain-modeling`; call `codebase-design` for anything
  that changes the binding layer.
- Standing preference: **keep it slim.** The smallest design that works; extras go to fog or out
  of scope.
- Standing preference: **favour Compose conventions and ergonomics.** Names, parameter shapes,
  defaults and behaviour follow Compose (and well-known Compose libraries where Compose has none);
  where Raven differs, compose-raven adapts, and section 12 records the difference.
- **Raven changes are allowed** when small: one commit each on `compose-raven` with a C test, the
  bindings regenerated, an entry in spec section 11, and an on-hold upstream ticket.
- **Implementation tickets**: ordinary tracker tickets (no `wayfinder:` field), each linking its
  spec section, with Deliverables. They are built **in sequence** after phase 5's last,
  [Lists at scale: TreeView expansion and revealItem](0238-lists-at-scale-treeview-expansion-and-revealitem.md):
  each batch's first implementation ticket names the previous batch's last in its **Depends on:**
  line. Decision tickets have blocking edges only within their batch, so the design can run ahead
  of the build.
- **Each batch is proved by its sample port**, ticketed with the batch, its port gaps named.
- Keep [`api-coverage.md`](../api-coverage.md) in step as each batch's tickets are filed and built:
  its widget table's Status column.
- Nothing is released beyond the local machine: research branches stay local and unpushed.

## Decisions so far

## Not yet specified

- **Specialist widgets (batch 9)**: NodeGraph, Timeline and BlockEditor; how a graph's nodes and
  edges, or a timeline's tracks and clips, live in Compose state, and which of their many
  callbacks an app needs. `studio` uses NodeGraph and Timeline; no example uses BlockEditor. Its
  questions hang on how batches 5 to 8 settle callback-heavy widgets; they graduate once those
  are decided.
- **Printing, and PDF writing with it**: what Kotlin exposes once
  [Printing: Raven's printing and how Kotlin should expose it](0251-printing-ravens-printing-and-how-kotlin-should-expose-it.md)
  reports. If printing needs PDF writing (`Rpdf_document_*`), the two are designed together as one
  batch of their own; if not, printing joins a batch on its own and PDF writing is ruled out of
  scope then.

## Out of scope

- **raven_web's `WebView` and the `browser` port**: a separate optional library that drives an
  installed browser; SystemWebView covers web pages in the window. The roadmap's "WebView" for
  `browser` is SystemWebView now.
