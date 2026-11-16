---
status: open
---

# The maps spike's own Kotlin drawing: retire it or keep it as a reference

The maps spike (`compose/spikes/maps`) still holds its Kotlin decoder, style evaluator,
triangulation and label placement, behind `MAPS_KOTLIN=1`, as the reference the
[native Map widget](archive/0140-a-native-map-widget.md) was measured and checked against. Now the
map lives in C, decide whether that code goes, or stays as a test reference, and act on it.

**Depends on:** nothing open. The spike's drawing is on `spike/maps`; `spike/maps-native` adds
`MapView` and makes it the default.

## Deliverables

- [ ] A decision, recorded here: retire the Kotlin drawing, or keep it, and if so as what (the benchmarks' baseline, or a check that C and Kotlin draw and label alike).
- [ ] If retired: the Kotlin drawing, `MAPS_KOTLIN`, and the tests and benchmarks that only measure it go; the spike's tests still pass, and `spikes/README.md` says what the spike is now.
- [ ] If kept: a test that compares the two (draw counts and labels placed, as 0140's comparison did by hand), so the reference can't drift unseen.
