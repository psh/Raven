---
status: open
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: null
---

# Animated effects and continuous redraw

## Question

`music`'s backdrop, album hero and artist card are animated shader effects whose colours follow the
cover, which can stop moving (`EffectAnimate`) and never follow the pointer (`EffectPointer="false"`).
Phase 3's `Surface(effect, effectStyle)` has neither setting. `music` and `glass` also turn on
`Rwindow_set_continuous_redraw` while something moves. Decide `EffectStyle`'s animate and pointer
settings, and whether continuous redraw is the host's business (frame awaiters already ask for
frames; does Raven animate an effect without continuous redraw?) or a `Window` parameter.
