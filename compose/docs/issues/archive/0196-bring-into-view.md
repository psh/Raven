---
status: closed
---

# Bring into view

An app can't scroll a widget into view; `Rwidget_scroll_into_view` is called only by the test
harness's `performScrollTo()` ([Scroll position](0121-scroll-position.md)). Expose it the
Compose way, with `BringIntoViewRequester`. One of the everyday features in the [Raven API coverage review](https://claude.ai/artifact/J56iXnntbqm8dmAUXWAEeh) (2026-11-14).

## Deliverables

- [x] `BringIntoViewRequester` and `Modifier.bringIntoViewRequester`; `bringIntoView()` calls `Rwidget_scroll_into_view`.
- [x] Tests: a widget below a scroll box's view is scrolled into it.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 583 tests, 6 of them in foundation's new
`BringIntoViewTest`.

- **`BringIntoViewRequester`**, in `raven.compose.foundation.relocation` as Compose's is: a sealed
  interface made by `BringIntoViewRequester()`, with `suspend bringIntoView()`, and
  `Modifier.bringIntoViewRequester(it)`. It calls `Rwidget_scroll_into_view` on each widget it is
  attached to, which lays the window out if it needs it and scrolls every ScrollBox around the
  widget, innermost first, along its own axis. So it reaches `VerticalScroll`, `HorizontalScroll`
  and the lazy lists and grid built on them; the `ScrollState`s and `LazyListState`s read the
  position on the next frame. It jumps, as `animateScrollTo` does, and returns at once.
- **No `rect`**: compose-raven has no `Rect`, and Raven brings the whole widget. Section 12 says so.
- **`WidgetAttachedElement`**, a new internal interface in ui's `Layout.kt`: a chain element told
  `attach(widget)` and `detach(widget)` by the node, so a requester can live in foundation.
  `FocusRequester` and `TransitionRequester` keep their own fields; they could move to it later.
- **Tests**: a widget below the view scrolls just into it, one above back to the top, one in view
  doesn't move; nested scrolls each move on their axis; a `LazyColumn` item; a requester attached
  to nothing, before composition and after its widget leaves, does nothing. The nested test gives
  the outer column room for its scroll bar: a `VerticalScroll` sized by its widest child takes the
  bar's 10 from it, which squeezed the 100-wide row to 90.
- **Spec**: a bullet in section 8's Scroll position and a row in section 12.
