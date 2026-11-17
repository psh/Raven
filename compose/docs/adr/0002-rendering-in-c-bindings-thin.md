---
status: accepted
---

# Rendering in C, bindings thin

The maps spike drew the same map twice: in Kotlin, in compose-raven, and in C, as Raven's Map
widget ([A native Map widget](../issues/archive/0140-a-native-map-widget.md)). The C widget was
faster wherever the two could differ (tiles prepared 6 to 14 times faster, labels placed 3 to 6
times), drew and labelled the same map, and every binding got it at once: Python showed it in about
120 lines with no map code of its own. A fix made after both existed, the world repeating past the
antimeridian, had to be written twice, once in each. On 2026-11-17 we took the spike's lesson as the
rule for everything after it:

- **What draws, decodes, lays out or computes heavily goes in Raven's C**, as a widget or a C API,
  so that every binding (Kotlin, Python, Rust, and the rest) gets the same behaviour and the same
  speed, and it is fixed in one place.
- **A binding stays as thin as it can**: it maps the language's idioms onto Raven's calls
  (compose-raven's composables, state and modifiers; another language's classes or callbacks) and
  does only what belongs to the language or the app, such as fetching data with the language's own
  HTTP client and cache, as compose-raven's `MapView` does for tiles. It holds no drawing or
  geometry of its own.
- **Where C lacks something a binding needs, Raven grows it**, through a fork change proposed
  upstream, rather than the binding working around it in its own code.

## Considered options

- **Draw in each binding, over Raven's drawing calls**: each language's idioms all the way down, but
  each binding writes and fixes the same code again, and Kotlin/Native was the slower of the two
  here.
- **Split by case**: heavy work in C, lighter drawing allowed in a binding. Rejected as the default,
  because the line moves and the copies drift apart; a binding that needs drawing of its own for
  something only it has (a composable's `Canvas`, say) still gets it.

## Consequences

- New widgets are written in C first, with compose-raven's composable over them, not as Kotlin to
  port later.
- The maps spike's own Kotlin drawing is retired, not kept as a reference beside the C
  ([0142](../issues/archive/0142-the-map-spikes-kotlin-drawing.md)).
