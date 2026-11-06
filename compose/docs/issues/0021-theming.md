---
status: open
wayfinder: grilling
parent: 0001
blocked_by: [0014]
assignee: null
---

# Theming

## Question

How do Raven's themes and styles (`Rtheme_apply`, theme files, `Class`, `Color.<Role>`, `Metric.<Name>`, text colour and size) appear in compose-raven, as the `themes` port needs? Compose's convention is a theme composable with CompositionLocals (`MaterialTheme { }`). Decide whether compose-raven offers a `RavenTheme` over Raven's theme engine, how per-widget colour and metric overrides are spelled (parameters or modifiers), where `Text`'s `color`/`fontSize` land, and whether a Material-like module above foundation is wanted.
