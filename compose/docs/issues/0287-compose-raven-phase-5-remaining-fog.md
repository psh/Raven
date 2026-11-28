---
status: open
wayfinder: map
---

# compose-raven phase 5 remaining fog

## Destination

The fog left by [compose-raven phase 5 spec](maps/0204-compose-raven-phase-5-spec.md#not-yet-specified)
and [compose-raven phase 5 widgets](maps/0241-compose-raven-phase-5-widgets.md#not-yet-specified),
designed into `compose/docs/spec.md` as phase 5 **batches** eleven to eighteen, each with its
implementation tickets filed in the tracker, ready to build in this order: gestures and drag,
popups and motion, lists at scale continued, windows, layout and direction, touch and pen, widget
extras, opt-in modules. Patches that turn out not to be worth building are ruled out of scope
instead. This map files the implementation tickets; it doesn't build them. Charted on 2026-11-27
from those two maps' fog; phase 5 is then wholly designed.

| Batch | Patches | Source |
|---|---|---|
| 11. Gestures and drag | coroutine `pointerInput` and drag gestures; drag and drop inside the app; nested scroll | 0204 |
| 12. Popups and motion | `Popup`; exit transitions for `AnimatedVisibility` | 0204 |
| 13. Lists at scale, continued | lazy list extras; lazy pages for the pagers; lazy grids; Table extras | 0204, 0241 |
| 14. Windows | tests of several windows; torn-off panels as Windows; Navigation 3 scenes | 0204 |
| 15. Layout and direction | custom `Layout { }`; right-to-left and localisation | 0204 |
| 16. Touch and pen | `raven_touch.h` | 0204 |
| 17. Widget extras | Plot extras; search in a PDF; web view extras; NodeGraph's view observed; printing's result | 0241 |
| 18. Opt-in modules | `compose-raven-lottie`; graphics extras | 0204 |

## Notes

- Domain: the Compose runtime over Raven's retained widget tree, as before. Vocabulary is in
  `compose/GLOSSARY.md` (**Phase**, **Batch**, **Port gap**); keep it current as terms resolve.
- Skills: grilling tickets call `grilling` + `domain-modeling`; call `codebase-design` for anything
  that changes the binding layer (gestures, lazy layouts, custom `Layout { }`, layout direction).
- Standing preference: **keep it slim.** The smallest design that works; extras go to fog or out
  of scope. A patch no sample or test app needs may be ruled out of scope rather than designed.
- Standing preference: **favour Compose conventions and ergonomics.** Names, parameter shapes,
  defaults and behaviour follow Compose (and well-known Compose libraries where Compose has none);
  where Raven differs, compose-raven adapts, and section 12 records the difference.
- **Raven changes are allowed** when small: one commit each on `compose-raven` with a C test, the
  bindings regenerated, an entry in spec section 11, and an on-hold upstream ticket.
- **Implementation tickets**: ordinary tracker tickets (no `wayfinder:` field), each linking its
  spec section, with Deliverables. They are built **in sequence** after phase 5's last so far,
  [samples/blocks](0286-samples-blocks.md): each batch's first implementation ticket names the
  previous batch's last in its **Depends on:** line. Decision tickets have blocking edges only
  within their batch, so the design can run ahead of the build.
- **Each batch is proved** by a sample port or a test app where one fits, ticketed with the batch,
  or by headless tests where none does.
- Keep [`api-coverage.md`](../api-coverage.md) in step as each batch's tickets are filed and built.
- Nothing is released beyond the local machine: research branches stay local and unpushed.

## Decisions so far

<!-- the index: one line per closed ticket -->

## Not yet specified

- **Touch and pen's design**: the shape of touch and stylus input in Compose terms (pointer types,
  pressure, tilt, multi-touch gestures such as `detectTransformGestures`), once
  [Touch and pen: what Raven offers and how Compose shapes it](0302-touch-and-pen-what-raven-offers-and-how-compose-shapes-it.md)
  reports; it may also reshape batch eleven's pointer events.
- **Graphics extras' Raven side**: the queue lock for Raven's own Vulkan device and a public redraw
  for a bitmap whose Vulkan image changed may need Raven changes, which
  [Opt-in modules: graphics extras](0309-opt-in-modules-graphics-extras.md) will show.

## Out of scope

- **App bundling** (a macOS `.app` with MoltenVK, Linux packages): it goes with the release, phase
  4, on hold while nothing is released beyond the local machine.
- **raven_web's `WebView` and the `browser` port**: SystemWebView covers web pages in the window
  ([compose-raven phase 5 widgets](maps/0241-compose-raven-phase-5-widgets.md#out-of-scope)).
- **Work already ticketed outside the maps**:
  [Raven: a bar chart widget, proposed upstream](on-hold/0258-raven-a-bar-chart-widget-proposed-upstream.md),
  [Vector map: features from upstream's MapView](0271-vector-map-features-from-upstreams-mapview.md)
  and [samples/git-history](0268-samples-git-history.md).
