---
status: closed
---

# The maps spike's own Kotlin drawing: retire it or keep it as a reference

The maps spike (`compose/spikes/maps`) still holds its Kotlin decoder, style evaluator,
triangulation and label placement, behind `MAPS_KOTLIN=1`, as the reference the
[native Map widget](0140-a-native-map-widget.md) was measured and checked against. Now the
map lives in C, decide whether that code goes, or stays as a test reference, and act on it.

**Depends on:** nothing open. The spike's drawing is on `spike/maps`; `spike/maps-native` adds
`MapView` and makes it the default.

## Deliverables

- [x] A decision, recorded here: retire the Kotlin drawing, or keep it, and if so as what (the benchmarks' baseline, or a check that C and Kotlin draw and label alike).
- [x] If retired: the Kotlin drawing, `MAPS_KOTLIN`, and the tests and benchmarks that only measure it go; the spike's tests still pass, and `spikes/README.md` says what the spike is now.
- [ ] ~~If kept: a test that compares the two (draw counts and labels placed, as 0140's comparison did by hand), so the reference can't drift unseen.~~ Not kept.

## Decision

**Decided 2026-11-17: retired.** Following [ADR 0002](../../adr/0002-rendering-in-c-bindings-thin.md),
the map's drawing lives in Raven's C, and a second copy kept as a reference is the copy the ADR
rules out: the antimeridian fix that day had to be written in both. 0140 already recorded the
comparison and its numbers, and `test_map` covers what the Kotlin tests checked. The C comments no
longer name the Kotlin port as their source.

On `spike/maps-native` (`2aaaacc`, `6a10849`): the spike is a thin demo of `MapView` over the Map
widget (live or offline tiles, the stats overlay, `launch-maps`, `fetch-data`), about 500 lines
from about 3,150. The decoder, style evaluator, earcut, labels and `Canvas` drawing went, with
`MAPS_KOTLIN`, the release test binary and the tests that only checked them; the JSON reader's and
the disk cache's tests stay and pass, and `spikes/README.md` says what the spike is now. The Kotlin
drawing stays in history on `spike/maps`.
