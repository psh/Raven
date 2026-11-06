---
status: open
wayfinder: grilling
parent: 0001
blocked_by: [0013]
assignee: null
---

# Templates in the XML translator

## Question

How does the XML translator handle Raven's templates, which v1 treats as unsupported? `<Widget>` declarations would become `@Composable` functions with parameters (and `<Children/>` / `Into=` would become content slots), `<Repeat>` and `<ForEach>` would become loops, `If=` / `<If>` would become `if`, and `<Include>` would be handled somehow. How much of Raven's `{...}` expression language is translated into Kotlin expressions, and what is left as a TODO?
