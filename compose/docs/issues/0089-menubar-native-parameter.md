---
status: open
---

# MenuBar(native)

`MenuBar` gains `native: Boolean = false`. Raven 0.2.3 shows a window's first MenuBar in the macOS
system menu bar by default; [Menus and KeyShortcut](archive/0061-menus-and-keyshortcut.md) turned
that off so menus are drawn in the window everywhere, and the app now chooses. Spec:
[8: menus and the header bar](../spec.md#phase-2-menus-and-the-header-bar), section 12's MenuBar
row. Decided on 2026-11-12, when the menus ticket reported the macOS default.

**Depends on:** nothing open.

## Deliverables

- [ ] `MenuBar(modifier, native: Boolean = false) { }`: `Rmenu_bar_set_native(bar, native)`, so
      `native = true` keeps Raven's default (the system menu bar on macOS, in the window
      elsewhere) and `false` keeps the bar in the window everywhere. A change takes effect at once.
- [ ] The harness's menu actions still work for a bar with `native = true` (on a headless host
      Raven draws it in the window, or the test notes what it can't reach).
- [ ] Tests: the flag reaches Raven (`Rmenu_bar_is_native` or its equivalent) both ways and
      after a change.
