---
status: open
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: null
---

# XML translator as a tool

## Question

How is the sample's XML parsing and translator extracted for reuse: a shared module, a command-line tool, a Gradle task, or several? Decide where the private cinterop goes, how `<Include>` is followed across files (one set of functions per file), how output files are named and laid out, and whether the translator runs headless without a window.
