---
status: closed
wayfinder: map
---

# compose-raven phase 5 widgets

## Destination

The widgets [Raven API coverage](../../api-coverage.md#widgets-with-no-composable) lists with no
composable and no design yet, designed into `compose/docs/spec.md` as phase 5 **batches** five to
nine, and printing with PDF writing as batch ten, each with its implementation tickets filed in the tracker, its sample port among them, ready
to build in this order: overlays and pagers, charts, documents and the web, maps, specialist
widgets, printing and PDF documents. This map files the implementation tickets; it doesn't build them. Charted on 2026-11-26
from that table, after merging upstream's `main`, which added six of the widgets. The seven the
[compose-raven phase 5 spec](0204-compose-raven-phase-5-spec.md) map already designed (Table,
TileView, DatePicker, TimePicker, Rating, Pagination, Breadcrumb) aren't part of it.

| Batch | Widgets | Proved by |
|---|---|---|
| 5. Overlays and pagers | Toast, Carousel | the `carousel` port; a test app for Toast |
| 6. Charts | Plot, Chart | the `plot` and `charts` ports |
| 7. Documents and the web | PdfView, DiffView, SystemWebView | the `diff_view` and `web_view` ports; headless tests for PdfView |
| 8. Maps | upstream's raster MapView, beside the vector map | the `map` port |
| 9. Specialist widgets | NodeGraph, Timeline, BlockEditor | the `studio` port; a test app for BlockEditor |
| 10. Printing and PDF documents | no widget: printing, and the PDF writing it needs | the `pdf` port (it writes, saves and prints its invoice) |

Batch ten is built straight after batch eight; batch nine's tickets follow it, once filed.

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
  [Lists at scale: TreeView expansion and revealItem](../archive/0238-lists-at-scale-treeview-expansion-and-revealitem.md):
  each batch's first implementation ticket names the previous batch's last in its **Depends on:**
  line. Decision tickets have blocking edges only within their batch, so the design can run ahead
  of the build.
- **Each batch is proved by its sample port**, ticketed with the batch, its port gaps named.
- Keep [`api-coverage.md`](../../api-coverage.md) in step as each batch's tickets are filed and built:
  its widget table's Status column.
- Nothing is released beyond the local machine: research branches stay local and unpushed.

## Decisions so far

- [Overlays and pagers: Carousel](../archive/0242-overlays-and-pagers-carousel.md): `HorizontalPager` and `VerticalPager` with a small `PagerState`, every page composed, Raven's loop, dots, arrows and autoplay as parameters. Filed as [Overlays and pagers: HorizontalPager and VerticalPager](../archive/0253-overlays-and-pagers-horizontalpager-and-verticalpager.md) and [samples/carousel](../archive/0254-samples-carousel.md).
- [Overlays and pagers: Toast](../archive/0243-overlays-and-pagers-toast.md): Material 3's `SnackbarHost` and `SnackbarHostState.showSnackbar` over Raven's Toast, with its kinds and stacking, over a Raven change for message ids. Filed as [Raven: toast message ids](../0255-raven-toast-message-ids.md), [Propose toast message ids upstream](../on-hold/0256-propose-toast-message-ids-upstream.md) and [Overlays and pagers: SnackbarHost](../0257-overlays-and-pagers-snackbarhost.md).
- [Charts: one model for Plot and Chart](../archive/0245-charts-one-model-for-plot-and-chart.md): one composable per kind; Plot's series in a keyed DSL over arrays compared by reference, the view Raven's behind a `PlotState`; printf formats; a controlled slice selection; bars ruled out. Filed as [Charts: Plot and PlotState](../0259-charts-plot-and-plotstate.md), [samples/plot](../0260-samples-plot.md), [Charts: PieChart, Gauge, Heatmap and Sparkline](../0261-charts-piechart-gauge-heatmap-and-sparkline.md) and [samples/charts](../0262-samples-charts.md).
- [Documents: PdfView](../archive/0247-documents-pdfview.md): `PdfView(source, state, password)` with a saveable `PdfViewState` (page, zoom and fit observable, over a Raven zoom callback), no search; the `pdf` port moved to batch ten. Filed as [Raven: PDF view zoom callback](../0263-raven-pdf-view-zoom-callback.md), [Propose PDF view zoom callback upstream](../on-hold/0264-propose-pdf-view-zoom-callback-upstream.md) and [Documents: PdfView](../0265-documents-pdfview.md).
- [Documents: DiffView](../archive/0248-documents-diffview.md): `DiffView(content: DiffContent, ...)` (texts, files or a patch) with the options as parameters, notes as a list, and a `DiffViewState` for navigation; `unifiedDiff` and `countChanges`. Filed as [Documents: DiffView](../0266-documents-diffview.md) and [samples/diff-view](../0267-samples-diff-view.md).
- [The web: SystemWebView](../archive/0249-the-web-systemwebview.md): `WebView(state, onMessage)` with one `WebViewState` (observed values and commands), scripts as a suspend call that throws, raw messages, the same composable where there is no engine. Filed as [The web: WebView and WebViewState](../0269-the-web-webview-and-webviewstate.md) and [samples/web-view](../0270-samples-web-view.md).
- [Maps: raster tiles beside the vector map](../archive/0250-maps-raster-tiles-beside-the-vector-map.md): upstream's raster map is `MapView`, landing first; the fork's is renamed `VectorMapView` (and `Rvector_map_*` in C); one `TileSource` for both; markers, `animateTo` and coordinate conversion on `MapView`. Filed as [Raven: the vector map's own names](../0272-raven-the-vector-maps-own-names.md), [Maps: VectorMapView](../0273-maps-vectormapview.md), [Maps: MapView over upstream's raster map](../0274-maps-mapview-over-upstreams-raster-map.md) and [samples/map](../0275-samples-map.md).
- [Printing and PDF documents: what Kotlin exposes](../archive/0252-printing-and-pdf-documents-what-kotlin-exposes.md): print PDFs with a suspend `printPdf` and write them with `buildPdf { page { } }` returning bytes; `PdfViewState.print`; the `pdf` port without port gaps. Filed as [Printing and PDF documents: buildPdf and printPdf](../0279-printing-and-pdf-documents-buildpdf-and-printpdf.md) and [samples/pdf](../0280-samples-pdf.md); built after batch eight, before batch nine.
- [Specialist widgets: NodeGraph](../archive/0276-specialist-widgets-nodegraph.md): app-owned keyed nodes and links with controlled edits and selection, composable node content, the view Raven's behind a `NodeGraphState`; the rarer extras filed apart. Filed as [Specialist widgets: NodeGraph, the composable](../0281-specialist-widgets-nodegraph-the-composable.md) and [Specialist widgets: NodeGraph extras](../0282-specialist-widgets-nodegraph-extras.md).
- [Specialist widgets: Timeline](../archive/0277-specialist-widgets-timeline.md): app-owned keyed tracks and items with controlled edits, the playhead in a `TimelineState` Raven plays; the `studio` port. Filed as [Specialist widgets: Timeline, the composable](../0283-specialist-widgets-timeline-the-composable.md) and [samples/studio](../0284-samples-studio.md).
- [Specialist widgets: BlockEditor](../archive/0278-specialist-widgets-blockeditor.md): Raven's Scratch-style editor, its program controlled as text (Raven's saved form), a snapshot of the scripts for running them, kinds as a list; proved by a turtle of compose-raven's own. Filed as [Specialist widgets: BlockEditor, the composable](../0285-specialist-widgets-blockeditor-the-composable.md) and [samples/blocks](../0286-samples-blocks.md).
- [Printing: Raven's printing and how Kotlin should expose it](../archive/0251-printing-ravens-printing-and-how-kotlin-should-expose-it.md): Raven prints PDFs only, blocking on the UI thread; anything else must be written as a PDF first, so printing and PDF writing are batch ten together, decided by [Printing and PDF documents: what Kotlin exposes](../archive/0252-printing-and-pdf-documents-what-kotlin-exposes.md).
- [The web: how Compose web views shape their APIs](../archive/0246-the-web-how-compose-web-views-shape-their-apis.md): a state of observable fields plus a navigator is the common shape; Raven's SystemWebView has the events, better script results and one-way messages, but no interception and nothing drawn over it; nine trade-offs left for [The web: SystemWebView](../archive/0249-the-web-systemwebview.md).
- [Charts: how Compose chart libraries shape their APIs](../archive/0244-charts-how-compose-chart-libraries-shape-their-apis.md): lists of data classes are the norm, Vico's producer the exception; only Vico and KoalaPlot hoist pan and zoom; Raven's Plot streams by index and owns its view, and has no bar chart; seven trade-offs left for [Charts: one model for Plot and Chart](../archive/0245-charts-one-model-for-plot-and-chart.md).

## Not yet specified

Closed on 2026-11-27 with the destination reached: batches five to ten (overlays and pagers,
charts, documents and the web, maps, specialist widgets, printing and PDF documents) are designed
into `compose/docs/spec.md`, every decision ticket is archived, and their implementation tickets are
filed in sequence, from
[Overlays and pagers: HorizontalPager and VerticalPager](../archive/0253-overlays-and-pagers-horizontalpager-and-verticalpager.md)
to [samples/blocks](../0286-samples-blocks.md), batch ten built before batch nine. The patches below
are the fog that remains, moved on 2026-11-27 to
[compose-raven phase 5 remaining fog](../0287-compose-raven-phase-5-remaining-fog.md), which holds them now.

- **Lazy pages**: composing only the pages near the one shown, for pagers of many pages; today
  every page is composed ([Overlays and pagers: Carousel](../archive/0242-overlays-and-pagers-carousel.md)).
- **Plot extras**: appends detected, so live data isn't copied whole on each update; series
  visibility as controlled state rather than Raven's
  ([Charts: one model for Plot and Chart](../archive/0245-charts-one-model-for-plot-and-chart.md)).
- **Search in a PDF**: finding text and highlighting the matches, which needs a Raven change; today
  only a page's text is given ([Documents: PdfView](../archive/0247-documents-pdfview.md)).
- **Web view extras**: calls from the page that wait for a reply (a layer over
  `evaluateJavaScript`, no C change); intercepting a navigation (a Raven change crossing into
  Kotlin on the UI thread); menus and popups over the view (hiding it while one is open, or Raven
  compositing); the native view's handle from common Kotlin
  ([The web: SystemWebView](../archive/0249-the-web-systemwebview.md)).
- **Printing's result**: telling a cancelled print from a failed one, which needs each platform's
  print code to report it ([Printing and PDF documents: what Kotlin exposes](../archive/0252-printing-and-pdf-documents-what-kotlin-exposes.md)).
- **NodeGraph's view observed**: a Raven callback when the user pans or zooms, so
  `NodeGraphState`'s view can be state ([Specialist widgets: NodeGraph](../archive/0276-specialist-widgets-nodegraph.md)).

## Out of scope

- **raven_web's `WebView` and the `browser` port**: a separate optional library that drives an
  installed browser; SystemWebView covers web pages in the window. The roadmap's "WebView" for
  `browser` is SystemWebView now.
- **Bar and column charts**: Raven has no widget for them, so binding one would mean building a
  Raven widget first, beyond binding what Raven draws. That is
  [Raven: a bar chart widget, proposed upstream](../on-hold/0258-raven-a-bar-chart-widget-proposed-upstream.md),
  outside this map.
- **The vector map's catching up with upstream's** (markers, animated moves, coordinate
  conversion): an improvement to the fork's widget rather than a binding, filed as
  [Vector map: features from upstream's MapView](../0271-vector-map-features-from-upstreams-mapview.md).
