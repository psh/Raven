---
status: open
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: null
---

# Icons and resources: build-time XML translation

## Question

How does an app translate its Raven XML during its own build? Decide the Gradle task that runs
`raven-xml2compose` (a native executable, since Gradle's JVM can't load Raven): where the tool
comes from, its inputs and outputs and up-to-date checks, how warnings and `--werror` reach the
build, and how it relates to generated resources. The answer is the spec section and its
implementation tickets.
