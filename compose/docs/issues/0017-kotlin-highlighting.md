---
status: open
wayfinder: grilling
parent: 0001
blocked_by: [0013]
assignee: null
---

# Kotlin highlighting in CodeEditor

## Question

Raven's TextEditor has no Kotlin highlighting, so the translator sample shows its output as Java. How is a Kotlin language added with `Rlanguage_register` (`RLanguageDesc`: keywords, comments, strings, flags), and where does it live? As a language compose-raven registers at start-up so that `CodeEditor(language = "Kotlin")` works for every app, in the sample only, or upstream in Raven next to the built-in languages?
