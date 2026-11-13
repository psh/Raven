---
status: open
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: null
---

# Music's accent picker

## Question

`music`'s settings choose the accent with a `ColorPicker` in its popup form
(`Popup="true" Alpha="false"`), when it doesn't follow the cover. `ColorPicker` is one of phase 5's
specialist widgets, and `samples/themes` already ports with a row of swatches until then
([Theme extras the themes sample uses](archive/0077-theme-extras-the-themes-sample-uses.md)).
Decide whether `music` does the same (a port gap completed in phase 5), or a slim
`ColorPicker(color, onColorChange, modifier, popup, alpha)` moves into phase 3, completing both
samples.
