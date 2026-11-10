---
status: open
---

# raven.native helpers

Write the small public `raven.native` layer that node code needs again and again: the callback
registry, string conversion at the C boundary, and the UI-thread check. Spec:
[3. raven-native](../spec.md#3-raven-native).

**Depends on:** [Gradle build and libraven](archive/0041-gradle-build-and-libraven.md).

## Deliverables

- [ ] **Callback registry**: one `StableRef` per widget, passed as every callback's `user_data`
      and disposed from `Rwidget_set_destroy_callback`. The destroy callback's `Rid` is already
      dead, so disposal must not touch it. The registry reports how many references are live, for
      the leak checks.
- [ ] **Strings**: Kotlin `String` to and from C strings, with no leak on either side.
- [ ] **UI-thread check**: record the UI thread at start and give a cheap assertion that fails
      clearly off it.
- [ ] No handle value classes and no per-widget wrappers.
- [ ] Tests: the registry reaches zero live references after widgets are destroyed, including a
      widget destroyed with its parent, and the string round trip works with non-ASCII text.
