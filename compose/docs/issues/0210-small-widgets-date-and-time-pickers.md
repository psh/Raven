---
status: open
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: null
---

# Small widgets: date and time pickers

## Question

How do Raven's DatePicker and TimePicker become composables? Compare Compose Material 3's
`DatePicker(state)` / `rememberDatePickerState` and `TimePicker(state)` with Raven's widgets
(`raven_controls.h`): which state they hold, controlled or not, what of Material's shape fits and
what is a difference for section 12, and how they are tested. The answer is the spec section and
its implementation tickets.
