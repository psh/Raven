---
status: open
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: null
---

# Accessibility semantics

## Question

How do Compose's semantics (`Modifier.semantics`, `contentDescription`, roles, `Modifier.clearAndSetSemantics`) map onto Raven's accessibility API (`Rwidget_set_accessible_role`, `_name`, `_description`, `_hidden`, `_labelled_by`, `RAccessibleRole`)? Decide which roles composables set by default, how a `Text` next to an input becomes its label, and whether the test harness finders should use semantics.
