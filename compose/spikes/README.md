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
| [`macos-child-views/`](macos-child-views/) | `macos-child-views/run.sh`, on macOS with MoltenVK | GL and Metal in Raven's native child views, in a real window, read back and captured on screen |
| [`navigation3/`](navigation3/) | `./gradlew :spikes:navigation3:allTests`, from `compose/` | Navigation 3's back stack and saveable-state decorator under the host, with the serialization plugin |

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

| `rememberNavBackStack` and the saveable-state decorator work under the host, with the kotlinx-serialization plugin (checked 2026-11-12) | **Passes**, with two findings | `navigation3/`: a back stack of `@Serializable` keys shows its top entry in a harness window and follows `add`/`removeLastOrNull`; with `rememberSaveableStateHolderNavEntryDecorator`, an entry's `rememberSaveable` state survives another entry pushed over it and popped; after the whole subtree leaves the composition, the stack and the entry's state come back from a `SaveableStateRegistry` provided above it. `navigation3-runtime` 1.2.1 brings no Compose UI, and its runtime resolves to ours (1.12.1) | No fallback needed. **On Kotlin/Native**, `rememberNavBackStack` takes a `SavedStateConfiguration` whose `serializersModule` declares the `NavKey` subclasses (`polymorphic(NavKey::class) { subclass(Home::class) }`), and refuses the default one: there is no reflective overload. **Saveable state goes through `runtime-saveable`**: the decorator uses a `SaveableStateHolder` (no registry needed), and restoring after a subtree leaves needs a `LocalSaveableStateRegistry` above it; neither reads the `SavedStateRegistryOwner` |
| GL and Metal work in Raven's macOS native child views (checked 2026-11-12, at `97f8aa5`, MoltenVK from Homebrew) | **Passes**, with one finding | `macos-child-views/`: a window drawn by Raven through MoltenVK holds a label and two native viewports. An `NSOpenGLContext` (3.2 core) on the left child view clears and reads back green with no GL error; Metal frames in the right child's `CAMetalLayer` read back magenta from the drawable. A capture of the window (`screencapture -l`) shows Raven's label, the green and the magenta, so both composite beside Raven | `OpenGLViewport` keeps macOS, and Metal goes through `NativeViewport`. **Raven reports a child's `CAMetalLayer`, not its `NSView`**, and the layer has no delegate, so compose-raven finds the view in the window's view tree by its layer (no fork change) and its `NativeWindowHandle` carries both. `NSOpenGLContext` is deprecated since macOS 10.14 but works |

### Nested frames: caveats

- **Calls that wait together return in reverse order.** A second blocking call runs inside the
  first one's frames, so closing the first dialog doesn't resume its coroutine until the second
  has closed too (`stackedDialogsReturnInReverseOrder`).
- **No longer applies (2026-11-11): the first nested frame came at once only because a dialog needs
  a layout.** While the blocked block ran, `RavenDispatcher` still counted its drain task as
  posted, so a dispatch queued behind it neither posted a task nor woke Raven, and only the layout
  of the dialog's content made Raven frame without sleeping. Each drain now arms a guard timer due
  at once, so the nested loop wakes with or without a layout:
  [RavenDispatcher wakes for work queued behind a blocked block](../docs/issues/archive/0059-ravendispatcher-wakes-for-work-behind-a-blocked-block.md).
- **Most "native" dialogs are Raven's.** `Rmessage_box` is always Raven's own sheet. `Rfile_dialog_*`
  uses the OS only on Windows (`IFileDialog`, inside Windows' own modal loop, not checked) and on
  Linux with zenity or kdialog (waited for with nested frames every 30 ms). On macOS, `native = true`
  shows the same Raven dialogs as the default, only blocking.

## Not run yet

| Fact | Waits for |
|---|---|
| Compose recomposes inside Windows' own modal loop (`IFileDialog_Show`), for `native = true` file dialogs | a Windows host |
| A MinGW-gcc `libraven.a` links with Kotlin/Native's `mingwX64` toolchain | a Windows host with MSYS2 |
