# Verify-first spikes

Throwaway checks of the facts listed under "0. Verify first" in
[`../docs/roadmap.md`](../docs/roadmap.md). Each one settles an assumption the spec makes about
Raven or about a dependency, before implementation starts. Results are dated 2026-11-06, run on
macOS (arm64) against Raven 0.2.1 at `8b1e7d4`, unless a row gives its own date.

| Spike | How to run | What it checks |
|---|---|---|
| [`raven/`](raven/) | `raven/run.sh` | Raven's own behaviour, headless (null backend, null platform) |
| [`deps/`](deps/) | `cd deps && gradle -q resolve` | that phase 3 dependencies resolve for `macosArm64`, `linuxX64` and `mingwX64` without Compose UI |
| [`nested-frames/`](nested-frames/) | `./gradlew :spikes:nested-frames:allTests`, from `compose/` | Compose inside the nested frames of Raven's blocking dialogs, headless, through `RavenHost` |

## Results

| Fact | Result | Evidence | Consequence |
|---|---|---|---|
| `Rinit`/`Rshutdown` can be repeated in one process | **Passes** | `raven/spikes.c`: three rounds with a window, widgets and frames; 0 blocks held after each `Rshutdown` | Fork change 2 is not needed. Tests start Raven fresh each time, as planned |
| `Rxml_document_parse` registers no `<Widget>` declarations | **Passes** | `raven/spikes.c`: `Rxml_is_widget_registered` is false after parsing, and the `<Widget>` stays an unexpanded node | `parseRavenXml` needs no clean-up |
| A `Separator` inserted into a Menu acts as a menu separator | **Fails as assumed, works another way** | `src/widgets/menu.c`: only a `MenuItem` with its `separator` flag is a separator. `raven/spikes.c`: `Rwidget_set_attribute(item, "Separator", "true")` works on a MenuItem, which can be inserted at an index | `Separator()` in `MenuScope` creates a MenuItem with the `Separator` attribute. No fork change |
| Raven derives hover and pressed shades from an overridden colour | **Fails** | `src/widget/theme.c`: `Rtheme_set_accent` derives `ACCENT_HOVERED`/`PRESSED` with per-look rules, but `Rwidget_set_color` (`src/widget/style.c`) sets only the one role | compose-raven sets the hovered and pressed roles itself, following `rvn_theme_set_accent`'s per-look rules |
| Raven scales explicit text sizes by the accessibility text scale | **Passes** | `src/render/font.c` multiplies every size by `rvn_a11y_text_scale()` in metrics, measuring and hit-testing | compose-raven passes sizes through and never scales them itself |
| Focusing the root clears focus, for `clearFocus()` | **Fails as assumed, works another way** | `src/widget/events.c`: `Rwidget_focus` ignores widgets that aren't focusable, so neither the root nor `R_INVALID_ID` clears focus. `raven/spikes.c`: turning the focused widget's focusable flag off and on clears focus (`Rwidget_set_focusable` calls `rvn_window_set_focus(window, NULL)`) | Fork change 5, `Rwindow_clear_focus`, is added for `clearFocus()`. The flag toggle is the stopgap until it lands |
| `animation-core` resolves without Compose UI | **Fails** | `deps/`: `androidx.compose.animation:animation-core:1.12.1` has no macOS or Windows variant, and on Linux it is a stub that needs `compose.ui` 1.10.0. JetBrains' `org.jetbrains.compose.animation:animation-core` depends on `ui`, `ui-graphics` and `ui-unit`, and has no Linux or Windows variant | compose-raven provides a small `animate*AsState` / `Animatable` set on its frame clock |
| `navigationevent` resolves without Compose UI | **Passes** | `deps/`: `navigation3-runtime` 1.2.1, `navigationevent` and `navigationevent-compose` 1.1.1 resolve for all three targets with no Compose UI | `NavDisplay` can use them. The rest of the Navigation 3 item needs the host |
| Compose recomposes safely inside the nested frames of Raven's blocking dialogs (checked 2026-11-11, Raven 0.2.3 at `c2f072d`) | **Passes** | `nested-frames/`: `Rmessage_box`, `Rdialog_run` and `Rfile_dialog_open`, each called from a coroutine on `RavenDispatcher`. While one waits, state writes recompose and apply in its frames and Text follows, a controlled Checkbox in another window reverts a refused click and keeps an accepted one, and every apply has exactly one `applyChanges` on the stack, never one inside another. Work queued behind the blocked coroutine and writes from another thread are composed within a millisecond, with animations on or off. Removing the dialog's window from the composition, or `Rquit`, ends the call with Cancel and leaks nothing | `native = true` stays in phase 3, with the [caveats below](#nested-frames-caveats) |
| Writing text back during an IME composition doesn't cancel it (checked 2026-11-11, from the source) | **Passes**: there is no composition for Raven to see | Raven's platform code: the macOS view reads `[event characters]` in `keyDown:` and doesn't implement `NSTextInputClient`; X11 creates its input context with `XIMPreeditNothing`, so the input method composes in its own window; Windows handles `WM_CHAR` and no `WM_IME_*`; Wayland has only xkbcommon compose sequences. Every backend reports committed text only | `TextField` writes back as soon as the app answers, with no deferral. Check again if Raven gains preedit (`NSTextInputClient`, `text-input-v3`) |

### Nested frames: caveats

- **Calls that wait together return in reverse order.** A second blocking call runs inside the
  first one's frames, so closing the first dialog doesn't resume its coroutine until the second
  has closed too (`stackedDialogsReturnInReverseOrder`).
- **The first nested frame comes at once only because a dialog needs a layout.** While the blocked
  block runs, `RavenDispatcher` still counts its drain task as posted, so a dispatch queued behind
  it neither posts a task nor wakes Raven. Opening a dialog attaches its content, so
  `rvn_window_frame_in` returns 0 and Raven frames without sleeping; that frame's drain empties the
  queue and clears the flag. If a blocking call ever waited without a layout first, that work would
  wait for the next event. Posting a new drain task when a block starts with others still queued
  would remove the dependency: [RavenDispatcher wakes for work queued behind a blocked block](../docs/issues/0059-ravendispatcher-wakes-for-work-behind-a-blocked-block.md).
- **Most "native" dialogs are Raven's.** `Rmessage_box` is always Raven's own sheet. `Rfile_dialog_*`
  uses the OS only on Windows (`IFileDialog`, inside Windows' own modal loop, not checked) and on
  Linux with zenity or kdialog (waited for with nested frames every 30 ms). On macOS, `native = true`
  shows the same Raven dialogs as the default, only blocking.

## Not run yet

| Fact | Waits for |
|---|---|
| Compose recomposes inside Windows' own modal loop (`IFileDialog_Show`), for `native = true` file dialogs | a Windows host |
| `rememberNavBackStack` and the saveable decorator work under the host and window owners | compose-raven's host |
| A MinGW-gcc `libraven.a` links with Kotlin/Native's `mingwX64` toolchain | a Windows host with MSYS2 |
| A GL context works on Raven's macOS child view | a real window with MoltenVK installed |
| Metal can draw into the child view's `CAMetalLayer` beside Raven | a real window with MoltenVK installed |
