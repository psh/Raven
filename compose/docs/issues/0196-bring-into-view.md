---
status: open
---

# Bring into view

An app can't scroll a widget into view; `Rwidget_scroll_into_view` is called only by the test
harness's `performScrollTo()` ([Scroll position](archive/0121-scroll-position.md)). Expose it the
Compose way, with `BringIntoViewRequester`. One of the everyday features in the [Raven API coverage review](https://claude.ai/artifact/J56iXnntbqm8dmAUXWAEeh) (2026-11-14).

## Deliverables

- [ ] `BringIntoViewRequester` and `Modifier.bringIntoViewRequester`; `bringIntoView()` calls `Rwidget_scroll_into_view`.
- [ ] Tests: a widget below a scroll box's view is scrolled into it.
