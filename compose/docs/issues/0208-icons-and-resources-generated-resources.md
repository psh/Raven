---
status: open
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: null
---

# Icons and resources: generated resources

## Question

How does an app ship its images, icons and fonts inside its binary, and name them in code? Decide
whether a Gradle task embeds a resources folder and registers it at start-up (beside today's
`registerResource` and the resource root), and whether compose-raven generates Compose's
`Res.drawable.foo` / `Res.font.bar` accessors, their package, and how `painterResource` and fonts
take them. The answer is the spec section and its implementation tickets.
