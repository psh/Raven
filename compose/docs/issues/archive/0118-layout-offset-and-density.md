---
status: closed
---

# Modifier.offset and LocalDensity

Two small layout pieces the samples need. Spec: [6. Layout and Modifiers](../../spec.md#6-layout-and-modifiers).

**Depends on:** nothing in phase 3.

## Deliverables

- [x] `Modifier.offset(x, y)` on the slot padding (left +x, right −x, top +y, bottom −y), adding to `padding` ([Small port gaps](0107-small-port-gaps.md)).
- [x] `LocalDensity` with a minimal `Density` (`density` from `Rwindow_get_scale`, `Dp.toPx()`, `Int.toDp()`) ([The editor's scene viewport](0101-the-editors-scene-viewport.md)).
- [x] Tests: an offset moves a widget without moving its neighbours; density matches the window's scale.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 328 tests, 6 of them new (3 in
`LayoutsTest`, 2 in `DensityTest`, 1 in `LayoutTest`).

- **`Modifier.offset(x: Dp = 0.dp, y: Dp = 0.dp)`**, in `raven.compose.foundation.layout`
  (`LayoutModifiers.kt`), creates an `OffsetElement` (`compose-raven-ui`'s `LayoutElements.kt`).
  The fold (`Layout.kt`) adds it to the slot padding: left +x, right −x, top +y, bottom −y. Raven
  insets a child's area by its slot padding, negative too, so the widget keeps its size and its
  neighbours keep their places; offsets add up, to each other and to `padding`. `music`'s
  `Modifier.align(Alignment.BottomEnd).offset(10.dp, 8.dp)` hangs over the corner, as in Compose.
- **A negative `padding` is now refused** (`PaddingElement`'s `init`), as in Compose. The spec said
  it "stays an error", but nothing checked it before.
- **`LocalDensity`**, in `raven.compose.ui.platform` (`LocalDensity.kt`), provides a `Density`
  (`raven.compose.ui.unit`, `Density.kt`): `density`, with `Dp.toPx()`, `Int.toDp()` and also
  `Float.toDp()` in its scope (a pointer position is a float), and a `Density(density)` factory.
  `density` is the window's `Rwindow_get_scale`. Raven reports no change of scale, so `Window`
  reads it once a frame in `followRaven()`, beside the position, through `WindowDensity`, which
  holds it as snapshot state: a window moved to a monitor of another scale, or `Rset_ui_scale`,
  recomposes its readers. `Window.kt` changes by four lines (lane A's file).
- **Spec**: section 6's `LocalDensity` bullet gains `Float.toDp()` and that it follows the scale.
- **Tests**: an offset in a Row moves its widget, also up and left past the Row's start, without
  moving the widget after it; an offset hangs a `BottomEnd` widget over its Box's corner; a negative
  padding throws; offsets fold into the padding on the right sides (`LayoutTest`); the density is
  the window's scale, and follows `Rset_ui_scale(2f)` with a recomposition; the conversions.
