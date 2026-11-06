---
status: open
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: null
---

# Public test module for apps

## Question

How does the internal test harness from [Testing strategy](archive/0012-testing-strategy.md) become a public `compose-raven-test` module so apps can test their own UIs? Decide its API surface (finders, actions, assertions, `runRavenTest`), whether it depends on `raven-native` publicly, how leak checks are offered, and how it ships alongside the library.
