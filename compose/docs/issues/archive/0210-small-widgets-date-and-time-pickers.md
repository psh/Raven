---
status: closed
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: claude
---

# Small widgets: date and time pickers

## Question

How do Raven's DatePicker and TimePicker become composables? Compare Compose Material 3's
`DatePicker(state)` / `rememberDatePickerState` and `TimePicker(state)` with Raven's widgets
(`raven_controls.h`): which state they hold, controlled or not, what of Material's shape fits and
what is a difference for section 12, and how they are tested. The answer is the spec section and
its implementation tickets.

## Resolution

Resolved on 2026-11-26 in conversation. Written into spec section 8 (Date and time pickers),
section 2 (foundation's new dependency) and section 12, and filed as
[Small widgets: DatePicker and TimePicker](../0227-small-widgets-datepicker-and-timepicker.md).

- **Controlled**, as the binding's other inputs are: `DatePicker(date, onDateChange, ...)` and
  `TimePicker(time, onTimeChange, ...)`, named after Raven's widgets. Material 3's state objects
  (`DatePickerState`, `TimePickerState`) were not followed: Raven's DatePicker is a field with a
  drop-down calendar, not an inline calendar, and its TimePicker a field, not a dial; section 12
  says so.
- **Types**: kotlinx-datetime's `LocalDate`, `LocalTime` and `DayOfWeek`, a new `api` dependency
  of compose-raven-foundation, rather than types of the binding's own or Material's UTC
  milliseconds.
- **DatePicker**: `date` non-null (Raven's always holds one), `range: ClosedRange<LocalDate>?`,
  `firstDayOfWeek = SUNDAY` and `format` in Raven's own pattern syntax, Raven's defaults.
- **TimePicker**: `showSeconds = false`, `minuteStep = 1`, 24-hour only; seconds it doesn't show
  are kept.
- **One implementation ticket** for both.
