---
status: open
wayfinder: grilling
parent: 0287
blocked_by: []
assignee: null
---

# Gestures and drag: pointerInput and drag gestures

## Question

How compose-raven offers Compose's coroutine pointer input: `Modifier.pointerInput(key) { awaitPointerEventScope { } }`, `detectTapGestures` (long press, double tap), `detectDragGestures` and `Modifier.draggable`, beyond today's `onPointerEvent`. Which Raven events feed it (press, move, release, capture while dragging), how a gesture consumes events from the widgets under it, what of `PointerInputChange` is real (position, pressed, consumed, type), and the test app or sample that proves it. From [compose-raven phase 5 spec](0204-compose-raven-phase-5-spec.md#not-yet-specified).
