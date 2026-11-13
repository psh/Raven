---
status: closed
wayfinder: task
parent: 0095
blocked_by: []
assignee: Paul Hawke
---

# Verify Navigation 3 under the host

## Question

The last open part of a verify-first fact on `roadmap.md`: `rememberNavBackStack` and the
saved-state decorator of `navigation3-runtime` work under compose-raven's host and window owners,
with the kotlinx-serialization plugin. `navigationevent` already passed; the rest waited for the
host, which now exists. Check it in `../spikes/` (or a test on the host), record the result in the
roadmap's milestone 0 table, and, if it fails, apply its fallback (windows call registered back
handlers directly; any other gap is fixed in `compose-raven-navigation3`).

## Resolution

Done 2026-11-12: **passes**, so the fallback isn't needed. The spike is
[`spikes/navigation3/`](../../../spikes/navigation3/) (`./gradlew :spikes:navigation3:allTests`,
four headless tests through the harness), and its result is in
[`spikes/README.md`](../../../spikes/README.md) and the roadmap's milestone 0 table.

- A back stack of `@Serializable` `NavKey`s, built with the kotlinx-serialization plugin on
  Kotlin/Native, shows its top entry and follows `add`/`removeLastOrNull`.
- With `rememberSaveableStateHolderNavEntryDecorator`, an entry's `rememberSaveable` state survives
  another entry pushed over it and popped, with no registry provided at all.
- After the whole subtree leaves the composition, the back stack and the entry's state come back
  from a `SaveableStateRegistry` provided above it (the spike provides it where a window's owner
  would).
- `navigation3-runtime` 1.2.1 brings no Compose UI, Foundation or Animation; its Compose runtime
  resolves to ours (1.12.1). The serialization plugin joined the version catalog
  (`libs.plugins.kotlin.serialization`).

Two facts the spec now records:

- **On Kotlin/Native, `rememberNavBackStack` needs a `SavedStateConfiguration`** whose
  `serializersModule` declares the `NavKey` subclasses; it refuses the default one, and there is no
  reflective overload as on Android. Spec section 8's Navigation 3 part shows it, and section 12
  lists it.
- **Saveable state goes through `runtime-saveable`, not the `SavedStateRegistryOwner`**: neither the
  runtime nor the decorator reads the window's `SavedStateRegistryOwner`. Spec section 4 now says
  so, and [Scroll position](0096-scroll-position.md)'s line "comes back with the window's saved
  state" became "wherever saveable state is kept, such as a Navigation 3 entry". For
  [Music's pages and back navigation](../0098-musics-pages-and-back-navigation.md): a page that is a
  Navigation 3 entry keeps its saveable state (scroll position included) while other pages are
  shown, with nothing more from the window.
