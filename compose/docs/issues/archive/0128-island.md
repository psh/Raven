---
status: closed
---

# Island

The pill at the top of the window. Spec: [8: Transitions and Island](../../spec.md#transitions-and-island-phase-3).

**Depends on:** [Windows that come and go, and their owners](0112-windows-that-come-and-go-and-their-owners.md).

## Deliverables

- [x] `Island(expanded, onExpandedChange, modifier, expandOnHover, expandOnClick, attached, notch, folded, unfolded)`, controlled.
- [x] `Window(island = { Island(...) })` over `Rwindow_set_island`, in its own composition.
- [x] Tests: a refused expansion reverts; hover and click expansion report back.

## Resolution

Done on macOS. 335 tests pass across the build, 7 of them new (`IslandTest`, in
compose-raven-foundation). No Raven change, and no deviation from the spec.

- **`Island`** (`compose-raven-foundation`, `Island.kt`) is a `RavenWidget` over `R_WIDGET_ISLAND`,
  with `expandOnHover`, `expandOnClick`, `attached` and `notch` as properties. `folded` and
  `unfolded` are each a Box content node, in Raven's order (first folded, second unfolded), emitted
  by a new internal `RavenContentNode`, since `RavenWidget`'s single content panel doesn't fit a
  widget whose children are each a slot of its own.
- **Controlled**: `IslandExpanded` is a `ControlledValue` over `Risland_is_expanded` and
  `Risland_set_expanded`, and `IslandChanged` an `Event` over `Risland_set_changed_callback` that
  marks it, so an expansion the app doesn't take reverts, as other controlled inputs do.
- **`Window(island = { ... })`** gives the island a composition of its own under the window's
  parent context, with the same window locals as the content. Its root is a placeholder widget:
  a `RootSlot` on `RavenApplier` sends the root's children to `Rwindow_set_island` instead. The
  window owns the island's widget from then on, and deletes the one it had when another is set, so
  a removed island isn't detached; its node is released and destroying its widget takes it out
  (Raven clears the window's island then). An island composition that emits more than one widget
  shows the last one. Setting `island` to `null` disposes the composition, and closing the window
  disposes it before the window.
- **Test harness**: `RavenHost.islandRoots`, so the finders search the islands as well as the
  windows.
- **Tests**:
  - a window's island floats at its top, holds the folded and unfolded content, takes its top
    padding as its distance from the edge, and passes `notch` on;
  - a refused expansion reverts;
  - hover and click expansion report back, and the app folds it again;
  - without `expandOnClick` a click does nothing, and without `expandOnHover` the pointer doesn't;
  - the island recomposes in its own composition, and comes and goes with `island`, the content
    staying;
  - an `Island` elsewhere is a widget in the layout, not the window's island.
