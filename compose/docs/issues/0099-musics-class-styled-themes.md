---
status: open
wayfinder: grilling
parent: 0095
blocked_by: []
assignee: null
---

# Music's class-styled themes

## Question

`music` styles nearly everything through theme classes: its three themes (`Nocturne`, `Aurora`,
`Daylight`) and `styles.xml` (laid over any look) give `muted` and `eyebrow` labels their colour,
opacity and size, and `nav`, `active`, `icon`, `round`, `white`, `chip`, `on`, `card`, `quick`,
`row`, `link` and `top` buttons their colours, **hovered and pressed colours** and corner radius,
`seek` sliders their track, `bare` inputs no frame, and `surface`, `bar`, `search` and `card-flat`
borders their background. Classes stay unexposed (settled three times; see the map's notes), so
the port has to say all of this in Kotlin, per theme. Decide what that needs that phase 3 lacks:
`XxxColors` with explicit hovered and pressed colours (today compose-raven derives them), the
border and focus colours a frameless `TextField` needs, and an icon tint that follows the colour
of the text around it, hover included (`Rimage_set_tint_text`; Compose's `LocalContentColor`).
Also settle how the port switches its per-theme values with the theme it applies.
