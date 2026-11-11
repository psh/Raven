---
status: closed
---

# samples/hello

Port Raven's `examples/hello` to idiomatic compose-raven: a label, buttons and a click counter,
with its timer as a `LaunchedEffect`. The first app a person can run. Spec:
[`samples/hello`](../../spec.md#sampleshello).

**Depends on:** [Text, buttons and the first finders](0047-text-buttons-and-first-finders.md).

## Deliverables

- [x] `samples/hello` as a `singleWindowApplication` with the same look and behaviour as the C
      example.
- [x] Runs on macOS (with MoltenVK) and Linux from `./gradlew -p compose :samples:hello:run...`
      (or the linked executable). Document how in `compose/README.md`.
- [x] A headless test of its counter.

## Resolution

Done and run on macOS. Linux is documented but not run: it waits for
[Linux build and libraven](../0058-linux-build-and-libraven.md). 102 tests pass, 3 of them the
sample's.

- **No timer**: Raven's `examples/hello` has none; that came from the planning ticket. The port has
  no `LaunchedEffect`, and spec section 10 now describes the real example.
- **Pulled forward from later phases**, because the C example uses them (decided with the user):
  - `application(followSystemAccessibility) { }` with `ApplicationScope.exitApplication()`, and a
    public `Window(onCloseRequest, title, width, height) { }`. The Quit button needs
    `exitApplication`, which a `singleWindowApplication`'s content can't reach. `Window`'s other
    parameters and `WindowState` stay in phase 3, and `singleWindowApplication` is now shorthand
    for `application` with one `Window`, as spec section 4 says.
  - `TextUnit` with `.sp` (`raven.compose.ui.unit`), and `Text(text, modifier, fontSize)` through
    `Rlabel_set_text_size`, where unspecified is 0, the theme's size.
  - `Modifier.tooltip(text)` through `Rwidget_set_tooltip`, set when the folded tooltip changes.
- **The sample** (`raven.sample.hello`): `main` is `application { Window(onCloseRequest =
  ::exitApplication, title = "Hello Raven", width = 420, height = 220) { Hello(onQuit =
  ::exitApplication) } }`.
  - `Hello` centres a 12 dp-spaced Column in a `Box(fillMaxSize, Center)`.
  - It shows a 24 sp title, "Press the button" or "Clicked N time(s)", and a Row 8 dp apart with
    `Button` "Click me" (primary, tooltip "Counts the clicks") and `OutlinedButton` "Quit" (Raven's
    default style).
  - The executable is `hello.kexe`.
- **Harness for samples**: `RavenTest.kt` moved to foundation's `src/testHarness/kotlin`, which
  foundation's `nativeTest` and the sample's `nativeTest` both add as a source directory. The file
  opts in to `InternalComposeRavenApi` itself. This is a stopgap until `compose-raven-test`
  (phase 3).
- **MoltenVK on Apple silicon**: Raven `dlopen`s `libMoltenVK.dylib` by bare name, which dyld
  doesn't look for in `/opt/homebrew/lib`, so the first run failed with "cannot initialize the
  Vulkan backend". `raven.def`'s `linkerOpts.osx` now adds `-rpath /opt/homebrew/lib`. dyld searches
  the rpath for a bare name, so every compose-raven app finds Homebrew's MoltenVK with no
  `DYLD_*` variable. Verified by running the release executable.
- **Raven bug found**, fixed separately as
  [fork change 6](../../spec.md#11-required-raven-changes): Raven's Cocoa `flagsChanged:` called
  `[event charactersIgnoringModifiers]`, which AppKit rejects for a modifier-key event. Pressing
  Cmd, Shift, Ctrl or Option while a Raven window had focus ended the app with
  `NSInternalInconsistencyException`; one run of the debug build did. Raven's C examples had it
  too. It now passes `nil` for the characters, since modifier keys are mapped by key code.
  Verifying the fix also showed that `raven-native`'s cinterop didn't re-run when libraven was
  rebuilt, so apps kept the old library. It now declares `libraven.a` as an input.
- **Checked by eye** on macOS: a capture of the window shows the centred title, counter text and
  the two buttons, as the C example lays them out.
- **README**: how to run the samples, and the Vulkan driver each platform needs.
- **Tests** (`samples/hello`, `HelloTest`):
  - the counter reads "Press the button", then "Clicked 1 time" and "Clicked 2 times";
  - Quit calls `onQuit`;
  - the title is larger than the counter (Raven has no text-size getter), both are centred, and
    Click me has its tooltip.
