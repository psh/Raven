---
status: closed
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: Paul Hawke
---

# Island and appear transitions

## Question

How do Raven's Island (folded and unfolded children, `Expanded`, hover and click expansion) and its appear transitions (`Appear`, `AppearDelay`: Fade, Pop, Zoom, Fly, Slide, Drop) map onto Compose, as `animations`, `music` and `workspace` need? Compose's convention is `AnimatedVisibility` with `enter`/`exit` transitions, so decide how far Raven's transitions can stand in for it, and the shape of an `Island` composable.

## Resolution

Facts (`raven_widget.h`, `raven_shell.h`): Raven's transitions are **enter-only**. `Rwidget_set_transition(widget, transition, delay)` plays each time the widget appears (first drawn, added to a shown window, visible again), and `Rwidget_play_transition` plays one now. They are Fade, Pop, Zoom, Fly up/down/left/right, Drop and Slide left/right, as Raven springs, animating what the widget draws but not its layout. There are **no exit transitions** and no transform API, only `Rwidget_set_opacity`. **Island** has a folded and an unfolded child, `set_expanded`, expand on hover and on click, `attached`, `notch` (the MacBook camera housing), `expand_for(seconds)` and a changed callback. `Rwindow_set_island` floats it at the top centre of a window, which owns it.

- **Enter transitions**, in two layers:
  - `Modifier.appearTransition(AppearTransition.FlyUp, delay = 100.milliseconds)` uses `Rwidget_set_transition`. It is decoration any widget can have, replays on every appearance, and is what the XML translator emits for `Appear`/`AppearDelay`.
  - `AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) { }` maps Compose's enter specs to the nearest Raven transition: `fadeIn` is Fade, `scaleIn` is Pop (from below 1) or Zoom (from above 1), and `slideInVertically`/`slideInHorizontally` are Fly up/down/left/right by the sign of the offset. `animationSpec` is ignored, because Raven springs (documented).
- **Exit transitions**: only `fadeOut()`. compose-raven keeps the content composed, animates `Rwidget_set_opacity` to 0 on the frame clock, then removes it. Other exits fall back to `fadeOut` with a one-time warning (documented). Real exit transitions would need a fork change (fog).
- **General animations** (`animate*AsState`, `Animatable`, `updateTransition`): **verify first** that `androidx.compose.animation:animation-core` resolves for our targets without Compose UI. If it does, it is used directly, with converters for compose-raven's `Dp` and `Color`. If not, compose-raven provides a small `animate*AsState` set on the frame clock. These animate parameters by recomposing each frame, which is fine at small scale (documented).
- **`Island(expanded, onExpandedChange, modifier, expandOnHover = true, expandOnClick = true, attached = false, notch = false, folded: @Composable () -> Unit, unfolded: @Composable () -> Unit)`**:
  - The slots go into internal Boxes in Raven's order.
  - Expansion is controlled (the frame-callback check).
  - `expand_for` is left out, because a `LaunchedEffect` that expands, delays and folds does the same.
  - `Window(island = { Island(...) })` gives the window-level island its own composition rooted through `Rwindow_set_island`, like `Dialog`. An `Island` anywhere else is an ordinary widget in the layout.
