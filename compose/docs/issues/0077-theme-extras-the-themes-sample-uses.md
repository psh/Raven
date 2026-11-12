---
status: open
wayfinder: grilling
parent: 0073
blocked_by: []
assignee: null
---

# Theme extras the themes sample uses

## Question

`themes` uses theme features the spec doesn't cover: `Rtheme_set_accent` (one colour and the
shades Raven derives from it), `Rtheme_save`, style classes (`Class="danger"`, which phase 3's
[Fonts and text styling](archive/0027-fonts-and-text-styling.md) kept out), and reloading theme
and content files when they change on disk (`Rxml_set_reload_callback`). For each: its
compose-raven shape (a `RavenTheme` parameter, a function, a deliberate non-goal with Compose's
answer instead), and is it phase 2 or a named later phase? Read `examples/themes/` for what each
does there.
