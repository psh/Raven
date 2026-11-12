---
status: closed
---

# MenuBar(native)

`MenuBar` gains `native: Boolean = false`. Raven 0.2.3 shows a window's first MenuBar in the macOS
system menu bar by default; [Menus and KeyShortcut](0061-menus-and-keyshortcut.md) turned
that off so menus are drawn in the window everywhere, and the app now chooses. Spec:
[8: menus and the header bar](../../spec.md#phase-2-menus-and-the-header-bar), section 12's MenuBar
row. Decided on 2026-11-12, when the menus ticket reported the macOS default.

**Depends on:** nothing open.

## Deliverables

- [x] `MenuBar(modifier, native: Boolean = false) { }`: `Rmenu_bar_set_native(bar, native)`, so
      `native = true` keeps Raven's default (the system menu bar on macOS, in the window
      elsewhere) and `false` keeps the bar in the window everywhere. A change takes effect at once.
- [x] The harness's menu actions still work for a bar with `native = true` (on a headless host
      Raven draws it in the window, or the test notes what it can't reach).
- [x] Tests: the flag reaches Raven (`Rmenu_bar_is_native` or its equivalent) both ways and
      after a change.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 194 tests, 4 of them new in foundation's
`MenusTest`.

- **`MenuBar(modifier, native = false) { }`**, in `Menus.kt`: the bar binds `native` through a
  `Native` property calling `Rmenu_bar_set_native`, in place of the fixed `InWindow` one that
  always passed `R_FALSE`. A change is applied by the recomposition that makes it, and Raven gives
  the bar to the system, or takes it back, at its next frame. The KDoc says what `true` does on
  macOS and elsewhere, and that the default differs from Raven's.
- **Testing the flag**: `Rmenu_bar_is_native` reports whether the system shows the bar, not what
  was asked, and the headless host offers no system menu bar unless `RAVEN_NULL_SYSTEM_MENU_BAR=1`
  is set when Raven starts, as Raven's own `tests/test_widgets.c` does. The tests that read the
  flag set it around `runRavenTest` with a `withSystemMenuBar` helper in `MenusTest`.
- **Harness**: unchanged. On the plain headless host a `native = true` bar is drawn in the window
  and `performClick()` works on its items as before. When the system shows the bar, it takes no
  room in the window, but `Rmenu_open` still opens its menus there, so `performClick()` and
  shortcuts still reach the items.
- **Tests**:
  - the default bar stays in the window even when the system offers its bar;
  - a `native = true` bar is given to the system;
  - switching `native` takes effect at once, both ways;
  - a `native = true` bar's item runs from a click and from its shortcut, both on the plain
    headless host (drawn in the window) and when the system shows the bar.
