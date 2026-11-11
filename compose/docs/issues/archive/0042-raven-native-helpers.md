---
status: closed
---

# raven.native helpers

Write the small public `raven.native` layer that node code needs again and again: the callback
registry, string conversion at the C boundary, and the UI-thread check. Spec:
[3. raven-native](../../spec.md#3-raven-native).

**Depends on:** [Gradle build and libraven](0041-gradle-build-and-libraven.md).

## Deliverables

- [x] **Callback registry**: one `StableRef` per widget, passed as every callback's `user_data`
      and disposed from `Rwidget_set_destroy_callback`. The destroy callback's `Rid` is already
      dead, so disposal must not touch it. The registry reports how many references are live, for
      the leak checks.
- [x] **Strings**: Kotlin `String` to and from C strings, with no leak on either side.
- [x] **UI-thread check**: record the UI thread at start and give a cheap assertion that fails
      clearly off it.
- [x] No handle value classes and no per-widget wrappers.
- [x] Tests: the registry reaches zero live references after widgets are destroyed, including a
      widget destroyed with its parent, and the string round trip works with non-ASCII text.

## Resolution

Done on macOS (arm64), 2026-11-10, in `raven-native/src/nativeMain/kotlin/raven/native/`. Twelve
headless tests pass in `raven-native`, the smoke test among them.

- **`CallbackRegistry`**: `register(widget, target)` makes the `StableRef` and returns the
  `user_data` for the widget's callbacks. `callbackTarget<T>()` turns that `user_data` back into
  the target, and `liveCount` is for the leak checks. The reference is also stored as the
  widget's Raven user handle, so `targetOf(widget)` finds a widget's node, which test finders and
  hit testing will need, and a second `register` fails instead of leaking the first reference.
  Raven calls the destroy callback for widgets destroyed directly, with their parent, and by
  `Rshutdown`. Tests cover all three.
- **Strings**: cinterop already converts a `String` passed to a `const char*` for the duration of
  the call, so the only helpers are for returned strings. `toRavenString()` copies a string Raven
  keeps, and reads null as empty. `takeRavenString()` copies and then frees a string Raven hands
  over (`Rfile_read`).
- **UI thread**: `requireUiThread()` uses Raven's own `Ris_ui_thread()`, which is false with Raven
  stopped too, so nothing is recorded on the Kotlin side.
- `withHeadlessRaven { }` and `ravenBlocksHeld()` in `raven-native`'s tests are the beginnings of
  the harness's start-up and leak check.
