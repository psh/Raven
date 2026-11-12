---
status: closed
---

# HeaderBar

A title bar that is part of the window's content, for the `notepad` port. The
`customHeader` window flag it needs moved to
[Window: WindowState and its parameters](archive/0086-window-windowstate-and-its-parameters.md). Spec:
[8: menus and the header bar](../spec.md#phase-2-menus-and-the-header-bar),
[4. Application host](../spec.md#4-application-host).

**Depends on:** [Window: WindowState and its parameters](archive/0086-window-windowstate-and-its-parameters.md) (`customHeader`).

## Deliverables

- [x] `HeaderBar(modifier, title, buttons: Set<WindowButton> = WindowButton.all()) { }` over
      HeaderBar, with `WindowButton` and `Rheader_bar_set_buttons`.
- [x] In `HeaderBarScope`, `Modifier.align(Alignment.End)` places a child on the right.
- [x] A `HeaderBar` in a window without `customHeader` logs a warning.
- [x] Tests: the title and buttons follow their parameters; a child aligned to the end sits on the
      right of one that isn't; the warning is logged.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 213 tests, 6 of them HeaderBar's.

- **Composable**: `HeaderBar(modifier, title: String? = null, buttons: Set<WindowButton> =
  WindowButton.all()) { }` is in `raven.compose.foundation`, over `R_WIDGET_HEADER_BAR`. `title`
  uses `Rheader_bar_set_title`, where `null` shows the window's title, and `buttons` folds
  `WindowButton` (`Close`, `Minimize`, `Maximize`) into `Rheader_bar_set_buttons`'s mask. Raven
  still hides the maximize button on a window that isn't resizable.
- **Placing children**: Raven's header bar packs its children from the left, and those whose slot
  is `R_HALIGN_RIGHT` from the right, each with `slot.fill` sharing out the space left. A new
  `MainAxis.Packed` tells `resolve` this: a child is a Row child centred vertically, except that
  `Modifier.align(Alignment.End)` (in `HeaderBarScope`) gives it `HAlign.End`. Raven can't centre a
  child there (the title is in the middle), so `Alignment.CenterHorizontally` is ignored with the
  one-time warning, and `fillMaxWidth` takes the space left as it does in a Row.
- **The warning**: `windowHasCustomHeader()`, internal API in `Window.kt`, reads the flags of
  `LocalRavenWindow`. A HeaderBar in a window without `customHeader` logs a warning once when it
  enters the composition, through `layoutWarningSink`, which became internal API so foundation and
  its tests can reach it. Window's own warnings already went there.
- **Tests**:
  - the title follows its parameter: a longer title widens the bar, `null` measures as the
    window's title does, and `""` as no title;
  - the buttons follow their parameter, the bar narrowing with fewer of them and widening again;
  - a child aligned to the end sits on the right of one that isn't, though composed before it;
  - a MenuBar in a HeaderBar runs its item;
  - a HeaderBar without `customHeader` logs the warning, and one with it logs none;
  - `LayoutTest`: a header bar child is packed at the start or the end, and centring warns.

  Raven has no getters for the title or the buttons, so the tests read them through the bar's
  desired width.
